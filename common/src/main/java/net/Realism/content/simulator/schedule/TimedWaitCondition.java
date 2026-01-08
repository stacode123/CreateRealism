package net.Realism.content.simulator.schedule;

import net.Realism.content.simulator.SimulatedTrain;

public class TimedWaitCondition implements ScheduleCondition {
    private final int totalTicks;
    private int currentTicks = 0;

    public TimedWaitCondition(int ticks) {
        this.totalTicks = ticks;
    }

    @Override
    public void tick(SimulatedTrain train) {
        train.targetSpeed = 0;
        if (currentTicks < totalTicks) {
            currentTicks++;
            if (currentTicks == 1) {
                // Record stop for data collection
                if (train.lastStopTick != 0) {
                    train.timeBetweenStops.add(train.currentTick - train.lastStopTick);
                }
                train.lastStopTick = train.currentTick;
                System.out.println("[SimulatedTrain] Starting wait for " + totalTicks + " ticks");
            }
        }
    }

    @Override
    public boolean isMet(SimulatedTrain train) {
        return currentTicks >= totalTicks;
    }

    @Override
    public void reset() {
        currentTicks = 0;
    }

}
