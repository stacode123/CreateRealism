package net.Realism.trains;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;


import com.simibubi.create.foundation.utility.Couple;
import com.simibubi.create.foundation.utility.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.*;

public class SignalFinder {

    /**
     * Scans ahead of a train for signals, automatically detecting train direction
     *
     * @param train The train to scan ahead of
     * @param maxDistance Maximum distance to scan
     * @return Information about upcoming signals
     */
    public static SignalScanResult scanAheadForSignals(Train train, double maxDistance) {
        // Determine if train is moving backward based on speed direction
        boolean isMovingBackward = train.speed < 0;
        return scanAheadForSignals(train, maxDistance, isMovingBackward);
    }

    /**
     * Scans ahead of a train for signals, assuming straight path at junctions
     *
     * @param train The train to scan ahead of
     * @param maxDistance Maximum distance to scan
     * @param isMovingBackward Whether the train is moving backward
     * @return Information about upcoming signals
     */
    public static SignalScanResult scanAheadForSignals(Train train, double maxDistance, boolean isMovingBackward) {
        SignalScanResult result = new SignalScanResult();

        if (train.graph == null)
            return result;

        // Create a signal scout starting at appropriate point based on direction
        TravellingPoint scout = new TravellingPoint();
        TravellingPoint startPoint;
        
        if (isMovingBackward) {
            // When moving backward, we need to scan from the "rear" of the train
            // which is actually the back of the last carriage
            startPoint = train.carriages.get(train.carriages.size() - 1).getTrailingPoint();
            
            // Set up scout to look in the opposite direction the train is facing
            // No need to swap nodes - the trailing point is already oriented correctly
            // We just want to travel from this point in the direction of train movement
            scout.node1 = startPoint.node1;
            scout.node2 = startPoint.node2;
            scout.edge = startPoint.edge;
            scout.position = startPoint.position;
            // The travel() method will move in the direction from node1 to node2
            // which matches our backward movement from the train's end
            maxDistance = -maxDistance; // Reverse the distance for backward movement
        } else {
            // Use leading point of first carriage when moving forward
            startPoint = train.carriages.get(0).getLeadingPoint();
            scout.node1 = startPoint.node1;
            scout.node2 = startPoint.node2;
            scout.edge = startPoint.edge;
            scout.position = startPoint.position;
        }

        // For tracking cross signals
        MutableDouble crossSignalDistanceTracker = new MutableDouble(-1);
        // Store the actual signal boundary and primary flag instead of just the ID
        MutableObject<Pair<SignalBoundary, Boolean>> trackingCrossSignal = new MutableObject<>(null);
        Map<UUID, Pair<SignalBoundary, Boolean>> chainedGroups = new HashMap<>();

        TravellingPoint.SteerDirection steerDirection = TravellingPoint.SteerDirection.NONE;
        if (Minecraft.getInstance().player != null) {
            // Check for A key (turn left)
            if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_A)) {
                steerDirection = TravellingPoint.SteerDirection.LEFT;
            }
            // Check for D key (turn right)
            else if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_D)) {
                steerDirection = TravellingPoint.SteerDirection.RIGHT;
            }
        }

        // Travel along the track
        scout.travel(train.graph, maxDistance,
                scout.steer(steerDirection,new Vec3(0,1,0)), // Always go straight
                (distance, couple) -> {
                    // Process signal points
                    Couple<TrackNode> nodes = couple.getSecond();
                    TrackEdgePoint bond = couple.getFirst();
                    if (!(bond instanceof SignalBoundary signal))
                        return false;
                    SignalBoundary boundary = train.graph.getPoint(EdgePointType.SIGNAL,bond.id);
                    // Skip non-signal points

                    if(train.speed> 0.2 && distance < 5){
                        return false;
                    }

                    UUID entering = signal.getGroup(nodes.getSecond());
                    SignalEdgeGroup signalEdgeGroup = Create.RAILWAYS.signalEdgeGroups.get(entering);
                    if (signalEdgeGroup == null)
                        return false;

                    boolean primary = entering.equals(signal.groups.getFirst());
                    boolean crossSignal = signal.types.get(primary) == SignalType.CROSS_SIGNAL;
                    boolean occupied = signal.isForcedRed(nodes.getSecond()) ||
                            signalEdgeGroup.isOccupiedUnless(train);

                    boolean crossSignalTracked = trackingCrossSignal.getValue() != null;

                    if (!crossSignalTracked) {
                        if (crossSignal) {
                            // Store the signal boundary object itself along with primary flag
                            trackingCrossSignal.setValue(Pair.of(signal, primary));
                            crossSignalDistanceTracker.setValue(distance);
                            chainedGroups.put(entering, Pair.of(signal, primary));
                        }
                        if (occupied) {
                            result.addSignal(signal, distance, primary, occupied, crossSignal, entering);
                            if (!crossSignal)
                                return true; // Stop at blocked entry signal
                        }
                    } else if (crossSignalTracked) {
                        chainedGroups.put(entering, Pair.of(signal, primary));
                        if (occupied) {
                            // Use the stored cross signal when adding to result
                            Pair<SignalBoundary, Boolean> crossSignalPair = trackingCrossSignal.getValue();
                            result.addSignal(crossSignalPair.getFirst(), crossSignalDistanceTracker.doubleValue(),
                                    crossSignalPair.getSecond(), occupied, true, entering);
                            if (!crossSignal)
                                return true;
                        }
                        if (!crossSignal) {
                            trackingCrossSignal.setValue(null);
                        }
                    }
                    return false;
                },
                (distance, edge) -> {
                    // Process junctions (no special handling needed - going straight)
                });

        return result;
    }

    public static class SignalScanResult {
        private List<SignalInfo> signals = new ArrayList<>();

        public void addSignal(SignalBoundary signal, double distance, boolean primary,
                              boolean occupied, boolean isCrossSignal, UUID groupId) {
            signals.add(new SignalInfo(signal.id, groupId, distance, primary, occupied, isCrossSignal));
        }

        public List<SignalInfo> getSignals() {
            return signals;
        }

        public SignalInfo getClosestOccupiedSignal() {
            return signals.stream()
                    .filter(SignalInfo::isOccupied)
                    .min(Comparator.comparing(SignalInfo::getDistance))
                    .orElse(null);
        }

        public boolean hasBlockedPath() {
            return getClosestOccupiedSignal() != null;
        }

        public double getDistanceToClosestOccupiedSignal() {
            SignalInfo signal = getClosestOccupiedSignal();
            return signal != null ? signal.getDistance() : Double.MAX_VALUE;
        }
    }

    public static class SignalInfo {
        private final UUID signalId;
        private final UUID groupId;
        private final double distance;
        private final boolean primary;
        private final boolean occupied;
        private final boolean isCrossSignal;

        public SignalInfo(UUID signalId, UUID groupId, double distance, boolean primary,
                          boolean occupied, boolean isCrossSignal) {
            this.signalId = signalId;
            this.groupId = groupId;
            this.distance = distance;
            this.primary = primary;
            this.occupied = occupied;
            this.isCrossSignal = isCrossSignal;
        }

        public UUID getSignalId() { return signalId; }
        public UUID getGroupId() { return groupId; }
        public double getDistance() { return distance; }
        public boolean isPrimary() { return primary; }
        public boolean isOccupied() { return occupied; }
        public boolean isCrossSignal() { return isCrossSignal; }
    }
}

