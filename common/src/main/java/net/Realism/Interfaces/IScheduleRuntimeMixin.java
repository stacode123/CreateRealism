package net.Realism.Interfaces;

public interface IScheduleRuntimeMixin {
    long getDepartureDate();
    long getExpectedArrivalDate();
    void setExpectedArrivalDate(long date);
    int getLastScheduledDepartureDate();
    void setLastScheduledDepartureDate(int date);
    boolean dontCheck();
    void setDontCheck(boolean dontCheck);
}
