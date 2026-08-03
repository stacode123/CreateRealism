package net.Realism.content.simulator.core;

import java.util.List;

/**
 * One detected scheduling conflict (SPEC §4.5). Identical repeats (same
 * resource, same trains) are merged: {@code startTick}/{@code endTick} span
 * the first occurrence and {@code count} says how often it repeated.
 * {@code trains} are engine train indexes, primary train first (the waiter /
 * follower / first dweller). {@code nonDeterministic} marks conflicts
 * involving live-snapshot-anchored trains or static obstacles — those depend
 * on the network state at the moment Simulate was pressed.
 * {@code anchorEdge}/{@code anchorOffset} is the graph-space location behind
 * {@code position}, so presentation can project it (e.g. onto the diagram).
 */
public record SimConflict(Type type, long startTick, long endTick, int count,
                          SimVec position, int dimension, String resourceName,
                          List<Integer> trains, boolean nonDeterministic,
                          int anchorEdge, double anchorOffset) {

    public enum Type {
        /** A train held at a red signal longer than the wait threshold. */
        SECTION,
        /** Mutually blocked trains: a cycle in the wait-for graph. */
        DEADLOCK,
        /** Consecutive trains through a section closer than the threshold. */
        HEADWAY,
        /** Overlapping dwell windows at the same station platform. */
        PLATFORM
    }
}
