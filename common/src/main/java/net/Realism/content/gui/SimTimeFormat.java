package net.Realism.content.gui;

import net.Realism.content.simulator.SimulationPayload;

/**
 * Formats simulation ticks for display, shared by the results window, the
 * time-distance diagram and the schedule-card overlay. Day-length aware:
 * when day time is frozen ({@code dayTimeRate <= 0}) times fall back to
 * elapsed real time.
 */
public class SimTimeFormat {

    /**
     * A tick relative to sim start as wall-clock time ("D+1 08:30") when day
     * time advances, otherwise as elapsed real time ("+12:30").
     */
    public static String time(SimulationPayload payload, long tick) {
        if (payload.dayTimeRate <= 0) {
            long seconds = tick / 20;
            return String.format("+%d:%02d", seconds / 60, seconds % 60);
        }
        long dayTime = payload.startDayTime + Math.round(tick * payload.dayTimeRate);
        long startDay = Math.floorDiv(payload.startDayTime + 6000, 24000);
        long day = Math.floorDiv(dayTime + 6000, 24000);
        int hour = (int) ((dayTime / 1000 + 6) % 24);
        int minute = (int) ((dayTime % 1000) * 60 / 1000);
        String clock = String.format("%02d:%02d", hour, minute);
        return day > startDay ? "D+" + (day - startDay) + " " + clock : clock;
    }

    /** A tick count as in-game clock duration ("14m"), or real "m:ss" when frozen. */
    public static String duration(SimulationPayload payload, long ticks) {
        if (payload.dayTimeRate <= 0) {
            long seconds = ticks / 20;
            return String.format("%d:%02d", seconds / 60, seconds % 60);
        }
        long minutes = Math.round(ticks * payload.dayTimeRate * 60 / 1000.0);
        if (minutes < 1)
            return "<1m";
        return minutes < 60 ? minutes + "m" : (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
