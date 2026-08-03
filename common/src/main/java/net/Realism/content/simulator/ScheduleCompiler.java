package net.Realism.content.simulator;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import net.Realism.content.simulator.core.SimCondition;
import net.Realism.content.simulator.core.SimProgram;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compiles a live Create {@code Schedule} into a deterministic
 * {@link SimProgram}. Everything is dispatched by registered id and read from
 * the entry's data tag — no CRN classes are referenced, so this works whether
 * or not CRN is installed. Unsupported content becomes {@link Problem}s: a
 * network train with problems is excluded from simulation; a phantom schedule
 * with problems refuses to simulate.
 */
public class ScheduleCompiler {

    /** An unsupported piece of schedule. {@code translationKey} gets {@code detail} as arg. */
    public record Problem(String translationKey, String detail) {}

    public record CompileResult(SimProgram program, List<Problem> problems, List<String> notices) {

        public boolean clean() {
            return problems.isEmpty();
        }
    }

    public static CompileResult compile(Schedule schedule) {
        SimProgram program = new SimProgram();
        program.cyclic = schedule.cyclic;
        List<Problem> problems = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        // CRN section identity, switched by travel-section instructions
        // (mirrors ScheduleSection: default/no identity before the first).
        String lineToken = null;
        String categoryToken = null;

        for (ScheduleEntry scheduleEntry : schedule.entries) {
            ScheduleInstruction instruction = scheduleEntry.instruction;
            if (instruction == null) {
                problems.add(new Problem("realism.sim.problem.unknown_instruction", "?"));
                continue;
            }
            String id = instruction.getId().toString();
            CompoundTag data = instruction.getData();

            SimProgram.Entry entry;
            switch (id) {
                case "create:destination" ->
                        entry = SimProgram.Entry.destination(data.getString("Text"));
                // Full CRN semantics: filters tried in priority order, first
                // reachable one wins; "AvoidTrains" prefers a later filter
                // over a busy station. "AvoidRedSignal" only counts signals
                // around the train itself — the same for every filter — so
                // it can never change which filter wins and is ignored.
                case "createrailwaysnavigator:prioritized_destination_instruction" -> {
                    List<String> filters = new ArrayList<>();
                    data.getList("Filters", Tag.TAG_STRING).forEach(tag -> filters.add(tag.getAsString()));
                    if (filters.isEmpty()) {
                        problems.add(new Problem("realism.sim.problem.empty_destination", id));
                        continue;
                    }
                    entry = SimProgram.Entry.destinationPrioritized(filters,
                            data.getBoolean("AvoidTrains"));
                }
                case "create:throttle" ->
                        entry = SimProgram.Entry.throttle(
                                (data.contains("Value") ? data.getInt("Value") : 100) / 100.0);
                // Navigation-wise a plain destination; whether the tram is
                // actually flagged down is live player state, so the sim
                // assumes every request stop is requested.
                case "tramways:request_stop" -> {
                    entry = SimProgram.Entry.destination(data.getString("Text"));
                    notices.add("realism.sim.notice.request_stop");
                }
                // Extends Create's throttle instruction and applies the same
                // Value as train throttle on start, which is what we model;
                // the tram-sign primary limit side effect is close enough.
                case "tramways:set_primary_limit" -> {
                    entry = SimProgram.Entry.throttle(
                            (data.contains("Value") ? data.getInt("Value") : 100) / 100.0);
                    notices.add("realism.sim.notice.primary_limit");
                }
                // SnR waypoint: same "Text" glob as a destination, but the
                // train rolls through without stopping.
                case "railways:waypoint_destination" ->
                        entry = SimProgram.Entry.waypointDestination(data.getString("Text"));
                // Fires a redstone link pulse; never moves the train.
                case "create:rename", "createrailwaysnavigator:reset_timings",
                        "railways:redstone_link" ->
                        entry = SimProgram.Entry.noOp();
                case "createrailwaysnavigator:travel_section" -> {
                    lineToken = lineToken(data);
                    categoryToken = categoryToken(data);
                    entry = SimProgram.Entry.noOp();
                }
                default -> {
                    problems.add(new Problem("realism.sim.problem.unknown_instruction", id));
                    continue;
                }
            }

            entry.lineToken = lineToken;
            entry.categoryToken = categoryToken;
            if (entry.kind == SimProgram.InstructionKind.DESTINATION)
                compileConditions(scheduleEntry, entry, problems, notices);
            program.entries.add(entry);
        }

        // Repeated instructions repeat their notices; show each once.
        return new CompileResult(program, problems,
                new ArrayList<>(new java.util.LinkedHashSet<>(notices)));
    }

    private static void compileConditions(ScheduleEntry scheduleEntry, SimProgram.Entry entry,
                                          List<Problem> problems, List<String> notices) {
        for (List<ScheduleWaitCondition> column : scheduleEntry.conditions) {
            List<SimCondition> compiled = new ArrayList<>();
            for (ScheduleWaitCondition condition : column) {
                if (condition == null) {
                    // Create drops unknown conditions on NBT load; skipping
                    // mirrors what the real train would do.
                    notices.add("realism.sim.notice.null_condition");
                    continue;
                }
                SimCondition simCondition = compileCondition(condition, problems, notices);
                if (simCondition != null)
                    compiled.add(simCondition);
            }
            entry.columns.add(compiled);
        }
    }

    private static SimCondition compileCondition(ScheduleWaitCondition condition,
                                                 List<Problem> problems, List<String> notices) {
        String id = condition.getId().toString();
        CompoundTag data = condition.getData();
        switch (id) {
            case "create:delay":
                return new SimCondition.Delay(timedTicks(data));
            case "create:time_of_day":
                return new SimCondition.TimeOfDay(data.getInt("Hour"), data.getInt("Minute"),
                        rotationTicks(data.getInt("Rotation")));
            case "realism:time_of_day_realistic":
                return new SimCondition.TimeOfDayRealistic(data.getInt("Hour"), data.getInt("Minute"));
            // SnR "station loaded": waits for the station's chunk, which
            // depends on live players — the projection assumes it is loaded.
            case "railways:loaded":
                notices.add("realism.sim.notice.station_loaded");
                return new SimCondition.Delay(0);
            case "createrailwaysnavigator:dynamic_delay":
                return new SimCondition.DynamicDelay(timedTicks(data),
                        data.getInt("Min") * unitTicks(data));
            case "createrailwaysnavigator:train_separation": {
                // Line/category identity is carried on each compiled entry
                // (from travel-section instructions), so these filters
                // compare for real — matching CRN's DepartureHistory.
                SimCondition.TrainFilter filter = switch (data.getByte("TrainFilter")) {
                    case 1 -> SimCondition.TrainFilter.SAME_LINE;
                    case 2 -> SimCondition.TrainFilter.SAME_CATEGORY;
                    case 3 -> SimCondition.TrainFilter.SAME_NAME;
                    default -> SimCondition.TrainFilter.ANY;
                };
                return new SimCondition.Separation(data.getInt("Ticks"), filter,
                        data.getString("StationFilter"));
            }
            default:
                problems.add(new Problem("realism.sim.problem.unpredictable_condition", id));
                return null;
        }
    }

    /**
     * CRN {@code TravelSectionInstruction} line reference as a comparison
     * token: modern data stores the line UUID, legacy data the line name —
     * CRN maps names via {@code TrainLine.genMD5Uuid} (UUID v3 of the UTF-8
     * name), reproduced here so both formats compare equal.
     */
    private static String lineToken(CompoundTag data) {
        if (data.hasUUID("TrainLine"))
            return data.getUUID("TrainLine").toString();
        String name = data.getString("TrainLine");
        if (name.isBlank())
            return null;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Category UUID reference; legacy "TrainGroup" names get their own token. */
    private static String categoryToken(CompoundTag data) {
        if (data.hasUUID("TrainCategory"))
            return data.getUUID("TrainCategory").toString();
        String legacy = data.getString("TrainGroup");
        return legacy.isBlank() ? null : "group:" + legacy;
    }

    private static int timedTicks(CompoundTag data) {
        return data.getInt("Value") * unitTicks(data);
    }

    /** Create's {@code TimedWaitCondition.TimeUnit} ordinals. */
    private static int unitTicks(CompoundTag data) {
        return switch (data.getInt("TimeUnit")) {
            case 0 -> 1;
            case 2 -> 20 * 60;
            default -> 20;
        };
    }

    /** Create's {@code TimeOfDayCondition.getRotation} mapping. */
    private static int rotationTicks(int index) {
        return switch (index) {
            case 9 -> 250;
            case 8 -> 500;
            case 7 -> 750;
            case 6 -> 1000;
            case 5 -> 2000;
            case 4 -> 3000;
            case 3 -> 4000;
            case 2 -> 6000;
            case 1 -> 12000;
            default -> 24000;
        };
    }
}
