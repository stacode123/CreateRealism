package net.Realism.Interfaces;

public interface IScheduleRuntimeMixin {
    /** Game time at which the train started waiting at its current stop, or 0 if it never has. */
    long getArrivalGameTime();

    /** Game time of the departure slot this train last left on, or 0 if it has not left one yet. */
    long getLastDepartureSlot();

    void setLastDepartureSlot(long slot);
}
