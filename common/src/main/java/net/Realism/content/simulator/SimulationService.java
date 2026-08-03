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
import net.Realism.content.simulator.core.*;
import net.Realism.content.trains.schedule.AdvancedScheduleItem;
import net.Realism.foundation.network.SimulationResultPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import java.util.*;
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

    /**
     * {@code headwaySeconds} < 0 means "use the server config default".
     * {@code thorough} runs a second baseline simulation without the phantom
     * and reports only conflicts the schedule causes.
     */
    public record Settings(int carriages, int locomotives, int accelerationMode,
                           double customAcceleration, int horizonHours, boolean startNow,
                           int startHour, int startMinute, int headwaySeconds,
                           boolean thorough) {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Realism-Simulator");
        thread.setDaemon(true);
        return thread;
    });
    private static final int SAMPLE_STRIDE = 100;
    /** Conflict lines sent to the client; the rest is counted, not shipped. */
    private static final int MAX_CONFLICT_LINES = 300;
    /** Root-cause chains shipped, and stranded names listed per chain. */
    private static final int MAX_ROOT_CAUSE_LINES = 20;
    private static final int MAX_ROOT_CAUSE_NAMES = 10;
    /** Diagram payload budget: trains drawn, and points per polyline segment. */
    private static final int MAX_DIAGRAM_LINES = 40;
    private static final int MAX_DIAGRAM_POINTS = 300;
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
        List<String> dimensionNames = List.copyOf(translation.graph().dimensions);
        // CRN station tags group platforms into logical stations for the
        // diagram's distance axis; read on the server thread.
        Map<String, String> stationGroups = net.Realism.compat.CrnCompat.stationTagGroups();
        Set<String> blacklistedStations = net.Realism.compat.CrnCompat.blacklistedStations();
        Set<Integer> hiddenDiagramTrains = hiddenDiagramTrains(specs);
        java.nio.file.Path debugFile = RealismConfig.COMMON.SimDebugExport.get()
                ? server.getServerDirectory().toPath().resolve("realism-sim-debug.html")
                : null;
        // Debug playback wants fine motion; scale the stride so a run still
        // caps out around ~5k samples per train on long horizons.
        int sampleStride = debugFile != null
                ? (int) Math.max(10, Math.min(SAMPLE_STRIDE, horizonTicks / 4800))
                : SAMPLE_STRIDE;
        List<NetworkSnapshotter.Excluded> excluded = snapshot.excluded();
        long maxWallMillis = RealismConfig.COMMON.SimMaxWallSeconds.get() * 1000L;
        long waitConflictTicks = RealismConfig.COMMON.SimWaitConflictSeconds.get() * 20L;
        long headwayConflictTicks = (settings.headwaySeconds() >= 0
                ? settings.headwaySeconds()
                : RealismConfig.COMMON.SimHeadwaySeconds.get()) * 20L;
        WORKER.submit(() -> {
            SimulationPayload payload;
            String[] debugWritten = { null };
            try {
                long wallStart = System.currentTimeMillis();
                SimResult result = new SimEngine(finalGraph, specs, clock, horizonTicks,
                        sampleStride, maxWallMillis, waitConflictTicks, headwayConflictTicks).run();
                // Thorough mode: a second run without the phantom tells us
                // which conflicts pre-exist — only the schedule-caused ones
                // (including knock-on effects between other trains) remain.
                Set<String> baselineKeys = null;
                long baselineMillis = 0;
                if (settings.thorough()) {
                    long baselineStart = System.currentTimeMillis();
                    SimResult baseline = new SimEngine(finalGraph,
                            new ArrayList<>(specs.subList(1, specs.size())), clock, horizonTicks,
                            SAMPLE_STRIDE, maxWallMillis, waitConflictTicks, headwayConflictTicks)
                            .run();
                    baselineKeys = new java.util.HashSet<>();
                    for (SimConflict conflict : baseline.conflicts)
                        baselineKeys.add(conflictKey(conflict, baseline));
                    baselineMillis = System.currentTimeMillis() - baselineStart;
                }
                SimDiagram diagram = SimDiagram.build(finalGraph, result, specs, 0,
                        MAX_DIAGRAM_LINES, MAX_DIAGRAM_POINTS, stationGroups,
                        blacklistedStations, hiddenDiagramTrains);
                payload = buildPayload(result, excluded, specs, clock, horizonTicks, baselineKeys,
                        diagram, dimensionNames, finalGraph);
                payload.thorough = settings.thorough();
                payload.perfSummary = perfSummary(result, System.currentTimeMillis() - wallStart);
                if (settings.thorough())
                    payload.perfSummary += String.format(java.util.Locale.ROOT,
                            " + %.1fs baseline", baselineMillis / 1000.0);
                if (debugFile != null) {
                    try {
                        java.nio.file.Files.writeString(debugFile, SimDebugExporter.buildHtml(
                                finalGraph, result, specs, dimensionNames, stationGroups,
                                clock.startDayTime(), clock.dayTimeRate(), sampleStride));
                        // The raw dataset as plain JSON, for reading/sharing.
                        java.nio.file.Files.writeString(
                                debugFile.resolveSibling("realism-sim-debug.json"),
                                SimDebugExporter.buildJson(finalGraph, result, specs,
                                        dimensionNames, stationGroups,
                                        clock.startDayTime(), clock.dayTimeRate(), sampleStride));
                        debugWritten[0] = debugFile.toAbsolutePath().toString();
                    } catch (Exception e) {
                        RealismMod.LOGGER.error("Failed to write sim debug export", e);
                    }
                }
            } catch (Throwable t) {
                RealismMod.LOGGER.error("Simulation failed", t);
                payload = SimulationPayload.refusal("realism.sim.refuse.internal_error", "");
            }
            SimulationPayload finalPayload = payload;
            server.execute(() -> {
                activePlayers.remove(player.getUUID());
                if (player.hasDisconnected())
                    return;
                RNetworking.sendToPlayer(new SimulationResultPacket(finalPayload), player);
                if (debugWritten[0] != null)
                    player.displayClientMessage(net.minecraft.network.chat.Component
                            .translatable("realism.sim.debug_written", debugWritten[0]), false);
            });
        });
    }

    /**
     * Trains hidden from the diagram by the {@code Sim Diagram Hidden
     * Categories} config: any train whose CRN train category name (from its
     * schedule's travel sections) contains one of the configured words.
     * The phantom (index 0) is never hidden.
     */
    private static Set<Integer> hiddenDiagramTrains(List<SimTrainSpec> specs) {
        List<String> hiddenWords = new ArrayList<>();
        for (String word : RealismConfig.COMMON.SimDiagramHiddenCategories.get())
            if (!word.isBlank())
                hiddenWords.add(word.toLowerCase(java.util.Locale.ROOT));
        if (hiddenWords.isEmpty())
            return Set.of();
        Map<String, String> categoryNames = net.Realism.compat.CrnCompat.trainCategoryNames();
        Set<Integer> hidden = new java.util.HashSet<>();
        for (int i = 1; i < specs.size(); i++) {
            SimProgram program = specs.get(i).program;
            if (program == null)
                continue;
            entries:
            for (SimProgram.Entry entry : program.entries) {
                if (entry.categoryToken == null)
                    continue;
                String name = entry.categoryToken.startsWith("group:")
                        ? entry.categoryToken.substring("group:".length())
                        : categoryNames.getOrDefault(entry.categoryToken, "");
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                for (String word : hiddenWords)
                    if (lower.contains(word)) {
                        hidden.add(i);
                        break entries;
                    }
            }
        }
        return hidden;
    }

    /**
     * Temporary debug aid for the diagram tooltip: the distinct CRN category
     * names a train's schedule references, resolved exactly like
     * {@link #hiddenDiagramTrains} resolves them (unresolvable UUID tokens
     * stay raw so mismatches are visible).
     */
    /** Whether any of the entry's destination filters matches a real station. */
    private static boolean anyStationExists(SimGraph graph, SimProgram.Entry entry) {
        if (entry.patterns == null)
            return true;
        for (java.util.regex.Pattern pattern : entry.patterns)
            if (!graph.findStations(pattern).isEmpty())
                return true;
        return false;
    }

    private static String categoryText(SimTrainSpec spec, Map<String, String> categoryNames) {
        if (spec.program == null)
            return "";
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (SimProgram.Entry entry : spec.program.entries) {
            if (entry.categoryToken == null)
                continue;
            names.add(entry.categoryToken.startsWith("group:")
                    ? entry.categoryToken.substring("group:".length())
                    : categoryNames.getOrDefault(entry.categoryToken, entry.categoryToken));
        }
        return String.join(", ", names);
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

    /**
     * A run-independent identity for a conflict, used to subtract baseline
     * conflicts in thorough mode. Deliberately excludes times and positions:
     * the phantom shifts both slightly for pre-existing conflicts, and a
     * same-trains same-type conflict that merely moved is still background.
     * Conflicts involving the phantom can never match a baseline key.
     */
    private static String conflictKey(SimConflict conflict, SimResult result) {
        List<String> names = new ArrayList<>();
        for (int trainIndex : conflict.trains())
            names.add(result.trains.get(trainIndex).name);
        names.sort(String::compareTo);
        return conflict.type().ordinal() + "|" + conflict.resourceName() + "|"
                + String.join(",", names);
    }

    private static SimulationPayload buildPayload(SimResult result,
                                                  List<NetworkSnapshotter.Excluded> excluded,
                                                  List<SimTrainSpec> specs,
                                                  SimClock clock, long horizonTicks,
                                                  Set<String> baselineKeys,
                                                  SimDiagram diagram, List<String> dimensionNames,
                                                  SimGraph graph) {
        SimulationPayload payload = new SimulationPayload();
        payload.startDayTime = clock.startDayTime();
        payload.dayTimeRate = clock.dayTimeRate();
        payload.horizonTicks = horizonTicks;
        payload.ticksSimulated = result.ticksSimulated;
        payload.truncated = result.truncated;

        // Failed navigations per train: without this, a train whose route
        // search fails just sits "preparing to depart" with no explanation.
        Map<Integer, java.util.Set<Integer>> failedEntries = new java.util.HashMap<>();
        for (SimResult.SimEvent event : result.events)
            if (event.type() == SimResult.EventType.PATH_FAILED)
                failedEntries.computeIfAbsent(event.trainIndex(), k -> new java.util.LinkedHashSet<>())
                        .add((int) event.data());

        for (int i = 0; i < result.trains.size(); i++) {
            SimResult.TrainResult train = result.trains.get(i);
            List<SimulationPayload.Visit> visits = new ArrayList<>();
            for (SimResult.StationVisit visit : train.visits)
                visits.add(new SimulationPayload.Visit(visit.entryIndex(), visit.stationName(),
                        visit.arrivalTick(), visit.departureTick()));
            List<String> notices = new ArrayList<>(train.notices);
            SimProgram program = specs.get(i).program;
            for (int entryIndex : failedEntries.getOrDefault(i, java.util.Set.of()))
                if (program != null && entryIndex < program.entries.size()) {
                    SimProgram.Entry entry = program.entries.get(entryIndex);
                    // A filter matching no station at all is a schedule typo,
                    // not a routing problem - say so instead of hinting at
                    // one-way signals.
                    notices.add((anyStationExists(graph, entry)
                            ? "realism.sim.notice.path_failed\u001F"
                            : "realism.sim.notice.no_station\u001F")
                            + entry.filterText);
                }
            payload.trains.add(new SimulationPayload.TrainLine(train.name, i == 0,
                    train.obstacle, train.endState, notices, visits));
        }
        for (NetworkSnapshotter.Excluded line : excluded)
            payload.excluded.add(new SimulationPayload.ExcludedLine(line.trainName(),
                    line.translationKey(), line.detail()));

        int kept = 0;
        for (SimConflict conflict : result.conflicts) {
            if (baselineKeys != null && baselineKeys.contains(conflictKey(conflict, result)))
                continue;
            kept++;
            if (payload.conflicts.size() >= MAX_CONFLICT_LINES) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (int trainIndex : conflict.trains())
                names.add(result.trains.get(trainIndex).name);
            String dimension = conflict.dimension() >= 0
                    && conflict.dimension() < dimensionNames.size()
                    ? dimensionNames.get(conflict.dimension()) : "";
            payload.conflicts.add(new SimulationPayload.ConflictLine(conflict.type().ordinal(),
                    conflict.startTick(), conflict.endTick(), conflict.count(),
                    (int) Math.round(conflict.position().x()),
                    (int) Math.round(conflict.position().y()),
                    (int) Math.round(conflict.position().z()),
                    conflict.resourceName(), names, conflict.nonDeterministic(), dimension,
                    (float) diagram.project(conflict.anchorEdge(), conflict.anchorOffset())));
        }
        payload.conflictsDropped = kept - payload.conflicts.size();

        for (SimResult.RootCause cause : result.rootCauses) {
            if (payload.rootCauses.size() >= MAX_ROOT_CAUSE_LINES)
                break;
            List<String> strandedNames = new ArrayList<>();
            for (int index : cause.stranded()) {
                if (strandedNames.size() >= MAX_ROOT_CAUSE_NAMES)
                    break;
                strandedNames.add(result.trains.get(index).name);
            }
            String dimension = cause.dimension() >= 0 && cause.dimension() < dimensionNames.size()
                    ? dimensionNames.get(cause.dimension()) : "";
            payload.rootCauses.add(new SimulationPayload.RootCauseLine(
                    result.trains.get(cause.rootTrain()).name, cause.kind().ordinal(),
                    cause.detail(), strandedNames, cause.stranded().size(),
                    cause.stranded().contains(0), cause.sinceTick(),
                    (int) Math.round(cause.position().x()),
                    (int) Math.round(cause.position().y()),
                    (int) Math.round(cause.position().z()), dimension));
        }
        // Chains stranding the phantom lead the list.
        payload.rootCauses.sort(Comparator.comparing(
                (SimulationPayload.RootCauseLine line) -> !line.phantomStranded()));

        payload.diagramLength = (float) diagram.corridorLength;
        for (SimDiagram.StationMark station : diagram.stations)
            payload.diagramStations.add(new SimulationPayload.DiagramStation(
                    station.name(), (float) station.pos()));
        Map<String, String> categoryNames = net.Realism.compat.CrnCompat.trainCategoryNames();
        for (SimDiagram.Line line : diagram.lines) {
            List<List<SimulationPayload.DiagramPoint>> segments = new ArrayList<>();
            for (List<SimDiagram.Point> segment : line.segments()) {
                List<SimulationPayload.DiagramPoint> points = new ArrayList<>(segment.size());
                for (SimDiagram.Point point : segment)
                    points.add(new SimulationPayload.DiagramPoint(point.tick(), (float) point.pos()));
                segments.add(points);
            }
            payload.diagramLines.add(new SimulationPayload.DiagramLine(
                    result.trains.get(line.train()).name, line.train() == 0,
                    categoryText(specs.get(line.train()), categoryNames), segments));
        }
        payload.diagramLinesDropped = diagram.linesDropped;
        return payload;
    }

    private static void refuse(ServerPlayer player, String translationKey, String detail) {
        RNetworking.sendToPlayer(new SimulationResultPacket(
                SimulationPayload.refusal(translationKey, detail)), player);
    }
}
