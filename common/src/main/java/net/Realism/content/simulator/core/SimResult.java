package net.Realism.content.simulator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything a simulation run produced. Trajectories reference graph-v2 edge
 * ids, so the client can place them on the already-synced map graph.
 */
public class SimResult {

    /** One stop at a station. {@code departureTick} is -1 while unresolved. */
    public record StationVisit(int entryIndex, UUID stationId, String stationName,
                               long arrivalTick, long departureTick) {}

    /** A downsampled position/speed sample along a train's run. */
    public record Sample(long tick, int edgeId, float offset, float speed) {}

    public enum EventType {
        ARRIVAL, DEPARTURE, PATH_FAILED, SIGNAL_WAIT_START, SIGNAL_WAIT_END, PARKED, REVERSED
    }

    /**
     * A generic engine event; the M4 conflict detectors consume this stream.
     * {@code data} meaning depends on the type (entry index, wait ticks...).
     */
    public record SimEvent(EventType type, long tick, int trainIndex, int edgeId, double offset, long data) {}

    /** Why a root-cause train isn't moving (and never will on its own). */
    public enum RootCauseKind {
        /** Its destination filter matches no station on the network. */
        NO_MATCHING_STATION,
        /** The destination exists but no route reaches it from here. */
        NO_PATH,
        /** Finished a non-cyclic schedule and parked on the track. */
        FINISHED_PARKED,
        /** Unscheduled/manual train standing on the track from the start. */
        OBSTACLE,
        /** Dwelling at a station under its schedule's wait conditions. */
        SCHEDULE_HOLD,
        /** Member of a confirmed wait-for cycle. */
        DEADLOCK,
        /** Waiting at a red signal whose holder couldn't be identified. */
        SIGNAL_UNKNOWN
    }

    /**
     * The train at the far end of a wait chain still standing when the run
     * ended, with every train queued behind it. {@code detail} is the
     * destination filter (path failures), station name (schedule holds) or
     * fellow cycle members (deadlocks). {@code stranded} is ordered by wait
     * start — the queue as it formed. Position is the root's head.
     */
    public record RootCause(int rootTrain, RootCauseKind kind, String detail,
                            List<Integer> stranded, long sinceTick, SimVec position,
                            int dimension, int anchorEdge, double anchorOffset) {}

    public static class TrainResult {
        public final String id;
        public final String name;
        public final boolean obstacle;
        public final List<StationVisit> visits = new ArrayList<>();
        public final List<Sample> samples = new ArrayList<>();
        /** Every edge the head entered, in traversal order (head edge first). */
        public final List<Integer> path = new ArrayList<>();
        /** Debug: per signal wait, (startTick, blocked edge, holding train). */
        public final List<long[]> waitBlocks = new ArrayList<>();
        /** Debug: the route still planned at run end (edges ahead), capped. */
        public int[] finalPlan = new int[0];
        public final List<String> notices = new ArrayList<>();
        /** How the train ended the run: PARKED, MOVING, WAITING... */
        public String endState = "";

        public TrainResult(String id, String name, boolean obstacle) {
            this.id = id;
            this.name = name;
            this.obstacle = obstacle;
        }
    }

    /**
     * Engine self-timing, for the benchmark test and server-log diagnostics.
     * Collection never influences simulation decisions, so determinism holds.
     */
    public static class Stats {
        public long initNanos;
        public long occupancyNanos;
        /** All tickTrain work, pathfinding included. */
        public long trainLoopNanos;
        public long pathfindNanos;
        public int pathfindCalls;
        public int pathfindFails;
        /** Failures answered from the memo without searching. */
        public int pathfindMemoHits;
        /** Distinct failing (position, targets) keys seen. */
        public int pathfindMemoSize;
        /** Conflict-detector overhead (observation only, never decisions). */
        public long conflictNanos;
    }

    public final List<TrainResult> trains = new ArrayList<>();
    public final List<SimEvent> events = new ArrayList<>();
    /** M4 conflict records — kept out of {@link #events} (digest-hashed). */
    public final List<SimConflict> conflicts = new ArrayList<>();
    /** End-of-run wait chains resolved to their root blockers (observer-only). */
    public final List<RootCause> rootCauses = new ArrayList<>();
    public final Stats stats = new Stats();
    public long ticksSimulated;
    /** True when the wall-clock budget cut the run short of the horizon. */
    public boolean truncated;
}
