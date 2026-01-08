package net.Realism.content.simulator.schedule;

import net.Realism.content.simulator.SimulatedTrain;

public interface ScheduleCondition {
    void tick(SimulatedTrain train);
    boolean isMet(SimulatedTrain train);
    void reset();
}
