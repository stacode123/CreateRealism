package net.Realism.content.simulator.core;

import java.util.List;

/**
 * Immutable input description of one train. Speeds are blocks/tick,
 * acceleration blocks/tick². {@code headEdge}/{@code headOffset} place the
 * head of the train facing the edge's direction of travel.
 *
 * <p>{@code program == null} marks a static obstacle: the train never moves
 * but its interval still occupies track (manual, unscheduled or excluded
 * trains — their real movement is unpredictable).
 */
public class SimTrainSpec {

    public final String id;
    public final String name;
    public final double length;
    public final double acceleration;
    public final double topSpeed;
    public final double turnSpeed;
    public final double initialThrottle;
    public final SimProgram program;
    public final int headEdge;
    public final double headOffset;

    /** Schedule progress snapshot: entry index the train is currently on. */
    public int startEntry = 0;
    /** True when the train was mid-dwell at its snapshot (POST_TRANSIT). */
    public boolean startWaiting = false;
    /** Per-column condition progress at snapshot (may be null = fresh). */
    public int[] startColumnProgress = null;
    /** Per-column elapsed ticks of the active condition at snapshot. */
    public int[] startColumnElapsed = null;
    /**
     * Station id the train was already navigating to at snapshot, or null.
     * Lets an in-transit train resume toward its actual destination instead
     * of re-resolving the filter.
     */
    public java.util.UUID resumeDestination = null;
    /** Extra notices to surface with results (e.g. snapshot anchoring). */
    public final List<String> notices = new java.util.ArrayList<>();
    /** Whether pathfinding may turn the train around (double-ended). */
    public boolean canReverse = true;
    /** Speed at snapshot time (blocks/tick), so moving trains don't restart cold. */
    public double initialSpeed = 0;

    public SimTrainSpec(String id, String name, double length, double acceleration, double topSpeed,
                        double turnSpeed, double initialThrottle, SimProgram program,
                        int headEdge, double headOffset) {
        this.id = id;
        this.name = name;
        this.length = length;
        this.acceleration = acceleration;
        this.topSpeed = topSpeed;
        this.turnSpeed = turnSpeed;
        this.initialThrottle = initialThrottle;
        this.program = program;
        this.headEdge = headEdge;
        this.headOffset = headOffset;
    }
}
