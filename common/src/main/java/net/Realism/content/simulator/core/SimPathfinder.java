package net.Realism.content.simulator.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Deterministic Dijkstra over directed edges: turn-legal transitions only, no
 * U-turns, ordered by distance + penalty. Penalties mirror Create's
 * {@code Navigation}/{@code Train.Penalties}: parked and waiting trains make
 * their edges expensive and occupied signal entries add a red-signal weight,
 * so routes divert around blocked track exactly like real trains do.
 *
 * <p>Once a candidate route exists, branches whose cost so far plus a
 * Euclidean lower bound to the nearest target provably can't beat it are
 * pruned. Expansion order and tie-breaking stay pure-Dijkstra, and equal-cost
 * candidates never replace the incumbent, so results are identical to the
 * unpruned search — it's only faster.
 */
public class SimPathfinder {

    /** Create's {@code Train.Penalties.RED_SIGNAL}. */
    private static final double RED_SIGNAL_PENALTY = 25;

    /** Engine-supplied costs beyond distance. */
    public interface Penalties {
        Penalties NONE = new Penalties() {
            @Override
            public double edgeBase(int edgeId) {
                return 0;
            }

            @Override
            public boolean redSignalEntry(int edgeId) {
                return false;
            }
        };

        /** Flat extra cost of traversing this edge (idle trains, stations...). */
        double edgeBase(int edgeId);

        /** True when this edge's governed entry currently reads red. */
        boolean redSignalEntry(int edgeId);
    }

    /** A found route. {@code edges[0]} is the edge the head starts on. */
    public record Path(int[] edges, double distance, SimGraph.StationTarget target, boolean reversed) {}

    /**
     * Best path from the head position (or, when allowed and cheaper, from
     * the turned-around tail) to any of the targets. Ties break toward the
     * forward direction, then by station name and id.
     */
    public static Path find(SimGraph graph, int headEdge, double headOffset,
                            List<SimGraph.StationTarget> targets, boolean allowReverse,
                            int reverseEdge, double reverseOffset, Penalties penalties) {
        Found forward = search(graph, headEdge, headOffset, targets, false, penalties,
                Double.POSITIVE_INFINITY);
        if (!allowReverse || reverseEdge < 0)
            return forward == null ? null : forward.path();
        // The forward result is an incumbent the reverse search must strictly
        // beat, so it can prune against it from the first relaxation.
        Found reverse = search(graph, reverseEdge, reverseOffset, targets, true, penalties,
                forward == null ? Double.POSITIVE_INFINITY : forward.cost());
        if (forward == null)
            return reverse == null ? null : reverse.path();
        if (reverse == null || forward.cost() <= reverse.cost())
            return forward.path();
        return reverse.path();
    }

    private record Found(Path path, double cost) {}

    private static Found search(SimGraph graph, int startEdge, double startOffset,
                                List<SimGraph.StationTarget> targets, boolean reversed,
                                Penalties penalties, double initialBound) {
        int edgeCount = graph.edges.size();
        double[] cost = new double[edgeCount];
        double[] distance = new double[edgeCount];
        java.util.Arrays.fill(cost, Double.POSITIVE_INFINITY);
        int[] predecessor = new int[edgeCount];
        java.util.Arrays.fill(predecessor, -1);

        List<SimGraph.StationTarget> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparing(SimGraph.StationTarget::name)
                .thenComparing(t -> t.stationId().toString()));

        SimGraph.StationTarget bestTarget = null;
        double bestCost = initialBound;
        double bestDistance = 0;
        boolean bestIsDirect = false;
        // Targets ahead on the start edge itself need no expansion.
        for (SimGraph.StationTarget target : sorted) {
            if (target.edgeId() != startEdge || target.offset() < startOffset - 1e-6)
                continue;
            double directCost = target.offset() - startOffset;
            if (directCost < bestCost) {
                bestCost = directCost;
                bestDistance = directCost;
                bestTarget = target;
                bestIsDirect = true;
            }
        }

        // Euclidean lower bounds toward target entry nodes (minus the target
        // edge's length, since the platform sits somewhere along it). Only
        // valid geometry-free of dimension hops; disabled for huge target
        // fans where the min itself would dominate.
        boolean useBound = !graph.hasInterDimensional() && sorted.size() <= 32;
        double[] boundX = null, boundY = null, boundZ = null, boundSlack = null;
        if (useBound) {
            boundX = new double[sorted.size()];
            boundY = new double[sorted.size()];
            boundZ = new double[sorted.size()];
            boundSlack = new double[sorted.size()];
            for (int t = 0; t < sorted.size(); t++) {
                SimEdge targetEdge = graph.edge(sorted.get(t).edgeId());
                SimVec pos = graph.nodes.get(targetEdge.from).position();
                boundX[t] = pos.x();
                boundY[t] = pos.y();
                boundZ[t] = pos.z();
                boundSlack[t] = targetEdge.length;
            }
        }

        record QueueEntry(double cost, int edge) {}
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::cost).thenComparingInt(QueueEntry::edge));
        SimEdge start = graph.edge(startEdge);
        double toStartEnd = start.length - startOffset;
        for (int nextId : start.nextEdges) {
            double entryCost = toStartEnd + enterCost(graph, nextId, penalties);
            if (entryCost < cost[nextId]
                    && !(useBound && bestCost < Double.POSITIVE_INFINITY && entryCost
                            + lowerBound(graph, nextId, boundX, boundY, boundZ, boundSlack) >= bestCost)) {
                cost[nextId] = entryCost;
                distance[nextId] = toStartEnd;
                predecessor[nextId] = startEdge;
                queue.add(new QueueEntry(entryCost, nextId));
            }
        }

        boolean[] settled = new boolean[edgeCount];
        while (!queue.isEmpty()) {
            QueueEntry entry = queue.poll();
            if (settled[entry.edge()] || entry.cost() > cost[entry.edge()] + 1e-9)
                continue;
            settled[entry.edge()] = true;
            if (entry.cost() >= bestCost)
                break;

            SimEdge edge = graph.edge(entry.edge());
            for (SimGraph.StationTarget target : sorted) {
                if (target.edgeId() != entry.edge())
                    continue;
                double targetCost = entry.cost() + target.offset();
                if (targetCost < bestCost - 1e-9) {
                    bestCost = targetCost;
                    bestDistance = distance[entry.edge()] + target.offset();
                    bestTarget = target;
                    bestIsDirect = false;
                }
            }
            double throughCost = entry.cost() + edge.length;
            double throughDistance = distance[entry.edge()] + edge.length;
            for (int nextId : edge.nextEdges) {
                double entryCost = throughCost + enterCost(graph, nextId, penalties);
                if (entryCost < cost[nextId] - 1e-9
                        && !(useBound && bestCost < Double.POSITIVE_INFINITY && entryCost
                                + lowerBound(graph, nextId, boundX, boundY, boundZ, boundSlack) >= bestCost)) {
                    cost[nextId] = entryCost;
                    distance[nextId] = throughDistance;
                    predecessor[nextId] = entry.edge();
                    queue.add(new QueueEntry(entryCost, nextId));
                }
            }
        }

        if (bestTarget == null)
            return null;
        if (bestIsDirect)
            return new Found(new Path(new int[] { startEdge }, bestDistance, bestTarget, reversed), bestCost);

        // Predecessor chains always terminate at startEdge (every initial
        // relaxation used it as predecessor). The target edge may itself be
        // startEdge again — a legitimate loop route.
        List<Integer> routeReversed = new ArrayList<>();
        int cursor = bestTarget.edgeId();
        routeReversed.add(cursor);
        cursor = predecessor[cursor];
        while (cursor != startEdge) {
            routeReversed.add(cursor);
            cursor = predecessor[cursor];
        }
        int[] route = new int[routeReversed.size() + 1];
        route[0] = startEdge;
        for (int i = 0; i < routeReversed.size(); i++)
            route[i + 1] = routeReversed.get(routeReversed.size() - 1 - i);
        return new Found(new Path(route, bestDistance, bestTarget, reversed), bestCost);
    }

    /**
     * Smallest possible remaining cost from this edge's entry node to any
     * target: straight-line distance to the target edge's entry node, minus
     * that edge's length (the platform may sit at its far end). Admissible —
     * real routes are never shorter than the straight line, and penalties
     * only add cost.
     */
    private static double lowerBound(SimGraph graph, int edgeId, double[] boundX,
                                     double[] boundY, double[] boundZ, double[] boundSlack) {
        SimVec pos = graph.nodes.get(graph.edge(edgeId).from).position();
        double best = Double.POSITIVE_INFINITY;
        for (int t = 0; t < boundX.length; t++) {
            double dx = boundX[t] - pos.x();
            double dy = boundY[t] - pos.y();
            double dz = boundZ[t] - pos.z();
            double bound = Math.sqrt(dx * dx + dy * dy + dz * dz) - boundSlack[t];
            if (bound < best)
                best = bound;
        }
        return Math.max(0, best);
    }

    private static double enterCost(SimGraph graph, int edgeId, Penalties penalties) {
        double extra = penalties.edgeBase(edgeId);
        SimEdge edge = graph.edge(edgeId);
        if (edge.entrySignal != SimEdge.Signal.NONE && penalties.redSignalEntry(edgeId))
            extra += RED_SIGNAL_PENALTY;
        return extra;
    }
}
