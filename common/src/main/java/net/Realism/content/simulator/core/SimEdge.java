package net.Realism.content.simulator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A directed edge of the simulation graph, mirroring one graph-v2
 * {@code RailEdge} (same id). All speeds are blocks/tick, lengths blocks.
 */
public class SimEdge {

    /** Entry signal kinds, matching graph v2's {@code SignalKind} ordinals. */
    public enum Signal {
        NONE, ENTRY, CHAIN
    }

    public record Station(UUID id, String name, double offset, boolean approachable) {}

    public record Crossing(UUID id, double offset) {}

    public final int id;
    public final int from;
    public final int to;
    /** Reverse-direction twin, or -1 for one-way constructs. */
    public final int oppositeId;
    public final double length;
    /** Signal governing entry into this edge at its start node, if any. */
    public final Signal entrySignal;
    /**
     * Directional speed cap from Tramways signs (blocks/tick), 0 = none.
     * Curvature caps are deliberately NOT applied: in-game trains ignore
     * them; turns instead limit to the train's turn speed (Create behavior).
     */
    public final double signCap;
    /** Turn ranges as {@code [start, end]} offsets; trains use turn speed inside. */
    public final double[][] turnRanges;
    /** Unit direction of travel at the start / end of this edge. */
    public final SimVec entryTangent;
    public final SimVec exitTangent;
    public final boolean interDimensional;
    public final List<Station> stations = new ArrayList<>();
    public final List<Crossing> crossings = new ArrayList<>();

    /** Signal section this edge belongs to; assigned by {@link SimGraph}. */
    public int sectionId = -1;
    /** Legal continuation edge ids at the end node; assigned by {@link SimGraph}. */
    public int[] nextEdges = new int[0];

    public SimEdge(int id, int from, int to, int oppositeId, double length, Signal entrySignal,
                   double signCap, double[][] turnRanges, SimVec entryTangent, SimVec exitTangent,
                   boolean interDimensional) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.oppositeId = oppositeId;
        this.length = length;
        this.entrySignal = entrySignal;
        this.signCap = signCap;
        this.turnRanges = turnRanges;
        this.entryTangent = entryTangent;
        this.exitTangent = exitTangent;
        this.interDimensional = interDimensional;
    }

    public boolean inTurn(double offset) {
        for (double[] range : turnRanges)
            if (offset >= range[0] && offset <= range[1])
                return true;
        return false;
    }
}
