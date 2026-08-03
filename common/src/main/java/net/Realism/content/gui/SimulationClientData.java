package net.Realism.content.gui;

import com.simibubi.create.content.trains.schedule.Schedule;
import net.Realism.content.simulator.SimulationPayload;

/**
 * Client-side memory of the last delivered simulation, consumed by the
 * schedule-card time overlay and the map's conflict badges. The schedule
 * hash pins results to the schedule content they were computed from, so
 * edits after the run grey the overlay out instead of lying.
 */
public class SimulationClientData {

    /** Last non-refused payload, or null before the first successful run. */
    public static SimulationPayload lastResults;
    /** Hash of the schedule as it was when the request was sent. */
    public static int pendingScheduleHash;
    /** Hash the delivered {@link #lastResults} belongs to. */
    public static int lastScheduleHash;

    /** Content identity of a schedule, via its canonical NBT form. */
    public static int hash(Schedule schedule) {
        return schedule.write().toString().hashCode();
    }
}
