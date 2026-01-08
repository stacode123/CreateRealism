package net.Realism.content.simulator.schedule;

import net.Realism.content.graph.BlockEdge;
import net.Realism.content.graph.BlockStation;
import net.Realism.content.simulator.SimulatedTrain;

public class StationArrivalEntry extends ScheduleEntry {
    private final String stationName;
    private boolean arrived = false;

    private BlockStation targetStationCache = null;
    
    public StationArrivalEntry(String stationName) {
        this.stationName = stationName;
    }

    @Override
    public void tick(SimulatedTrain train) {
        if (arrived) return;

        BlockStation targetStation = findTargetStation(train);
        if (targetStation == null) {
            System.err.println("[SimulatedTrain] Target station " + stationName + " not found!");
            arrived = true; // Skip if not found
            return;
        }

        double distance = targetStation.getDistanceToStation(train);
        double signalDistance = train.getDistanceToNextSignal();
        
        // Braking logic: v = sqrt(2 * a * d)
        double maxSafeSpeed = Math.sqrt(2 * train.acceleration * Math.max(0, distance));
        double maxSignalSpeed = Math.sqrt(2 * train.acceleration * Math.max(0, signalDistance));
        
        train.targetSpeed = Math.min(train.maxSpeedFactor, Math.min(maxSafeSpeed, maxSignalSpeed));

        if (distance <= 0.05 && train.currentSpeed <= 0.1) {
            train.edgeLocation = targetStation.location;
            train.currentSpeed = 0;
            train.targetSpeed = 0;
            long timeTaken = train.lastStopTick == 0 ? 0 : train.currentTick - train.lastStopTick;
            System.out.println("[SimulatedTrain] Arrived at station " + stationName + " at tick " + train.currentTick + ". Time since last stop: " + timeTaken);
            arrived = true;
            train.path.clear();
            return;
        }

        train.moveTrain(targetStation);
    }

    private BlockStation findTargetStation(SimulatedTrain train) {
        if (targetStationCache != null) return targetStationCache;
        for (BlockEdge e : train.graph.edges) {
            for (BlockStation s : e.stations) {
                if (s.station != null && s.station.name.equals(stationName)) {
                    targetStationCache = s;
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isFinished(SimulatedTrain train) {
        return arrived;
    }

    @Override
    public void reset() {
        this.arrived = false;
        this.targetStationCache = null;
    }

    public String getStationName() {
        return stationName;
    }
}
