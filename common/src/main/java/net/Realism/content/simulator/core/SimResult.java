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

    public static class TrainResult {
        public final String id;
        public final String name;
        public final boolean obstacle;
        public final List<StationVisit> visits = new ArrayList<>();
        public final List<Sample> samples = new ArrayList<>();
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
    }

    public final List<TrainResult> trains = new ArrayList<>();
    public final List<SimEvent> events = new ArrayList<>();
    public final Stats stats = new Stats();
    public long ticksSimulated;
    /** True when the wall-clock budget cut the run short of the horizon. */
    public boolean truncated;
}
