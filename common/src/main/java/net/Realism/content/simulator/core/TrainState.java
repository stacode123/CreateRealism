package net.Realism.content.simulator.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mutable engine-side state of one train. Position is tracked as the head's
 * (edge, offset) plus the occupancy spans behind it; navigation as the
 * remaining route of edge ids with {@code route[routeIndex] == headEdge}.
 */
public class TrainState {

    public enum Mode {
        /** Needs to start/resolve its current schedule entry. */
        PRE_TRANSIT,
        /** Following a route toward a station. */
        MOVING,
        /** At a station, ticking wait conditions / departure gates. */
        WAITING,
        /** Done (non-cyclic end) — occupies track, never moves again. */
        PARKED,
        /** Static obstacle from the start (manual/unscheduled/excluded). */
        OBSTACLE
    }

    public final SimTrainSpec spec;
    public final int index;

    public Mode mode;
    public double speed;
    public double throttle;

    public int headEdge;
    public double headOffset;
    /** Spans {@code {edgeId, startOffset, endOffset}}, ordered tail → head. */
    public final ArrayDeque<double[]> occupied = new ArrayDeque<>();

    public int[] route;
    public int routeIndex;
    public double distanceToTarget;
    public UUID targetStation;
    public String targetStationName = "";
    /** Station to resume toward (train was in transit at snapshot), or null. */
    public UUID resumeDestination;
    /** Entry index whose pathfinding already logged a failure event. */
    public int lastPathFailEntry = -1;

    /** Still holding at the departure station (Create's pre-departure gate). */
    public boolean holdingAtStation;
    public int cooldown;

    public int currentEntry;
    public int[] columnProgress;
    public int[] columnElapsed;
    /** Set once every gate-relevant column finished; departure gates pending. */
    public boolean conditionsDone;
    public final List<SimCondition.Separation> departureGates = new ArrayList<>();
    /** State for {@link SimCondition.TimeOfDayRealistic} rollover handling. */
    public int lastScheduledDepartureDayTime;

    public UUID currentStationId;
    public String currentStationName = "";
    public long arrivalTick = -1;

    public boolean signalWaiting;
    public long signalWaitStart;

    public final SimResult.TrainResult result;

    public TrainState(SimTrainSpec spec, int index) {
        this.spec = spec;
        this.index = index;
        this.throttle = spec.initialThrottle;
        this.speed = spec.initialSpeed;
        this.headEdge = spec.headEdge;
        this.headOffset = spec.headOffset;
        this.currentEntry = spec.startEntry;
        this.resumeDestination = spec.resumeDestination;
        this.result = new SimResult.TrainResult(spec.id, spec.name, spec.program == null);
        this.result.notices.addAll(spec.notices);
    }

    public double occupiedLength() {
        double total = 0;
        for (double[] span : occupied)
            total += span[2] - span[1];
        return total;
    }
}
