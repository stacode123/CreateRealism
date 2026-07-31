package net.Realism.content.simulator.core;

/**
 * A node of the simulation graph (same id as its graph-v2 {@code RailNode}).
 * {@code signalBoundary} nodes split signal sections.
 */
public record SimNode(int id, boolean signalBoundary, int dimension, SimVec position) {}
