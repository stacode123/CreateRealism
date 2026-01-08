package net.Realism.content.simulator.schedule;

import net.Realism.content.simulator.SimulatedTrain;

import java.util.ArrayList;
import java.util.List;

public abstract class ScheduleEntry {
    protected List<ScheduleCondition> conditions = new ArrayList<>();

    public abstract void tick(SimulatedTrain train);
    public abstract boolean isFinished(SimulatedTrain train);

    public void addCondition(ScheduleCondition condition) {
        conditions.add(condition);
    }

    public List<ScheduleCondition> getConditions() {
        return conditions;
    }

    public boolean allConditionsMet(SimulatedTrain train) {
        for (ScheduleCondition condition : conditions) {
            if (!condition.isMet(train)) {
                return false;
            }
        }
        return true;
    }

    public void tickConditions(SimulatedTrain train) {
        for (ScheduleCondition condition : conditions) {
            condition.tick(train);
        }
    }

    public void resetConditions() {
        for (ScheduleCondition condition : conditions) {
            condition.reset();
        }
    }

    public abstract void reset();
}
