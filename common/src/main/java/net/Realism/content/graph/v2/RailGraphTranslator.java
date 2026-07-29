package net.Realism.content.graph.v2;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackEdgeIntersection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.compat.TramwaysCompat;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collapses a Create {@code TrackGraph} into a {@link RailGraph}: junctions,
 * dead ends, portal endpoints and signal boundaries become nodes; the
 * degree-2 track chains between them become directed edge pairs carrying
 * length, curvature speed caps, stations, diamond crossings and Tramways
 * signs. Iteration order is fully deterministic (sorted node locations), so
 * identical track networks translate to identical graphs.
 *
 * <p>Must run on the server thread (reads Create's live graph).
 */
public class RailGraphTranslator {

    /** {@code graph == null} means the network exceeded the node cap. */
    public record Result(RailGraph graph, int microNodeCount) {}

    private static final Comparator<TrackNodeLocation> LOCATION_ORDER = Comparator
            .comparing((TrackNodeLocation l) -> l.dimension == null ? "" : l.dimension.location().toString())
            .thenComparingInt(Vec3i::getX)
            .thenComparingInt(Vec3i::getY)
            .thenComparingInt(Vec3i::getZ)
            .thenComparingInt(l -> l.yOffsetPixels);

    /** A signal boundary encountered along a walk, splitting it in two. */
    private static class Cut {
        double dist;
        SignalBoundary signal;
        Vec3 position;
        boolean governsForward;
        boolean governsBackward;
        SignalType typeForward;
        SignalType typeBackward;
    }

    private record StationPoint(double dist, GlobalStation station, boolean approachableForward) {}

    private record CrossingPoint(double dist, java.util.UUID id, Vec3 position) {}

    private record SignPoint(double dist, Vec3 position, double capKmh, boolean forward) {}

    private record ShapePoint(double dist, Vec3 position) {}

    /** One collapsed degree-2 chain between two boundary nodes. */
    private static class Walk {
        TrackNode start;
        TrackNode end;
        double length;
        boolean interDimensional;
        final List<Cut> cuts = new ArrayList<>();
        final List<StationPoint> stations = new ArrayList<>();
        final List<CrossingPoint> crossings = new ArrayList<>();
        final List<SignPoint> signs = new ArrayList<>();
        final List<ShapePoint> shape = new ArrayList<>();
        /** Per micro-segment [startDist, endDist, capKmh] for curvature caps. */
        final List<double[]> segmentCaps = new ArrayList<>();
    }

    private final TrackGraph source;
    private final Map<TrackNode, List<TrackNode>> adjacency = new LinkedHashMap<>();
    private final Set<TrackNode> boundary = new HashSet<>();
    private final Set<Long> visited = new HashSet<>();
    private final Map<TrackNode, Integer> boundaryNodeIds = new IdentityHashMap<>();
    private final Map<SignalBoundary, Integer> signalNodeIds = new IdentityHashMap<>();
    private final Map<String, Integer> dimensionIndices = new LinkedHashMap<>();
    private RailGraph result;
    private final double maxTrainSpeedKmh;

    private RailGraphTranslator(TrackGraph source) {
        this.source = source;
        this.maxTrainSpeedKmh = Math.max(
                AllConfigs.server().trains.trainTopSpeed.getF(),
                AllConfigs.server().trains.poweredTrainTopSpeed.getF()) * 3.6;
    }

    public static Result translate(TrackGraph graph, int nodeCap) {
        return new RailGraphTranslator(graph).run(nodeCap);
    }

    private Result run(int nodeCap) {
        List<TrackNodeLocation> sortedLocations = new ArrayList<>(source.getNodes());
        sortedLocations.sort(LOCATION_ORDER);
        if (sortedLocations.size() > nodeCap)
            return new Result(null, sortedLocations.size());

        for (TrackNodeLocation location : sortedLocations) {
            TrackNode node = source.locateNode(location);
            if (node == null)
                continue;
            List<TrackNode> neighbors = new ArrayList<>(source.getConnectionsFrom(node).keySet());
            neighbors.sort(Comparator.comparing(TrackNode::getLocation, LOCATION_ORDER));
            adjacency.put(node, neighbors);
        }

        for (Map.Entry<TrackNode, List<TrackNode>> entry : adjacency.entrySet()) {
            TrackNode node = entry.getKey();
            if (entry.getValue().size() != 2) {
                boundary.add(node);
                continue;
            }
            for (TrackNode neighbor : entry.getValue()) {
                TrackEdge edge = source.getConnectionsFrom(node).get(neighbor);
                if (edge != null && edge.isInterDimensional()) {
                    boundary.add(node);
                    break;
                }
            }
        }

        result = new RailGraph(source.id, source.getChecksum());

        List<Walk> walks = new ArrayList<>();
        for (Map.Entry<TrackNode, List<TrackNode>> entry : adjacency.entrySet()) {
            if (!boundary.contains(entry.getKey()))
                continue;
            for (TrackNode neighbor : entry.getValue()) {
                if (visited.contains(segmentKey(entry.getKey(), neighbor)))
                    continue;
                walks.add(walk(entry.getKey(), neighbor));
            }
        }
        // Pure loops with no junction/signal-free boundary anywhere: anchor at
        // the lowest-sorted node so they still appear in the graph.
        for (Map.Entry<TrackNode, List<TrackNode>> entry : adjacency.entrySet()) {
            if (boundary.contains(entry.getKey()))
                continue;
            for (TrackNode neighbor : entry.getValue()) {
                if (visited.contains(segmentKey(entry.getKey(), neighbor)))
                    continue;
                walks.add(walk(entry.getKey(), neighbor));
            }
        }

        for (Walk walk : walks)
            emit(walk);

        result.linkNodes();
        return new Result(result, sortedLocations.size());
    }

    private static long segmentKey(TrackNode from, TrackNode to) {
        return ((long) from.getNetId() << 32) | (to.getNetId() & 0xFFFFFFFFL);
    }

    private Walk walk(TrackNode start, TrackNode first) {
        Walk walk = new Walk();
        walk.start = start;
        TrackNode previous = start;
        TrackNode current = first;
        double dist = 0;

        while (true) {
            TrackEdge edge = source.getConnectionsFrom(previous).get(current);
            visited.add(segmentKey(previous, current));
            visited.add(segmentKey(current, previous));
            if (edge == null)
                break;

            double edgeLength = edge.getLength();
            collectSegment(walk, previous, current, edge, dist);
            dist += edgeLength;
            if (edge.isInterDimensional())
                walk.interDimensional = true;

            if (boundary.contains(current) || current == start)
                break;
            List<TrackNode> neighbors = adjacency.get(current);
            if (neighbors == null || neighbors.size() != 2)
                break;
            TrackNode next = neighbors.get(0) == previous ? neighbors.get(1) : neighbors.get(0);
            previous = current;
            current = next;
        }

        walk.end = current;
        walk.length = dist;
        walk.shape.add(new ShapePoint(dist, nodePosition(current)));
        walk.cuts.sort(Comparator.comparingDouble(c -> c.dist));
        return walk;
    }

    private void collectSegment(Walk walk, TrackNode from, TrackNode to, TrackEdge edge, double startDist) {
        double edgeLength = edge.getLength();
        walk.shape.add(new ShapePoint(startDist, nodePosition(from)));
        if (edge.isTurn() && edgeLength > 2) {
            int samples = Math.max(2, Math.min(8, (int) (edgeLength / 4)));
            for (int i = 1; i < samples; i++) {
                double t = (double) i / samples;
                walk.shape.add(new ShapePoint(startDist + t * edgeLength, edge.getPosition(null, t)));
            }
        }

        double cap = edge.isTurn() ? speedForRadius(minCurvatureRadius(edge)) : 0;
        walk.segmentCaps.add(new double[] { startDist, startDist + edgeLength, cap });

        for (TrackEdgePoint point : edge.getEdgeData().getPoints()) {
            double locOn = point.getLocationOn(edge);
            double dist = startDist + locOn;
            if (point instanceof SignalBoundary signal) {
                Cut cut = new Cut();
                cut.dist = dist;
                cut.signal = signal;
                cut.position = positionOnSegment(edge, locOn);
                boolean towardSecond = signal.isPrimary(to);
                cut.governsForward = !signal.blockEntities.get(towardSecond).isEmpty();
                cut.typeForward = signal.types.get(towardSecond);
                boolean towardFirst = signal.isPrimary(from);
                cut.governsBackward = !signal.blockEntities.get(towardFirst).isEmpty();
                cut.typeBackward = signal.types.get(towardFirst);
                walk.cuts.add(cut);
            } else if (point instanceof GlobalStation station) {
                walk.stations.add(new StationPoint(dist, station, station.isPrimary(to)));
            } else if (TramwaysCompat.isTramSignPoint(point)) {
                double forwardCap = TramwaysCompat.getSignCapKmh(point, to, maxTrainSpeedKmh);
                if (forwardCap > 0)
                    walk.signs.add(new SignPoint(dist, positionOnSegment(edge, locOn), forwardCap, true));
                double backwardCap = TramwaysCompat.getSignCapKmh(point, from, maxTrainSpeedKmh);
                if (backwardCap > 0)
                    walk.signs.add(new SignPoint(dist, positionOnSegment(edge, locOn), backwardCap, false));
            }
        }

        for (TrackEdgeIntersection intersection : edge.getEdgeData().getIntersections()) {
            double dist = startDist + intersection.location;
            walk.crossings.add(new CrossingPoint(dist, intersection.id,
                    positionOnSegment(edge, intersection.location)));
        }
    }

    private void emit(Walk walk) {
        if (walk.shape.size() < 2)
            return;
        List<Object> anchors = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        anchors.add(walk.start);
        distances.add(0d);
        for (Cut cut : walk.cuts) {
            anchors.add(cut);
            distances.add(cut.dist);
        }
        anchors.add(walk.end);
        distances.add(walk.length);

        for (int i = 0; i < anchors.size() - 1; i++) {
            double a = distances.get(i);
            double b = distances.get(i + 1);
            int fromId = nodeId(anchors.get(i));
            int toId = nodeId(anchors.get(i + 1));

            SignalKind entryForward = SignalKind.NONE;
            if (anchors.get(i) instanceof Cut cut && cut.governsForward)
                entryForward = toKind(cut.typeForward);
            SignalKind entryBackward = SignalKind.NONE;
            if (anchors.get(i + 1) instanceof Cut cut && cut.governsBackward)
                entryBackward = toKind(cut.typeBackward);

            double cap = intervalCap(walk, a, b);
            double forwardSignCap = directionalSignCap(walk, a, b, true);
            double backwardSignCap = directionalSignCap(walk, a, b, false);

            RailEdge forward = new RailEdge(result.edges.size(), fromId, toId, b - a,
                    combineCaps(cap, forwardSignCap), entryForward, walk.interDimensional);
            result.edges.add(forward);
            RailEdge backward = new RailEdge(result.edges.size(), toId, fromId, b - a,
                    combineCaps(cap, backwardSignCap), entryBackward, walk.interDimensional);
            result.edges.add(backward);
            forward.oppositeId = backward.id;
            backward.oppositeId = forward.id;

            for (StationPoint station : walk.stations) {
                if (station.dist() < a || station.dist() > b)
                    continue;
                forward.stations.add(new RailStation(station.station().id, stationName(station.station()),
                        station.dist() - a, station.approachableForward()));
                backward.stations.add(new RailStation(station.station().id, stationName(station.station()),
                        b - station.dist(), !station.approachableForward()));
            }
            forward.stations.sort(Comparator.comparingDouble(RailStation::offset));
            backward.stations.sort(Comparator.comparingDouble(RailStation::offset));

            for (CrossingPoint crossing : walk.crossings) {
                if (crossing.dist() < a || crossing.dist() > b)
                    continue;
                forward.crossings.add(new RailCrossing(crossing.id(), crossing.dist() - a, crossing.position()));
                backward.crossings.add(new RailCrossing(crossing.id(), b - crossing.dist(), crossing.position()));
            }
            forward.crossings.sort(Comparator.comparingDouble(RailCrossing::offset));
            backward.crossings.sort(Comparator.comparingDouble(RailCrossing::offset));

            for (SignPoint sign : walk.signs) {
                if (sign.dist() < a || sign.dist() > b)
                    continue;
                if (sign.forward())
                    forward.signs.add(new RailSign(sign.dist() - a, sign.position(), sign.capKmh()));
                else
                    backward.signs.add(new RailSign(b - sign.dist(), sign.position(), sign.capKmh()));
            }
            forward.signs.sort(Comparator.comparingDouble(RailSign::offset));
            backward.signs.sort(Comparator.comparingDouble(RailSign::offset));

            for (double[] segment : walk.segmentCaps) {
                if (segment[2] <= 0 || segment[1] <= a || segment[0] >= b)
                    continue;
                double start = Math.max(segment[0], a);
                double end = Math.min(segment[1], b);
                forward.capProfile.add(new double[] { start - a, end - a, segment[2] });
                backward.capProfile.add(new double[] { b - end, b - start, segment[2] });
            }
            forward.capProfile.sort(Comparator.comparingDouble(range -> range[0]));
            backward.capProfile.sort(Comparator.comparingDouble(range -> range[0]));

            forward.shape.add(result.node(fromId).position);
            for (ShapePoint point : walk.shape)
                if (point.dist() > a + 0.01 && point.dist() < b - 0.01)
                    forward.shape.add(point.position());
            forward.shape.add(result.node(toId).position);
            for (int j = forward.shape.size() - 1; j >= 0; j--)
                backward.shape.add(forward.shape.get(j));
        }
    }

    /** Min curvature cap over micro segments overlapping [a, b]; 0 = uncapped. */
    private double intervalCap(Walk walk, double a, double b) {
        double cap = 0;
        for (double[] segment : walk.segmentCaps) {
            if (segment[1] <= a || segment[0] >= b || segment[2] <= 0)
                continue;
            cap = cap == 0 ? segment[2] : Math.min(cap, segment[2]);
        }
        return cap;
    }

    /** Min Tramways sign cap applying to travel through [a, b] in a direction. */
    private double directionalSignCap(Walk walk, double a, double b, boolean forward) {
        double cap = 0;
        for (SignPoint sign : walk.signs) {
            if (sign.forward() != forward || sign.dist() < a || sign.dist() > b)
                continue;
            cap = cap == 0 ? sign.capKmh() : Math.min(cap, sign.capKmh());
        }
        return cap;
    }

    private static double combineCaps(double a, double b) {
        if (a <= 0)
            return b;
        if (b <= 0)
            return a;
        return Math.min(a, b);
    }

    private int nodeId(Object anchor) {
        if (anchor instanceof TrackNode node) {
            Integer existing = boundaryNodeIds.get(node);
            if (existing != null)
                return existing;
            RailNode.NodeType type = nodeType(node);
            RailNode railNode = new RailNode(result.nodes.size(), type,
                    dimensionIndex(node.getLocation()), nodePosition(node));
            result.nodes.add(railNode);
            boundaryNodeIds.put(node, railNode.id);
            return railNode.id;
        }
        Cut cut = (Cut) anchor;
        Integer existing = signalNodeIds.get(cut.signal);
        if (existing != null)
            return existing;
        TrackNodeLocation location = cut.signal.edgeLocation.getFirst();
        RailNode railNode = new RailNode(result.nodes.size(), RailNode.NodeType.SIGNAL,
                dimensionIndex(location), cut.position);
        result.nodes.add(railNode);
        signalNodeIds.put(cut.signal, railNode.id);
        return railNode.id;
    }

    private RailNode.NodeType nodeType(TrackNode node) {
        List<TrackNode> neighbors = adjacency.get(node);
        int degree = neighbors == null ? 0 : neighbors.size();
        if (degree <= 1)
            return RailNode.NodeType.DEAD_END;
        if (neighbors != null)
            for (TrackNode neighbor : neighbors) {
                TrackEdge edge = source.getConnectionsFrom(node).get(neighbor);
                if (edge != null && edge.isInterDimensional())
                    return RailNode.NodeType.PORTAL;
            }
        return RailNode.NodeType.JUNCTION;
    }

    private int dimensionIndex(TrackNodeLocation location) {
        String key = location.dimension == null ? "unknown" : location.dimension.location().toString();
        return dimensionIndices.computeIfAbsent(key, k -> {
            result.dimensions.add(k);
            return result.dimensions.size() - 1;
        });
    }

    private static Vec3 nodePosition(TrackNode node) {
        return node.getLocation().getLocation();
    }

    private static String stationName(GlobalStation station) {
        return station.name == null ? "" : station.name;
    }

    private static Vec3 positionOnSegment(TrackEdge edge, double locOn) {
        double length = edge.getLength();
        if (length <= 0)
            return edge.node1.getLocation().getLocation();
        return edge.getPosition(null, Math.max(0, Math.min(1, locOn / length)));
    }

    private static SignalKind toKind(SignalType type) {
        return type == SignalType.ENTRY_SIGNAL ? SignalKind.ENTRY : SignalKind.CHAIN;
    }

    /**
     * Realism's track speed curve (same formula as the track placement
     * overlay): km/h from curve radius in blocks; 0 = no limit.
     */
    public static double speedForRadius(double radius) {
        if (radius <= 0 || radius >= 5000)
            return 0;
        return radius < 160 ? Math.pow(radius, 0.75) * 6.6 : Math.sqrt(radius) * 23.5;
    }

    /**
     * Min circumradius of the horizontal projection of the curve, sampled
     * every few blocks. Vertical easing on slopes deliberately ignored.
     */
    private static double minCurvatureRadius(TrackEdge edge) {
        double length = edge.getLength();
        if (length < 2)
            return 0;
        int steps = Math.max(4, Math.min(24, (int) (length / 2)));
        Vec3[] points = new Vec3[steps + 1];
        for (int i = 0; i <= steps; i++)
            points[i] = edge.getPosition(null, (double) i / steps);
        double min = Double.MAX_VALUE;
        for (int i = 1; i < steps; i++) {
            double radius = circumradiusXZ(points[i - 1], points[i], points[i + 1]);
            if (radius > 0)
                min = Math.min(min, radius);
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    private static double circumradiusXZ(Vec3 a, Vec3 b, Vec3 c) {
        double abx = b.x - a.x, abz = b.z - a.z;
        double bcx = c.x - b.x, bcz = c.z - b.z;
        double cax = a.x - c.x, caz = a.z - c.z;
        double cross = abx * (c.z - a.z) - abz * (c.x - a.x);
        double area2 = Math.abs(cross);
        if (area2 < 1e-4)
            return 0;
        double ab = Math.hypot(abx, abz);
        double bc = Math.hypot(bcx, bcz);
        double ca = Math.hypot(cax, caz);
        return (ab * bc * ca) / (2 * area2);
    }
}
