package net.Realism.content.simulator;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.RNetworking;
import net.Realism.RealismMod;
import net.Realism.config.RealismConfig;
import net.Realism.content.graph.v2.RailGraphCache;
import net.Realism.content.graph.v2.RailGraphTranslator;
import net.Realism.content.simulator.core.SimClock;
import net.Realism.content.simulator.core.SimEngine;
import net.Realism.content.simulator.core.SimGraph;
import net.Realism.content.simulator.core.SimProgram;
import net.Realism.content.simulator.core.SimResult;
import net.Realism.content.simulator.core.SimTrainSpec;
import net.Realism.content.trains.schedule.AdvancedScheduleItem;
import net.Realism.foundation.network.SimulationResultPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates simulation requests: validates and snapshots on the server
 * thread (schedule from the held item, live trains, translated graph), runs
 * the engine on a dedicated worker thread, and delivers the result back on
 * the server thread. One sim per player, global concurrency cap, per-player
 * cooldown, 10s wall-clock budget per run.
 */
public class SimulationService {

    public record Settings(int carriages, int locomotives, int accelerationMode,
                           double customAcceleration, int horizonHours, boolean startNow,
                           int startHour, int startMinute) {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Realism-Simulator");
        thread.setDaemon(true);
        return thread;
    });
    private static final int SAMPLE_STRIDE = 100;
    /** In-game hours are 1000 day-time ticks; horizon runs in real ticks. */
    private static final int TICKS_PER_HOUR = 1000;

    private static final Map<UUID, Long> lastRequestGameTime = new ConcurrentHashMap<>();
    private static final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    /** Server-thread entry point. */
    public static void request(ServerPlayer player, Settings settings) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof AdvancedScheduleItem)) {
            refuse(player, "realism.sim.refuse.no_item", "");
            return;
        }
        if (held.getTag() == null || !held.getTag().contains("Schedule")) {
            refuse(player, "realism.sim.refuse.empty_schedule", "");
            return;
        }

        long now = player.serverLevel().getGameTime();
        // Cooldown is an anti-spam guard for shared servers; pointless solo.
        boolean singleplayer = player.getServer() != null && player.getServer().isSingleplayer();
        long cooldownTicks = singleplayer ? 0 : RealismConfig.COMMON.SimCooldownSeconds.get() * 20L;
        Long last = lastRequestGameTime.get(player.getUUID());
        if (last != null && now - last < cooldownTicks) {
            refuse(player, "realism.sim.refuse.cooldown",
                    String.valueOf((cooldownTicks - (now - last)) / 20 + 1));
            return;
        }
        if (activePlayers.contains(player.getUUID())) {
            refuse(player, "realism.sim.refuse.already_running", "");
            return;
        }
        if (activePlayers.size() >= RealismConfig.COMMON.SimMaxConcurrent.get()) {
            refuse(player, "realism.sim.refuse.server_busy", "");
            return;
        }

        Schedule schedule;
        try {
            schedule = Schedule.fromTag(held.getTag().getCompound("Schedule"));
        } catch (Exception e) {
            refuse(player, "realism.sim.refuse.empty_schedule", "");
            return;
        }
        if (schedule.entries.isEmpty()) {
            refuse(player, "realism.sim.refuse.empty_schedule", "");
            return;
        }

        ScheduleCompiler.CompileResult compiled = ScheduleCompiler.compile(schedule);
        if (!compiled.clean()) {
            SimulationPayload payload = new SimulationPayload();
            for (ScheduleCompiler.Problem problem : compiled.problems())
                payload.refusals.add(new SimulationPayload.Refusal(problem.translationKey(),
                        problem.detail()));
            RNetworking.sendToPlayer(new SimulationResultPacket(payload), player);
            return;
        }

        int firstDestination = -1;
        for (int i = 0; i < compiled.program().entries.size(); i++)
            if (compiled.program().entries.get(i).kind == SimProgram.InstructionKind.DESTINATION) {
                firstDestination = i;
                break;
            }
        if (firstDestination == -1) {
            refuse(player, "realism.sim.refuse.no_destination", "");
            return;
        }
        SimProgram.Entry firstEntry = compiled.program().entries.get(firstDestination);

        // Pick the network: first graph (by id) with a platform matching the
        // schedule's first destination.
        List<TrackGraph> graphs = new ArrayList<>(
                Create.RAILWAYS.sided(player.level()).trackNetworks.values());
        graphs.sort(Comparator.comparing(graph -> graph.id.toString()));
        int nodeCap = RealismConfig.COMMON.GraphNodeCap.get();

        TrackGraph trackGraph = null;
        RailGraphTranslator.Result translation = null;
        SimGraph simGraph = null;
        List<SimGraph.StationTarget> startTargets = null;
        boolean anyTooLarge = false;
        for (TrackGraph candidate : graphs) {
            RailGraphTranslator.Result result = RailGraphCache.get(candidate, nodeCap);
            if (result.graph() == null) {
                anyTooLarge = true;
                continue;
            }
            SimGraph candidateSim = SimGraphBuilder.build(result.graph(), result.topology());
            List<SimGraph.StationTarget> targets = candidateSim.findStations(firstEntry.pattern);
            if (targets.isEmpty())
                continue;
            trackGraph = candidate;
            translation = result;
            simGraph = candidateSim;
            startTargets = targets;
            break;
        }
        if (trackGraph == null) {
            refuse(player, anyTooLarge ? "realism.sim.refuse.too_large"
                    : "realism.sim.refuse.no_matching_station", firstEntry.filterText);
            return;
        }

        startTargets.sort(Comparator.comparing(SimGraph.StationTarget::name)
                .thenComparing(target -> target.stationId().toString()));
        SimGraph.StationTarget start = startTargets.get(0);

        SimTrainSpec phantom = new SimTrainSpec("phantom", "phantom",
                Math.max(4, settings.carriages() * 8L), phantomAcceleration(settings),
                AllConfigs.server().trains.trainTopSpeed.getF() / 20,
                AllConfigs.server().trains.trainTurningTopSpeed.getF() / 20,
                1.0, compiled.program(), start.edgeId(), start.offset());
        phantom.startEntry = firstDestination;
        phantom.startWaiting = true;
        phantom.notices.addAll(compiled.notices());

        NetworkSnapshotter.Snapshot snapshot =
                NetworkSnapshotter.snapshot(player.level(), trackGraph, translation.topology());
        List<SimTrainSpec> specs = new ArrayList<>();
        specs.add(phantom);
        specs.addAll(snapshot.specs());

        boolean daylight = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        long dayTime = player.serverLevel().getDayTime();
        long startDayTime = settings.startNow() ? dayTime
                : nextOccurrence(dayTime, settings.startHour(), settings.startMinute());
        SimClock clock = new SimClock(startDayTime, daylight ? 1 : 0);

        long horizonTicks = (long) Math.min(settings.horizonHours(),
                RealismConfig.COMMON.SimMaxHorizonHours.get()) * TICKS_PER_HOUR;

        activePlayers.add(player.getUUID());
        lastRequestGameTime.put(player.getUUID(), now);

        MinecraftServer server = player.getServer();
        SimGraph finalGraph = simGraph;
        List<NetworkSnapshotter.Excluded> excluded = snapshot.excluded();
        long maxWallMillis = RealismConfig.COMMON.SimMaxWallSeconds.get() * 1000L;
        WORKER.submit(() -> {
            SimulationPayload payload;
            try {
                long wallStart = System.currentTimeMillis();
                SimResult result = new SimEngine(finalGraph, specs, clock, horizonTicks,
                        SAMPLE_STRIDE, maxWallMillis).run();
                payload = buildPayload(result, excluded, clock, horizonTicks);
                payload.perfSummary = perfSummary(result, System.currentTimeMillis() - wallStart);
            } catch (Throwable t) {
                RealismMod.LOGGER.error("Simulation failed", t);
                payload = SimulationPayload.refusal("realism.sim.refuse.internal_error", "");
            }
            SimulationPayload finalPayload = payload;
            server.execute(() -> {
                activePlayers.remove(player.getUUID());
                if (!player.hasDisconnected())
                    RNetworking.sendToPlayer(new SimulationResultPacket(finalPayload), player);
            });
        });
    }

    private static double phantomAcceleration(Settings settings) {
        double base = AllConfigs.server().trains.trainAcceleration.getF() / 400;
        return switch (settings.accelerationMode()) {
            // Mirrors TrainMixin.acceleration()'s realistic mode.
            case 1 -> Math.max(0.0001, base - settings.carriages() * 0.0002
                    * RealismConfig.COMMON.CustomTrainAccelerationMultiplyer.get()
                    / Math.max(1, settings.locomotives()));
            case 2 -> settings.customAcceleration() / 400;
            default -> base;
        };
    }

    /** Day time of the next wall-clock HH:MM at/after {@code dayTime}. */
    private static long nextOccurrence(long dayTime, int hour, int minute) {
        long target = ((hour + 18) % 24) * 1000L + Math.round(minute / 60.0 * 1000);
        long candidate = Math.floorDiv(dayTime, 24000) * 24000 + target;
        while (candidate < dayTime)
            candidate += 24000;
        return candidate;
    }

    /** Where the compute time went — shown in the results window's notes. */
    private static String perfSummary(SimResult result, long wallMillis) {
        SimResult.Stats stats = result.stats;
        return String.format(java.util.Locale.ROOT,
                "%.1fs (%d ticks; pathfinding %.1fs, %d searches, %d failed, %d memoized)",
                wallMillis / 1000.0, result.ticksSimulated, stats.pathfindNanos / 1e9,
                stats.pathfindCalls, stats.pathfindFails, stats.pathfindMemoHits);
    }

    private static SimulationPayload buildPayload(SimResult result,
                                                  List<NetworkSnapshotter.Excluded> excluded,
                                                  SimClock clock, long horizonTicks) {
        SimulationPayload payload = new SimulationPayload();
        payload.startDayTime = clock.startDayTime();
        payload.dayTimeRate = clock.dayTimeRate();
        payload.horizonTicks = horizonTicks;
        payload.ticksSimulated = result.ticksSimulated;
        payload.truncated = result.truncated;

        for (int i = 0; i < result.trains.size(); i++) {
            SimResult.TrainResult train = result.trains.get(i);
            List<SimulationPayload.Visit> visits = new ArrayList<>();
            for (SimResult.StationVisit visit : train.visits)
                visits.add(new SimulationPayload.Visit(visit.entryIndex(), visit.stationName(),
                        visit.arrivalTick(), visit.departureTick()));
            payload.trains.add(new SimulationPayload.TrainLine(train.name, i == 0,
                    train.obstacle, train.endState, train.notices, visits));
        }
        for (NetworkSnapshotter.Excluded line : excluded)
            payload.excluded.add(new SimulationPayload.ExcludedLine(line.trainName(),
                    line.translationKey(), line.detail()));
        return payload;
    }

    private static void refuse(ServerPlayer player, String translationKey, String detail) {
        RNetworking.sendToPlayer(new SimulationResultPacket(
                SimulationPayload.refusal(translationKey, detail)), player);
    }
}
