package net.Realism.Interfaces;

public interface IScheduleRuntimeMixin {
    /** Day time at which the train started waiting at its current stop, or 0 if it never has. */
    long getArrivalDayTime();

    /** Day time of the departure this train last left on, or 0 if it has not left one yet. */
    long getLastDepartureDayTime();

    void setLastDepartureDayTime(long dayTime);
}
