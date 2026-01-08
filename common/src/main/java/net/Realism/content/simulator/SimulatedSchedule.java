package net.Realism.content.simulator;
import net.Realism.content.simulator.schedule.ScheduleEntry;

import java.util.ArrayList;
import java.util.List;

public class SimulatedSchedule{
    public List<ScheduleEntry> entries = new ArrayList<>();
    public int currentEntryIndex = 0;
    public SimulatedTrain train;

    public void addEntry(ScheduleEntry entry) {
        entries.add(entry);
    }

    public ScheduleEntry getCurrentEntry() {
        if (currentEntryIndex >= entries.size()) {
            return null;
        }
        return entries.get(currentEntryIndex);
    }

    public void nextEntry() {
        currentEntryIndex++;
        if (currentEntryIndex >= entries.size()) {
            currentEntryIndex = 0; // Loop schedule
        }
        ScheduleEntry nextEntry = getCurrentEntry();
        if (nextEntry != null) {
            nextEntry.reset();
            nextEntry.resetConditions();
        }
    }
}
