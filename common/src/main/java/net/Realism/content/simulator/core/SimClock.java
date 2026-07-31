package net.Realism.content.simulator.core;

/**
 * Maps engine ticks to world day time. {@code dayTimeRate} is how many
 * day-time ticks pass per real server tick — 1 in vanilla, 0 with
 * doDaylightCycle off, other values under time-speed mods. Time-of-day
 * conditions read day time; movement always runs in real ticks.
 */
public record SimClock(long startDayTime, double dayTimeRate) {

    public long dayTimeAt(long tick) {
        return startDayTime + Math.round(tick * dayTimeRate);
    }
}
