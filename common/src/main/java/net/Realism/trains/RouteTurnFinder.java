package net.Realism.trains;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds the next non-straight junction decision along a train's route guidance path.
 * <p>
 * Walks the track graph from the train's leading end (in the direction of travel), following
 * the guidance {@link RouteGuidance#path} at junctions exactly like the steering selector does.
 * The first decision where the route leaves straight is reported with its track distance.
 * Straight-through junctions are skipped silently. Runs server-side from the train tick; it
 * never throws (returns {@link TurnInfo#NONE} on any unexpected topology).
 */
public final class RouteTurnFinder {

    public enum TurnDir {
        NONE,
        LEFT,
        RIGHT;

        public byte toByte() {
            return (byte) ordinal();
        }

        public static TurnDir fromByte(byte b) {
            if (b < 0 || b >= values().length) return NONE;
            return values()[b];
        }
    }

    public record TurnInfo(TurnDir dir, double distance) {
        public static final TurnInfo NONE = new TurnInfo(TurnDir.NONE, -1);
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double MAX_SCAN = 2500;
    private static final double STRAIGHT_ANGLE_DEG = 8;
    private static final int MAX_STEPS = 300;
    /** How many upcoming turns to report (nearest first). */
    private static final int MAX_TURNS = 2;

    /** TEMP-DEBUG: reason for the last scan outcome, read by ETCS.update for throttled logging. */
    public static volatile String lastDebugReason = "n/a";
    /** TEMP-DEBUG: angle of the last classified decision in degrees. */
    public static volatile double lastDebugAngle = Double.NaN;

    private RouteTurnFinder() {}

    public static TurnInfo findNextTurn(Train train, RouteGuidance guidance, boolean backward) {
        List<TurnInfo> turns = findNextTurns(train, guidance, backward);
        return turns.isEmpty() ? TurnInfo.NONE : turns.get(0);
    }

    /**
     * Nearest-first list of upcoming turns (up to MAX_TURNS). Empty when none found.
     */
    public static List<TurnInfo> findNextTurns(Train train, RouteGuidance guidance, boolean backward) {
        List<TurnInfo> found = new ArrayList<>();
        try {
            if (train == null || train.graph == null || guidance == null
                    || guidance.path == null || guidance.path.isEmpty()) {
                lastDebugReason = "no-path";
                return found;
            }
            if (train.carriages == null || train.carriages.isEmpty()) {
                lastDebugReason = "no-carriages";
                return found;
            }

            TravellingPoint start = backward
                    ? train.carriages.get(train.carriages.size() - 1).getTrailingPoint()
                    : train.carriages.get(0).getLeadingPoint();
            if (start == null || start.edge == null || start.node1 == null || start.node2 == null) {
                lastDebugReason = "no-start-edge";
                return found;
            }

            boolean forward = !backward;
            TrackGraph graph = train.graph;
            TrackEdge incoming = start.edge;
            TrackNode prev = forward ? start.node1 : start.node2;
            TrackNode at = forward ? start.node2 : start.node1;
            double edgeLen = incoming.getLength();
            double dist = forward ? edgeLen - start.position : start.position;
            if (dist < 0 || Double.isNaN(dist)) dist = 0;

            int pathIdx = 0;
            int steps = 0;
            while (dist < MAX_SCAN && steps++ < MAX_STEPS) {
                List<Map.Entry<TrackNode, TrackEdge>> options = viableOptions(graph, incoming, at, prev, forward);
                if (options.size() > 1) {
                    // The route's leaving edge here is the first path entry that touches this
                    // junction without touching where we came from. Entries behind the train
                    // (path goes stale between recomputes, and is never consumed in manual
                    // mode) and the arriving edge touch prev, so they are skipped.
                    TrackEdge chosen = null;
                    while (pathIdx < guidance.path.size()) {
                        Couple<TrackNode> couple = guidance.path.get(pathIdx++);
                        boolean touchesAt = couple.getFirst() == at || couple.getSecond() == at;
                        boolean touchesPrev = couple.getFirst() == prev || couple.getSecond() == prev;
                        if (!touchesAt || touchesPrev) continue;
                        TrackEdge routeEdge = null;
                        try {
                            routeEdge = graph.getConnection(couple);
                        } catch (Throwable ignored) {
                        }
                        if (routeEdge == null) continue;
                        for (Map.Entry<TrackNode, TrackEdge> option : options) {
                            if (option.getValue() == routeEdge) {
                                chosen = routeEdge;
                                break;
                            }
                        }
                        break;
                    }
                    if (chosen == null) {
                        lastDebugReason = "diverged@pathIdx" + pathIdx;
                        return found;
                    }

                    TurnDir dir = classify(incoming, prev, at, chosen);
                    if (dir != null) {
                        lastDebugReason = "found";
                        found.add(new TurnInfo(dir, dist));
                        if (found.size() >= MAX_TURNS) return found;
                    } else {
                        lastDebugReason = "straight-skipped";
                    }

                    TrackNode next = chosen.node1 == at ? chosen.node2 : chosen.node1;
                    dist += chosen.getLength();
                    incoming = chosen;
                    prev = at;
                    at = next;
                } else if (options.size() == 1) {
                    TrackEdge nextEdge = options.get(0).getValue();
                    TrackNode next = options.get(0).getKey();
                    dist += nextEdge.getLength();
                    incoming = nextEdge;
                    prev = at;
                    at = next;
                } else {
                    lastDebugReason = "dead-end@dist" + (int) dist;
                    return found;
                }
            }
            if (found.isEmpty()) lastDebugReason = "scan-capped";
            return found;
        } catch (Throwable ignored) {
            lastDebugReason = "exception";
            return found;
        }
    }

    /** Outgoing edges at a junction, excluding the way we came, mirroring TravellingPoint.travel. */
    private static List<Map.Entry<TrackNode, TrackEdge>> viableOptions(TrackGraph graph, TrackEdge incoming,
                                                                        TrackNode at, TrackNode prev, boolean forward) {
        List<Map.Entry<TrackNode, TrackEdge>> viable = new ArrayList<>();
        Map<TrackNode, TrackEdge> connections;
        try {
            connections = graph.getConnectionsFrom(at);
        } catch (Throwable ignored) {
            return viable;
        }
        if (connections == null) return viable;
        for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            if (entry.getKey() == prev) continue;
            try {
                if (forward) {
                    if (!incoming.canTravelTo(entry.getValue())) continue;
                } else {
                    TrackEdge reverse = graph.getConnectionsFrom(entry.getKey()).get(at);
                    if (reverse == null || !reverse.canTravelTo(incoming)) continue;
                }
            } catch (Throwable ignored) {
                continue;
            }
            viable.add(entry);
        }
        return viable;
    }

    /**
     * Classifies the route's choice as LEFT/RIGHT, or null when straight-through.
     * Sign convention matches TravellingPoint.steer (LEFT has negative dot).
     */
    private static TurnDir classify(TrackEdge incoming, TrackNode from, TrackNode at, TrackEdge chosen) {
        try {
            TrackNode next = chosen.node1 == at ? chosen.node2 : chosen.node1;
            Vec3 inVec = orientedIncoming(incoming, from, at);
            Vec3 outVec = orientedOutgoing(chosen, at, next);
            if (inVec.lengthSqr() < 1e-6 || outVec.lengthSqr() < 1e-6) return null;
            Vec3 in = inVec.normalize();
            Vec3 out = outVec.normalize();
            double dot = Math.max(-1, Math.min(1, in.dot(out)));
            double angleDeg = Math.acos(dot) * 180.0 / Math.PI;
            lastDebugAngle = angleDeg;
            if (angleDeg < STRAIGHT_ANGLE_DEG) return null;
            double side = in.cross(UP).dot(out);
            if (Math.abs(side) < 1e-6) return null;
            return side < 0 ? TurnDir.LEFT : TurnDir.RIGHT;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Incoming tangent at the destination node, oriented along travel from->to. */
    private static Vec3 orientedIncoming(TrackEdge edge, TrackNode from, TrackNode to) {
        if (edge.node1 == from && edge.node2 == to) return edge.getDirection(false);
        if (edge.node2 == from && edge.node1 == to) return edge.getDirection(true).scale(-1);
        try {
            Vec3 a = from.getLocation().getLocation();
            Vec3 b = to.getLocation().getLocation();
            return b.subtract(a);
        } catch (Throwable ignored) {
            return edge.getDirection(true);
        }
    }

    /** Outgoing tangent at the source node, oriented along travel from->to. */
    private static Vec3 orientedOutgoing(TrackEdge edge, TrackNode from, TrackNode to) {
        if (edge.node1 == from && edge.node2 == to) return edge.getDirection(true);
        if (edge.node2 == from && edge.node1 == to) return edge.getDirection(false).scale(-1);
        try {
            Vec3 a = from.getLocation().getLocation();
            Vec3 b = to.getLocation().getLocation();
            return b.subtract(a);
        } catch (Throwable ignored) {
            return edge.getDirection(true);
        }
    }
}
