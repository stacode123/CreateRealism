package net.Realism.content.simulator;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-only sidecar the translator produces alongside a {@code RailGraph}:
 * everything the simulator needs that the map client does not — edge-end
 * tangents (turn legality), directional Tramways sign caps, and the mapping
 * from Create's micro edges back to collapsed edges (to place live trains).
 * Never serialized; cached with the translation result.
 */
public class SimTopology {

    /** A live-train position mapped onto the collapsed graph. */
    public record Location(int edgeId, double offset) {}

    private record MicroSegment(int walk, double segStart, double segLength, boolean alignedWithWalk) {}

    private static class WalkTable {
        double[] bounds;
        int[] forwardIds;
        int[] backwardIds;
    }

    /** Per directed edge id: unit travel direction at the edge's start/end. */
    private final List<Vec3> entryTangents = new ArrayList<>();
    private final List<Vec3> exitTangents = new ArrayList<>();
    /** Per directed edge id: min Tramways sign cap in km/h, 0 = none. */
    private final List<Double> signCapsKmh = new ArrayList<>();

    private final List<WalkTable> walks = new ArrayList<>();
    /** Packed (fromNetId, toNetId) of a Create micro edge → its walk segment. */
    private final Map<Long, MicroSegment> microSegments = new HashMap<>();

    private static long key(int fromNetId, int toNetId) {
        return ((long) fromNetId << 32) | (toNetId & 0xFFFFFFFFL);
    }

    /** Called by the translator in edge-id order (forward, then backward). */
    public void addEdge(Vec3 entryTangent, Vec3 exitTangent, double signCapKmh) {
        entryTangents.add(entryTangent);
        exitTangents.add(exitTangent);
        signCapsKmh.add(signCapKmh);
    }

    public int addWalk(double[] bounds, int[] forwardIds, int[] backwardIds) {
        WalkTable table = new WalkTable();
        table.bounds = bounds;
        table.forwardIds = forwardIds;
        table.backwardIds = backwardIds;
        walks.add(table);
        return walks.size() - 1;
    }

    public void addMicroSegment(int walk, int fromNetId, int toNetId, double segStart, double segLength) {
        microSegments.put(key(fromNetId, toNetId), new MicroSegment(walk, segStart, segLength, true));
        microSegments.put(key(toNetId, fromNetId), new MicroSegment(walk, segStart, segLength, false));
    }

    public Vec3 entryTangent(int edgeId) {
        return entryTangents.get(edgeId);
    }

    public Vec3 exitTangent(int edgeId) {
        return exitTangents.get(edgeId);
    }

    public double signCapKmh(int edgeId) {
        return signCapsKmh.get(edgeId);
    }

    /**
     * Maps a Create travelling point — micro edge (node1 → node2, by netId)
     * and distance along it in travel direction — to a collapsed directed
     * edge and offset. Null when the segment is unknown (degenerate walks).
     */
    public Location locate(int fromNetId, int toNetId, double position) {
        MicroSegment segment = microSegments.get(key(fromNetId, toNetId));
        if (segment == null)
            return null;
        WalkTable table = walks.get(segment.walk());
        double walkDistance = segment.alignedWithWalk()
                ? segment.segStart() + position
                : segment.segStart() + (segment.segLength() - position);
        walkDistance = Math.max(table.bounds[0],
                Math.min(walkDistance, table.bounds[table.bounds.length - 1]));

        int index = 0;
        while (index + 2 < table.bounds.length && walkDistance > table.bounds[index + 1])
            index++;

        if (segment.alignedWithWalk())
            return new Location(table.forwardIds[index], walkDistance - table.bounds[index]);
        return new Location(table.backwardIds[index], table.bounds[index + 1] - walkDistance);
    }
}
