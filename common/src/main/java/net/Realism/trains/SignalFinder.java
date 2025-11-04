package net.Realism.trains;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import net.Realism.compat.TramwaysCompat;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
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
        if (train.manualSteer != null) {
            steerDirection = train.manualSteer;
        }

        // Travel along the track
         double distanceTraveled = scout.travel(train.graph, maxDistance,
                scout.steer(steerDirection, new Vec3(0, 1, 0)), // Always go straight
                (distance, couple) -> {
                    // Process signal points
                    Couple<TrackNode> nodes = couple.getSecond();
                    TrackEdgePoint bond = couple.getFirst();

                    if (TramwaysCompat.isTramSignPoint(bond)) {
                        // Determine if we're approaching from the primary side using compat method
                        boolean primary = TramwaysCompat.isPrimary(bond, nodes.getSecond());
                        // Pass this information when adding the tram sign
                        result.addTramSign(bond, distance, primary);
                        return false; // Continue scanning, don't stop at tram signs
                    }

                    // Handle SignalBoundary
                    if (!(bond instanceof SignalBoundary signal))
                        return false;

                    // Skip non-signal points

                    if (train.speed > 0.2 && distance < 5) {
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
                            return !crossSignal; // Stop at blocked entry signal
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
        double actualMaxDistance = Math.abs(maxDistance);
        if (Math.abs(distanceTraveled) < actualMaxDistance - 0.1){
            // Track ended before maxDistance - add artificial red signal at track end
            result.endOfTrack(Math.abs(distanceTraveled));
        }

        return result;
    }

    public static class SignalScanResult {
        private final List<SignalInfo> signals = new ArrayList<>();
        private final List<TramSignInfo> tramSigns = new ArrayList<>();

        public void addSignal(SignalBoundary signal, double distance, boolean primary,
                              boolean occupied, boolean isCrossSignal, UUID groupId) {
            signals.add(new SignalInfo(signal.id, groupId, distance, primary, occupied, isCrossSignal));
        }

        public void addTramSign(Object tramSign, double distance, boolean primary) {
            if (!TramwaysCompat.isLoaded()) return;

            Object tramSignInfo = TramwaysCompat.createTramSignInfo(
                    (UUID)getFieldValue(tramSign, "id"), distance, tramSign, primary);
            if (tramSignInfo != null) {
                tramSigns.add((TramSignInfo)tramSignInfo);
            }
        }

        private Object getFieldValue(Object obj, String fieldName) {
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception e) {
                return null;
            }
        }

        public List<SignalInfo> getSignals() {
            return signals;
        }

        public List<TramSignInfo> getTramSigns() {
            return tramSigns;
        }

        public TramSignInfo getClosestTramSign() {
            return tramSigns.stream()
                    .min(Comparator.comparing(TramSignInfo::getDistance))
                    .orElse(null);
        }

        public SignalInfo getClosestOccupiedSignal() {
            return signals.stream()
                    .filter(SignalInfo::occupied)
                    .min(Comparator.comparing(SignalInfo::distance))
                    .orElse(null);
        }

        public boolean hasBlockedPath() {
            return getClosestOccupiedSignal() != null;
        }

        public double getDistanceToClosestOccupiedSignal() {
            SignalInfo signal = getClosestOccupiedSignal();
            return signal != null ? signal.distance() : Double.MAX_VALUE;
        }

        public void endOfTrack(double distance) {
            signals.add(new SignalInfo(null, null, distance, true, true, false));
        }
    }

    public record SignalInfo(UUID signalId, UUID groupId, double distance, boolean primary, boolean occupied,
                             boolean isCrossSignal) {
    }

    public static class TramSignInfo {
        private final UUID signId;
        private final double distance;
        private final Object signType; // Changed from TramSignPoint
        private final boolean primary;

        public TramSignInfo(UUID signId, double distance, Object signType, boolean primary) {
            this.signId = signId;
            this.distance = distance;
            this.signType = signType;
            this.primary = primary;
        }

        public Object getSign() { // Changed return type
            return signType;
        }

        public UUID getSignId() {
            return signId;
        }

        public double getDistance() {
            return distance;
        }

        public boolean getPrimary() {
            return primary;
        }
    }
}
