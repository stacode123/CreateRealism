package net.Realism.content.simulator.core;

/**
 * A deterministic wait condition. Each variant mirrors the exact tick logic
 * of its in-game counterpart (see the source class named on each record) so
 * simulated departure times match real ones.
 */
public sealed interface SimCondition {

    /**
     * One condition-slot tick, mirroring {@code ScheduleWaitCondition
     * .tickCompletion}. {@code elapsed} is the ticks this condition has been
     * the active one in its column (Create's context {@code Time}).
     *
     * @return true when complete
     */
    boolean tick(SimClock clock, long tick, int elapsed, TrainState train);

    /** Create's {@code ScheduledDelay}: complete after {@code ticks}. */
    record Delay(int ticks) implements SimCondition {
        @Override
        public boolean tick(SimClock clock, long tick, int elapsed, TrainState train) {
            return elapsed >= ticks;
        }
    }

    /**
     * Create's {@code TimeOfDayCondition}: completes while
     * {@code dayTime % rotation} sits in the 40-tick window at the target.
     */
    record TimeOfDay(int hour, int minute, int rotation) implements SimCondition {
        @Override
        public boolean tick(SimClock clock, long tick, int elapsed, TrainState train) {
            int dayTime = (int) (clock.dayTimeAt(tick) % rotation);
            int targetTicks = (int) ((((hour + 18) % 24) * 1000 + Math.ceil(minute / 60f * 1000)) % rotation);
            int diff = dayTime - targetTicks;
            return diff >= 0 && diff <= 40;
        }
    }

    /**
     * Realism's {@code TimeOfDayRealistic}: departs at the first occurrence
     * of HH:MM at/after arrival; if that moment already passed on the arrival
     * day it departs immediately, unless the schedule wrapped past midnight
     * (target time-of-day earlier than the last scheduled departure), in
     * which case it waits for the next day's occurrence.
     */
    record TimeOfDayRealistic(int hour, int minute) implements SimCondition {
        @Override
        public boolean tick(SimClock clock, long tick, int elapsed, TrainState train) {
            long sinceMidnight = clock.dayTimeAt(tick) + 6000;
            long day = Math.floorDiv(sinceMidnight, 24000);
            int targetDayTime = (int) (hour * 1000 + Math.ceil(minute / 60f * 1000));
            long target = day * 24000 + targetDayTime;
            if (target < sinceMidnight && targetDayTime < train.lastScheduledDepartureDayTime)
                target += 24000;
            if (sinceMidnight >= target) {
                train.lastScheduledDepartureDayTime = targetDayTime;
                return true;
            }
            return false;
        }
    }

    /**
     * CRN's {@code DynamicDelayCondition}. Its in-game dwell shrinks by the
     * live delay CRN measured against its own predictions; a static
     * projection has no such baseline, so the deterministic model is the full
     * scheduled dwell (CRN's own behavior when its data is uninitialized).
     */
    record DynamicDelay(int ticks, int minTicks) implements SimCondition {
        @Override
        public boolean tick(SimClock clock, long tick, int elapsed, TrainState train) {
            return elapsed >= ticks;
        }
    }

    /**
     * CRN's {@code TrainSeparationCondition}: completes its column instantly
     * but registers a departure gate — the train may only leave once the last
     * matching departure is at least {@code ticks} old (see
     * {@link SimEngine} departure handling).
     */
    record Separation(int ticks, TrainFilter filter, String stationFilter)
            implements SimCondition {
        @Override
        public boolean tick(SimClock clock, long tick, int elapsed, TrainState train) {
            train.departureGates.add(this);
            return true;
        }
    }

    /**
     * CRN departure-history filters. Line and category identity come from
     * the schedule's travel-section instructions (compiled into each entry
     * as opaque tokens), matching CRN's {@code ScheduleSection} resolution.
     */
    enum TrainFilter {
        ANY, SAME_NAME, SAME_LINE, SAME_CATEGORY
    }
}
