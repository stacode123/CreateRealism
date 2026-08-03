package net.Realism.content.simulator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The simulator's immutable network snapshot. Mirrors a translated graph-v2
 * {@code RailGraph} (edge/node ids match), with derived data the engine
 * needs: turn-legal transitions, signal sections, and per-section occupancy
 * closures across diamond crossings (Create checks occupancy transitively
 * through {@code SignalEdgeGroup.intersectingResolved}; so do we).
 */
public class SimGraph {

    /** Create's turn-legality threshold, {@code TrackEdge.canTravelTo}. */
    private static final double TURN_DOT_THRESHOLD = 7 / 8f;

    public record StationTarget(int edgeId, double offset, UUID stationId, String name) {}

    public final List<SimNode> nodes = new ArrayList<>();
    public final List<SimEdge> edges = new ArrayList<>();

    private int sectionCount;
    /** Per section: sorted ids of all sections sharing occupancy via crossings. */
    private int[][] sectionClosures;
    /** True when any edge crosses dimensions — Euclidean bounds are invalid then. */
    private boolean hasInterDimensional;

    public SimEdge edge(int id) {
        return edges.get(id);
    }

    public int sectionCount() {
        return sectionCount;
    }

    public int[] sectionClosure(int sectionId) {
        return sectionClosures[sectionId];
    }

    public boolean hasInterDimensional() {
        return hasInterDimensional;
    }

    /** Call once after nodes/edges are populated. */
    public void computeDerived() {
        computeTransitions();
        computeSections();
        computeClosures();
        hasInterDimensional = false;
        for (SimEdge edge : edges)
            if (edge.interDimensional)
                hasInterDimensional = true;
    }

    private void computeTransitions() {
        List<List<Integer>> outgoingByNode = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++)
            outgoingByNode.add(new ArrayList<>());
        for (SimEdge edge : edges)
            outgoingByNode.get(edge.from).add(edge.id);

        for (SimEdge edge : edges) {
            List<Integer> candidates = outgoingByNode.get(edge.to);
            boolean boundary = nodes.get(edge.to).signalBoundary();
            List<Integer> legal = new ArrayList<>(candidates.size());
            for (int candidateId : candidates) {
                if (candidateId == edge.oppositeId)
                    continue;
                SimEdge next = edges.get(candidateId);
                // A signal boundary can only be crossed toward a signal head
                // (Create's SignalBoundary.canNavigateVia) — a single-sided
                // signal makes the track one-way.
                if (boundary && next.entrySignal == SimEdge.Signal.NONE)
                    continue;
                if (edge.interDimensional || next.interDimensional
                        || edge.exitTangent.dot(next.entryTangent) > TURN_DOT_THRESHOLD)
                    legal.add(candidateId);
            }
            edge.nextEdges = legal.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /**
     * Signal sections: connected components of edges, cut at signal-boundary
     * nodes. Both directions of a track always share a section.
     */
    private void computeSections() {
        int[] parent = new int[edges.size()];
        for (int i = 0; i < parent.length; i++)
            parent[i] = i;

        for (SimEdge edge : edges)
            if (edge.oppositeId >= 0)
                union(parent, edge.id, edge.oppositeId);

        List<List<Integer>> incidentByNode = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++)
            incidentByNode.add(new ArrayList<>());
        for (SimEdge edge : edges) {
            incidentByNode.get(edge.from).add(edge.id);
            incidentByNode.get(edge.to).add(edge.id);
        }
        for (SimNode node : nodes) {
            if (node.signalBoundary())
                continue;
            List<Integer> incident = incidentByNode.get(node.id());
            for (int i = 1; i < incident.size(); i++)
                union(parent, incident.get(0), incident.get(i));
        }

        sectionCount = 0;
        int[] sectionOfRoot = new int[edges.size()];
        java.util.Arrays.fill(sectionOfRoot, -1);
        for (SimEdge edge : edges) {
            int root = find(parent, edge.id);
            if (sectionOfRoot[root] == -1)
                sectionOfRoot[root] = sectionCount++;
            edge.sectionId = sectionOfRoot[root];
        }
    }

    /** Union sections whose edges share a crossing id, transitively. */
    private void computeClosures() {
        int[] parent = new int[sectionCount];
        for (int i = 0; i < parent.length; i++)
            parent[i] = i;

        java.util.Map<UUID, Integer> firstSectionOfCrossing = new java.util.HashMap<>();
        for (SimEdge edge : edges)
            for (SimEdge.Crossing crossing : edge.crossings) {
                Integer first = firstSectionOfCrossing.putIfAbsent(crossing.id(), edge.sectionId);
                if (first != null)
                    union(parent, first, edge.sectionId);
            }

        List<List<Integer>> members = new ArrayList<>(sectionCount);
        for (int i = 0; i < sectionCount; i++)
            members.add(new ArrayList<>());
        for (int i = 0; i < sectionCount; i++)
            members.get(find(parent, i)).add(i);

        sectionClosures = new int[sectionCount][];
        for (int i = 0; i < sectionCount; i++) {
            List<Integer> group = members.get(find(parent, i));
            sectionClosures[i] = group.stream().mapToInt(Integer::intValue).sorted().toArray();
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB)
            parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
    }

    /** All approachable platforms whose name matches the compiled pattern. */
    public List<StationTarget> findStations(java.util.regex.Pattern pattern) {
        List<StationTarget> result = new ArrayList<>();
        for (SimEdge edge : edges)
            for (SimEdge.Station station : edge.stations) {
                if (!station.approachable())
                    continue;
                if (pattern.matcher(station.name()).matches())
                    result.add(new StationTarget(edge.id, station.offset(), station.id(), station.name()));
            }
        return result;
    }

    /** The approachable platform with this id, if present. */
    public StationTarget findStation(UUID stationId) {
        for (SimEdge edge : edges)
            for (SimEdge.Station station : edge.stations)
                if (station.approachable() && station.id().equals(stationId))
                    return new StationTarget(edge.id, station.offset(), station.id(), station.name());
        return null;
    }
}
