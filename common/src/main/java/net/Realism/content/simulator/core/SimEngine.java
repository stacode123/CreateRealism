package net.Realism.content.simulator.core;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The deterministic train simulator. Fixed 1-tick steps, trains processed in
 * input order, no wall clock in any decision — identical inputs produce
 * identical results.
 *
 * <p>Movement, signalling and condition semantics deliberately mirror Create
 * 6.0.8 ({@code Navigation.tick}, {@code ScheduleRuntime}, {@code
 * SignalEdgeGroup}) so projected times match in-game behavior: bang-bang
 * speed control with {@code v²/2a} braking curves, persistent first-come
 * section claims, chain signals reserving whole chains atomically, occupancy
 * checked transitively across diamond crossings, turns limited by the
 * train's turn speed, and Tramways sign caps as limit regions.
 */
public class SimEngine {

    /** Create's arrival threshold (1/32 blocks). */
    private static final double ARRIVAL_EPS = 1 / 32d;
    /** Create's pre-departure signal lookahead at stations. */
    private static final double PRE_DEPARTURE_LOOKAHEAD = 4.5;
    /** Create's schedule retry cooldown ({@code ScheduleRuntime.INTERVAL}). */
    private static final int RETRY_COOLDOWN = 40;
    /** A train held at a signal re-navigates this often (Create keeps looking). */
    private static final long REPATH_WAIT_TICKS = 100;

    private static class StationHistory {
        long lastAny = Long.MIN_VALUE;
        final TreeMap<String, Long> byTrainName = new TreeMap<>();
        final TreeMap<String, Long> byLine = new TreeMap<>();
        final TreeMap<String, Long> byCategory = new TreeMap<>();
    }

    private final SimGraph graph;
    private final SimClock clock;
    private final long horizon;
    private final int sampleStride;
    private final long maxWallMillis;

    private final List<TrainState> trains = new ArrayList<>();
    private final SimResult result = new SimResult();
    private final ConflictDetector conflicts;

    /** Per-tick occupancy: how many trains sit in each section, and which. */
    private final int[] sectionTrainCount;
    private final int[] sectionSingleTrain;
    /**
     * Occupancy of trains that will never move again (obstacles, parked) —
     * rebuilt only when a train parks, copied per tick instead of re-marked.
     */
    private final int[] staticTrainCount;
    private final int[] staticSingleTrain;
    private boolean staticOccupancyDirty = true;
    /**
     * Signal-section claims, first come first served. A claim persists as
     * long as its owner keeps approaching (refreshes it every tick) and is
     * released once the owner stops refreshing — entered the section,
     * rerouted, reversed or arrived. Mirrors Create's signal groups staying
     * reserved for the first train that committed; re-deriving claims per
     * tick in train order would let a later train steal the block from a
     * train that claimed it first.
     */
    private final int[] sectionReservedBy;
    /** Tick each claim was last refreshed on; stale claims are released. */
    private final long[] sectionClaimTick;
    /** Unique legal predecessor per edge; -1 when none or ambiguous. */
    private final int[] uniquePredecessor;
    /**
     * Approachable-platform lookup for pathfinding penalties: per edge, which
     * stations a span on that edge can cover (own and mirrored from the
     * opposite edge) and at what offset in this edge's frame — so coverage is
     * found by iterating train spans instead of stations × trains.
     */
    private final int[][] edgeStationIndex;
    private final double[][] edgeStationOffset;
    /** Per station: the edge that takes its 50/300 penalty (Create: STATION). */
    private final int[] stationPenaltyEdge;
    private final double[] penaltyScratch;
    private final boolean[] stationCoveredScratch;
    /** Station targets per glob pattern — the graph never changes mid-run. */
    private final Map<String, List<SimGraph.StationTarget>> stationTargetCache = new HashMap<>();

    /**
     * Failed searches, keyed by exact start state and target set. Penalties
     * only ever add cost — they never sever reachability — so a search that
     * found no route keeps finding none until the train moves or its targets
     * change. Retrying stuck trains (Create retries every 40 ticks, forever)
     * then costs a lookup instead of a full-graph exploration.
     */
    private record PathFailKey(int headEdge, long headOffsetBits, int reverseEdge,
                               long reverseOffsetBits, String targetKey) {}

    private final java.util.Set<PathFailKey> pathFailMemo = new java.util.HashSet<>();

    /** CRN-style departure history: station name → last departures. */
    private final TreeMap<String, StationHistory> departureHistory = new TreeMap<>();
    private final Map<String, Pattern> patternCache = new HashMap<>();

    private long currentTick;

    public SimEngine(SimGraph graph, List<SimTrainSpec> specs, SimClock clock,
                     long horizonTicks, int sampleStride, long maxWallMillis) {
        this(graph, specs, clock, horizonTicks, sampleStride, maxWallMillis, 600, 200);
    }

    /**
     * @param waitConflictTicks    red-signal wait that becomes a SECTION
     *                             conflict; ≤0 disables wait conflicts
     * @param headwayConflictTicks minimum gap between consecutive trains
     *                             through a section; CRN separation
     *                             conditions tighten it per train pair, so
     *                             ≤0 only disables the flat threshold
     */
    public SimEngine(SimGraph graph, List<SimTrainSpec> specs, SimClock clock,
                     long horizonTicks, int sampleStride, long maxWallMillis,
                     long waitConflictTicks, long headwayConflictTicks) {
        this.graph = graph;
        this.clock = clock;
        this.horizon = horizonTicks;
        this.sampleStride = Math.max(1, sampleStride);
        this.maxWallMillis = maxWallMillis;
        this.sectionTrainCount = new int[graph.sectionCount()];
        this.sectionSingleTrain = new int[graph.sectionCount()];
        this.staticTrainCount = new int[graph.sectionCount()];
        this.staticSingleTrain = new int[graph.sectionCount()];
        this.sectionReservedBy = new int[graph.sectionCount()];
        java.util.Arrays.fill(sectionReservedBy, -1);
        this.sectionClaimTick = new long[graph.sectionCount()];
        // -2 marks "seen twice" while building; collapsed to -1 below.
        this.uniquePredecessor = new int[graph.edges.size()];
        java.util.Arrays.fill(uniquePredecessor, -1);
        for (SimEdge candidate : graph.edges)
            for (int next : candidate.nextEdges) {
                if (next == candidate.id)
                    continue;
                uniquePredecessor[next] = uniquePredecessor[next] == -1 ? candidate.id : -2;
            }
        for (int i = 0; i < uniquePredecessor.length; i++)
            if (uniquePredecessor[i] == -2)
                uniquePredecessor[i] = -1;

        int edgeCount = graph.edges.size();
        List<List<int[]>> touching = new ArrayList<>(edgeCount);
        List<List<Double>> touchingOffsets = new ArrayList<>(edgeCount);
        for (int i = 0; i < edgeCount; i++) {
            touching.add(new ArrayList<>());
            touchingOffsets.add(new ArrayList<>());
        }
        List<Integer> penaltyEdges = new ArrayList<>();
        for (SimEdge edge : graph.edges)
            for (SimEdge.Station station : edge.stations) {
                if (!station.approachable())
                    continue;
                int stationIndex = penaltyEdges.size();
                penaltyEdges.add(edge.id);
                touching.get(edge.id).add(new int[] { stationIndex });
                touchingOffsets.get(edge.id).add(station.offset());
                if (edge.oppositeId >= 0) {
                    touching.get(edge.oppositeId).add(new int[] { stationIndex });
                    touchingOffsets.get(edge.oppositeId).add(edge.length - station.offset());
                }
            }
        this.edgeStationIndex = new int[edgeCount][];
        this.edgeStationOffset = new double[edgeCount][];
        for (int i = 0; i < edgeCount; i++) {
            edgeStationIndex[i] = touching.get(i).stream().mapToInt(entry -> entry[0]).toArray();
            edgeStationOffset[i] = touchingOffsets.get(i).stream().mapToDouble(Double::doubleValue).toArray();
        }
        this.stationPenaltyEdge = penaltyEdges.stream().mapToInt(Integer::intValue).toArray();
        this.penaltyScratch = new double[edgeCount];
        this.stationCoveredScratch = new boolean[stationPenaltyEdge.length];

        for (int i = 0; i < specs.size(); i++)
            trains.add(new TrainState(specs.get(i), i));
        this.conflicts = new ConflictDetector(graph, trains, sectionReservedBy,
                waitConflictTicks, headwayConflictTicks);
    }

    public SimResult run() {
        long initStart = System.nanoTime();
        for (TrainState train : trains)
            initTrain(train);
        result.stats.initNanos = System.nanoTime() - initStart;

        long wallStart = System.currentTimeMillis();
        long tick = 0;
        for (; tick < horizon; tick++) {
            currentTick = tick;
            if (maxWallMillis > 0 && (tick & 1023) == 0
                    && System.currentTimeMillis() - wallStart > maxWallMillis) {
                result.truncated = true;
                break;
            }

            long occupancyStart = System.nanoTime();
            rebuildOccupancy();
            // Release claims whose owner didn't refresh them last tick.
            for (int s = 0; s < sectionReservedBy.length; s++)
                if (sectionReservedBy[s] != -1 && sectionClaimTick[s] < tick - 1)
                    sectionReservedBy[s] = -1;
            long loopStart = System.nanoTime();
            result.stats.occupancyNanos += loopStart - occupancyStart;

            boolean anyActive = false;
            for (TrainState train : trains) {
                tickTrain(train, tick);
                if (train.mode != TrainState.Mode.PARKED && train.mode != TrainState.Mode.OBSTACLE)
                    anyActive = true;
            }
            result.stats.trainLoopNanos += System.nanoTime() - loopStart;

            long conflictStart = System.nanoTime();
            conflicts.tick(tick);
            result.stats.conflictNanos += System.nanoTime() - conflictStart;

            if (tick % sampleStride == 0)
                for (TrainState train : trains)
                    if (train.mode != TrainState.Mode.OBSTACLE
                            && (train.mode != TrainState.Mode.PARKED || tick == 0))
                        sample(train, tick);

            if (!anyActive) {
                tick++;
                break;
            }
        }

        result.ticksSimulated = tick;
        result.stats.pathfindMemoSize = pathFailMemo.size();
        long conflictStart = System.nanoTime();
        conflicts.finish(result, tick);
        result.stats.conflictNanos += System.nanoTime() - conflictStart;
        for (TrainState train : trains) {
            sample(train, Math.min(tick, horizon));
            train.result.endState = train.mode.name();
            if (train.route != null && train.routeIndex < train.route.length) {
                int remaining = Math.min(train.route.length - train.routeIndex, 200);
                train.result.finalPlan = new int[remaining];
                System.arraycopy(train.route, train.routeIndex,
                        train.result.finalPlan, 0, remaining);
            }
            result.trains.add(train.result);
        }
        return result;
    }

    private void initTrain(TrainState train) {
        train.result.path.add(train.headEdge);
        initOccupancy(train);
        SimTrainSpec spec = train.spec;
        if (spec.program == null || spec.program.entries.isEmpty()) {
            train.mode = spec.program == null ? TrainState.Mode.OBSTACLE : TrainState.Mode.PARKED;
            sample(train, 0);
            return;
        }
        train.currentEntry = Math.min(spec.startEntry, spec.program.entries.size() - 1);
        if (spec.startWaiting
                && spec.program.entries.get(train.currentEntry).kind == SimProgram.InstructionKind.DESTINATION) {
            train.mode = TrainState.Mode.WAITING;
            train.arrivalTick = 0;
            resolveCurrentStation(train);
            initColumns(train);
            if (spec.startColumnProgress != null
                    && spec.startColumnProgress.length == train.columnProgress.length) {
                System.arraycopy(spec.startColumnProgress, 0, train.columnProgress, 0,
                        train.columnProgress.length);
                if (spec.startColumnElapsed != null
                        && spec.startColumnElapsed.length == train.columnElapsed.length)
                    System.arraycopy(spec.startColumnElapsed, 0, train.columnElapsed, 0,
                            train.columnElapsed.length);
            }
            train.result.visits.add(new SimResult.StationVisit(train.currentEntry, train.currentStationId,
                    train.currentStationName, 0, -1));
            train.holdingAtStation = true;
        } else {
            train.mode = TrainState.Mode.PRE_TRANSIT;
        }
    }

    /**
     * Covers {@code length} blocks of track behind the head: back along the
     * head edge, then through unique legal predecessors. Ambiguous history
     * (a switch directly behind) is clamped — slight under-coverage beats
     * inventing occupancy on the wrong branch.
     */
    private void initOccupancy(TrainState train) {
        double remaining = train.spec.length;
        int edgeId = train.headEdge;
        double end = train.headOffset;
        train.occupied.clear();
        train.occupiedVersion++;
        while (true) {
            double start = Math.max(0, end - remaining);
            train.occupied.addFirst(new double[] { edgeId, start, end });
            remaining -= end - start;
            if (remaining <= 1e-6)
                return;
            int predecessor = uniquePredecessor[edgeId];
            if (predecessor == -1)
                return;
            edgeId = predecessor;
            end = graph.edge(predecessor).length;
        }
    }

    private static boolean isStatic(TrainState train) {
        return train.mode == TrainState.Mode.OBSTACLE || train.mode == TrainState.Mode.PARKED;
    }

    private void rebuildOccupancy() {
        if (staticOccupancyDirty) {
            java.util.Arrays.fill(staticTrainCount, 0);
            java.util.Arrays.fill(staticSingleTrain, -1);
            for (TrainState train : trains)
                if (isStatic(train))
                    for (double[] span : train.occupied)
                        markSection(staticTrainCount, staticSingleTrain,
                                graph.edge((int) span[0]).sectionId, train.index);
            staticOccupancyDirty = false;
        }
        System.arraycopy(staticTrainCount, 0, sectionTrainCount, 0, sectionTrainCount.length);
        System.arraycopy(staticSingleTrain, 0, sectionSingleTrain, 0, sectionSingleTrain.length);
        // Opposite edges share their twin's section by construction, so
        // marking the spanned edge's section covers both directions.
        for (TrainState train : trains) {
            if (isStatic(train))
                continue;
            for (double[] span : train.occupied)
                markSection(sectionTrainCount, sectionSingleTrain,
                        graph.edge((int) span[0]).sectionId, train.index);
        }
    }

    private static void markSection(int[] count, int[] single, int section, int trainIndex) {
        if (count[section] == 0) {
            count[section] = 1;
            single[section] = trainIndex;
        } else if (single[section] != trainIndex) {
            count[section] = 2;
        }
    }

    /**
     * The train responsible for this edge's section reading occupied — a
     * physical occupant first, else the claim holder. Debug bookkeeping
     * for wait records; -1 when unknown.
     */
    private int sectionHolder(int edgeId, int me) {
        if (edgeId < 0)
            return -1;
        for (int linked : graph.sectionClosure(graph.edge(edgeId).sectionId)) {
            if (sectionTrainCount[linked] >= 1 && sectionSingleTrain[linked] != me
                    && sectionSingleTrain[linked] != -1)
                return sectionSingleTrain[linked];
            if (sectionReservedBy[linked] != -1 && sectionReservedBy[linked] != me)
                return sectionReservedBy[linked];
        }
        return -1;
    }

    /** Mirrors {@code SignalEdgeGroup.isOccupiedUnless} incl. crossings. */
    private boolean occupiedByOther(int section, int trainIndex) {
        for (int linked : graph.sectionClosure(section)) {
            if (sectionTrainCount[linked] >= 2)
                return true;
            if (sectionTrainCount[linked] == 1 && sectionSingleTrain[linked] != trainIndex)
                return true;
            if (sectionReservedBy[linked] != -1 && sectionReservedBy[linked] != trainIndex)
                return true;
        }
        return false;
    }

    private void tickTrain(TrainState train, long tick) {
        switch (train.mode) {
            case OBSTACLE, PARKED -> { }
            case PRE_TRANSIT -> tickPreTransit(train, tick);
            case WAITING -> tickWaiting(train, tick);
            case MOVING -> tickMoving(train, tick);
        }
    }

    // ------------------------------------------------------------------
    // PRE_TRANSIT: schedule entry dispatch (mirrors ScheduleRuntime.tick)
    // ------------------------------------------------------------------

    private void tickPreTransit(TrainState train, long tick) {
        if (train.cooldown-- > 0)
            return;
        SimProgram program = train.spec.program;
        if (train.currentEntry >= program.entries.size()) {
            train.currentEntry = 0;
            if (!program.cyclic) {
                train.mode = TrainState.Mode.PARKED;
                staticOccupancyDirty = true;
                event(SimResult.EventType.PARKED, tick, train, 0);
            }
            return;
        }

        SimProgram.Entry entry = program.entries.get(train.currentEntry);
        switch (entry.kind) {
            case THROTTLE -> {
                train.throttle = entry.throttle;
                train.currentEntry++;
            }
            case NO_OP -> train.currentEntry++;
            case DESTINATION -> startNavigation(train, entry, tick);
        }
    }

    private void startNavigation(TrainState train, SimProgram.Entry entry, long tick) {
        long pathfindStart = System.nanoTime();
        int reverseEdge = -1;
        double reverseOffset = 0;
        if (train.spec.canReverse && !train.occupied.isEmpty()) {
            double[] tailSpan = train.occupied.peekFirst();
            SimEdge tailEdge = graph.edge((int) tailSpan[0]);
            if (tailEdge.oppositeId >= 0) {
                reverseEdge = tailEdge.oppositeId;
                reverseOffset = tailEdge.length - tailSpan[1];
            }
        }

        SimPathfinder.Path path;
        if (train.resumeDestination != null
                && graph.findStation(train.resumeDestination) != null) {
            path = searchMemoized(train, List.of(graph.findStation(train.resumeDestination)),
                    "R" + train.resumeDestination, reverseEdge, reverseOffset, null);
        } else if (entry.patterns.size() == 1) {
            path = searchMemoized(train, findStationsCached(entry.pattern),
                    "P" + entry.pattern.pattern(), reverseEdge, reverseOffset, null);
        } else {
            path = prioritizedSearch(train, entry, reverseEdge, reverseOffset);
        }
        result.stats.pathfindNanos += System.nanoTime() - pathfindStart;
        result.stats.pathfindCalls++;

        if (path == null) {
            result.stats.pathfindFails++;
            if (train.lastPathFailEntry != train.currentEntry) {
                train.lastPathFailEntry = train.currentEntry;
                event(SimResult.EventType.PATH_FAILED, tick, train, train.currentEntry);
            }
            train.cooldown = RETRY_COOLDOWN;
            return;
        }
        train.lastPathFailEntry = -1;
        train.resumeDestination = null;

        if (path.reversed())
            reverse(train, tick);

        train.route = path.edges();
        train.routeIndex = 0;
        train.distanceToTarget = path.distance();
        train.targetStation = path.target().stationId();
        train.targetStationName = path.target().name();
        train.signalWaiting = false;
        train.mode = TrainState.Mode.MOVING;
    }

    /**
     * Mirrors Create's {@code Navigation} pathfinding costs: other trains
     * make their endpoint edges expensive ({@code Train.getNavigationPenalty}
     * halved, both directions), foreign platforms cost a little (a lot when a
     * train stands there), and red governed entries add signal weight — so
     * simulated routes divert around blockages exactly like real ones.
     */
    private SimPathfinder.Penalties buildPenalties(TrainState me) {
        double[] base = penaltyScratch;
        java.util.Arrays.fill(base, 0);
        boolean[] covered = stationCoveredScratch;
        java.util.Arrays.fill(covered, false);
        for (TrainState other : trains) {
            if (other == me || other.occupied.isEmpty())
                continue;
            double half = navigationPenalty(other) / 2.0;
            addEndpointPenalty(base, other.occupied.peekFirst(), half);
            addEndpointPenalty(base, other.occupied.peekLast(), half);
            for (double[] span : other.occupied) {
                int edgeId = (int) span[0];
                int[] stationsHere = edgeStationIndex[edgeId];
                double[] offsetsHere = edgeStationOffset[edgeId];
                for (int k = 0; k < stationsHere.length; k++)
                    if (offsetsHere[k] >= span[1] - 3 && offsetsHere[k] <= span[2] + 3)
                        covered[stationsHere[k]] = true;
            }
        }
        // Station penalties (Create: STATION=50, STATION_WITH_TRAIN=300).
        for (int s = 0; s < stationPenaltyEdge.length; s++)
            base[stationPenaltyEdge[s]] += covered[s] ? 350 : 50;
        return new SimPathfinder.Penalties() {
            @Override
            public double edgeBase(int edgeId) {
                return base[edgeId];
            }

            @Override
            public boolean redSignalEntry(int edgeId) {
                return occupiedByOther(graph.edge(edgeId).sectionId, me.index);
            }
        };
    }

    /** One memoized pathfinder invocation toward a fixed target set. */
    private SimPathfinder.Path searchMemoized(TrainState train, List<SimGraph.StationTarget> targets,
                                              String targetKey, int reverseEdge, double reverseOffset,
                                              SimPathfinder.Penalties penalties) {
        if (targets.isEmpty())
            return null;
        PathFailKey memoKey = new PathFailKey(train.headEdge,
                Double.doubleToLongBits(train.headOffset), reverseEdge,
                Double.doubleToLongBits(reverseOffset), targetKey);
        if (pathFailMemo.contains(memoKey)) {
            result.stats.pathfindMemoHits++;
            return null;
        }
        SimPathfinder.Path path = SimPathfinder.find(graph, train.headEdge, train.headOffset,
                targets, train.spec.canReverse, reverseEdge, reverseOffset,
                penalties != null ? penalties : buildPenalties(train));
        if (path == null)
            pathFailMemo.add(memoKey);
        return path;
    }

    /**
     * CRN's {@code PrioritizedDestinationInstruction.start}: filters in
     * priority order, cheapest matching station per filter, the first
     * reachable filter wins — unless avoid-trains is set, where a busy
     * chosen station makes later filters preferable (fewest problems,
     * earliest wins ties). The avoid-red-signal toggle inspects the train's
     * own surroundings, identical for every filter, so it can never change
     * which filter wins and is not modeled.
     */
    private SimPathfinder.Path prioritizedSearch(TrainState train, SimProgram.Entry entry,
                                                 int reverseEdge, double reverseOffset) {
        SimPathfinder.Penalties penalties = buildPenalties(train);
        SimPathfinder.Path best = null;
        int bestProblems = Integer.MAX_VALUE;
        for (Pattern pattern : entry.patterns) {
            SimPathfinder.Path path = searchMemoized(train, findStationsCached(pattern),
                    "P" + pattern.pattern(), reverseEdge, reverseOffset, penalties);
            if (path == null)
                continue;
            int problems = entry.avoidTrains && stationBusy(path.target(), train) ? 1 : 0;
            if (problems < bestProblems) {
                bestProblems = problems;
                best = path;
                if (problems == 0)
                    break;
            }
        }
        return best;
    }

    /** CRN's present/imminent/nearest-train test at a station, from sim state. */
    private boolean stationBusy(SimGraph.StationTarget target, TrainState me) {
        SimEdge platformEdge = graph.edge(target.edgeId());
        int opposite = platformEdge.oppositeId;
        double mirroredOffset = platformEdge.length - target.offset();
        for (TrainState other : trains) {
            if (other == me)
                continue;
            if (target.stationId().equals(other.currentStationId))
                return true;
            if (other.mode == TrainState.Mode.MOVING
                    && target.stationId().equals(other.targetStation))
                return true;
            for (double[] span : other.occupied) {
                int spanEdge = (int) span[0];
                if (spanEdge == target.edgeId()
                        && target.offset() >= span[1] - 3 && target.offset() <= span[2] + 3)
                    return true;
                if (spanEdge == opposite
                        && mirroredOffset >= span[1] - 3 && mirroredOffset <= span[2] + 3)
                    return true;
            }
        }
        return false;
    }

    private List<SimGraph.StationTarget> findStationsCached(Pattern pattern) {
        return stationTargetCache.computeIfAbsent(pattern.pattern(),
                key -> graph.findStations(pattern));
    }

    /** Create's {@code Train.getNavigationPenalty}, from sim state. */
    private double navigationPenalty(TrainState other) {
        return switch (other.mode) {
            case OBSTACLE, PARKED -> 700;                       // IDLE_TRAIN
            case WAITING, PRE_TRANSIT -> 50;                    // WAITING_TRAIN
            case MOVING -> other.signalWaiting
                    ? 50 + Math.min((currentTick - other.signalWaitStart) / 20.0, 1000)
                    : 25;                                       // ANY_TRAIN
        };
    }

    private void addEndpointPenalty(double[] base, double[] span, double amount) {
        if (span == null)
            return;
        int edgeId = (int) span[0];
        base[edgeId] += amount;
        int opposite = graph.edge(edgeId).oppositeId;
        if (opposite >= 0)
            base[opposite] += amount;
    }

    /** Turn the train around: head becomes tail, spans flip direction. */
    private void reverse(TrainState train, long tick) {
        ArrayList<double[]> flipped = new ArrayList<>();
        for (double[] span : train.occupied) {
            SimEdge edge = graph.edge((int) span[0]);
            if (edge.oppositeId < 0)
                continue;
            flipped.add(new double[] { edge.oppositeId, edge.length - span[2], edge.length - span[1] });
        }
        train.occupied.clear();
        train.occupiedVersion++;
        for (int i = flipped.size() - 1; i >= 0; i--)
            train.occupied.addLast(flipped.get(i));
        if (!train.occupied.isEmpty()) {
            double[] headSpan = train.occupied.peekLast();
            train.headEdge = (int) headSpan[0];
            train.headOffset = headSpan[2];
            train.result.path.add(train.headEdge);
        }
        event(SimResult.EventType.REVERSED, tick, train, 0);
    }

    // ------------------------------------------------------------------
    // WAITING: condition columns + departure gates
    // ------------------------------------------------------------------

    private void initColumns(TrainState train) {
        SimProgram.Entry entry = train.spec.program.entries.get(train.currentEntry);
        train.columnProgress = new int[entry.columns.size()];
        train.columnElapsed = new int[entry.columns.size()];
        train.conditionsDone = false;
        train.departureGates.clear();
    }

    private void tickWaiting(TrainState train, long tick) {
        SimProgram.Entry entry = train.spec.program.entries.get(train.currentEntry);

        if (!train.conditionsDone) {
            // Mirrors ScheduleRuntime.tickConditions: completion is detected
            // at the top of the loop, columns race, conditions in a column
            // run one after another. No columns at all = wait forever.
            for (int i = 0; i < entry.columns.size(); i++) {
                List<SimCondition> column = entry.columns.get(i);
                if (train.columnProgress[i] >= column.size()) {
                    train.conditionsDone = true;
                    break;
                }
                SimCondition condition = column.get(train.columnProgress[i]);
                if (condition.tick(clock, tick, train.columnElapsed[i], train)) {
                    train.columnProgress[i]++;
                    train.columnElapsed[i] = 0;
                } else {
                    train.columnElapsed[i]++;
                }
            }
            if (!train.conditionsDone)
                return;
        }

        if (!gatesPass(train, entry, tick))
            return;

        depart(train, tick);
    }

    private boolean gatesPass(TrainState train, SimProgram.Entry entry, long tick) {
        for (SimCondition.Separation gate : train.departureGates) {
            String filter = gate.stationFilter() == null || gate.stationFilter().isBlank()
                    ? entry.filterText
                    : gate.stationFilter();
            long last = latestDeparture(gate.filter(), train.spec.name, entry, filter);
            if (last != Long.MIN_VALUE && last + gate.ticks() >= tick)
                return false;
        }
        return true;
    }

    private long latestDeparture(SimCondition.TrainFilter filter, String trainName,
                                 SimProgram.Entry entry, String stationFilter) {
        // A SAME_LINE/SAME_CATEGORY gate on a train with no section identity
        // can never match a departure (CRN: empty Optional line).
        if (filter == SimCondition.TrainFilter.SAME_LINE && entry.lineToken == null)
            return Long.MIN_VALUE;
        if (filter == SimCondition.TrainFilter.SAME_CATEGORY && entry.categoryToken == null)
            return Long.MIN_VALUE;
        Pattern pattern = patternCache.computeIfAbsent(stationFilter, SimGlob::compile);
        long latest = Long.MIN_VALUE;
        for (Map.Entry<String, StationHistory> stationEntry : departureHistory.entrySet()) {
            if (!pattern.matcher(stationEntry.getKey()).matches())
                continue;
            StationHistory history = stationEntry.getValue();
            long value = switch (filter) {
                case SAME_NAME -> history.byTrainName.getOrDefault(trainName, Long.MIN_VALUE);
                case SAME_LINE -> history.byLine.getOrDefault(entry.lineToken, Long.MIN_VALUE);
                case SAME_CATEGORY -> history.byCategory.getOrDefault(entry.categoryToken, Long.MIN_VALUE);
                case ANY -> history.lastAny;
            };
            latest = Math.max(latest, value);
        }
        return latest;
    }

    private void depart(TrainState train, long tick) {
        if (!train.departureGates.isEmpty() && !train.currentStationName.isEmpty()) {
            StationHistory history =
                    departureHistory.computeIfAbsent(train.currentStationName, k -> new StationHistory());
            history.lastAny = tick;
            history.byTrainName.put(train.spec.name, tick);
            SimProgram.Entry entry = train.spec.program.entries.get(train.currentEntry);
            if (entry.lineToken != null)
                history.byLine.put(entry.lineToken, tick);
            if (entry.categoryToken != null)
                history.byCategory.put(entry.categoryToken, tick);
        }

        if (!train.result.visits.isEmpty()) {
            SimResult.StationVisit last = train.result.visits.get(train.result.visits.size() - 1);
            if (last.departureTick() == -1)
                train.result.visits.set(train.result.visits.size() - 1,
                        new SimResult.StationVisit(last.entryIndex(), last.stationId(),
                                last.stationName(), last.arrivalTick(), tick));
        }
        event(SimResult.EventType.DEPARTURE, tick, train, train.currentEntry);

        train.departureGates.clear();
        train.conditionsDone = false;
        train.columnProgress = null;
        train.columnElapsed = null;
        train.currentEntry++;
        train.cooldown = 0;
        train.mode = TrainState.Mode.PRE_TRANSIT;
        train.holdingAtStation = true;
    }

    // ------------------------------------------------------------------
    // MOVING: Create's Navigation.tick, in order
    // ------------------------------------------------------------------

    private void tickMoving(TrainState train, long tick) {
        double acceleration = train.spec.acceleration;
        double brakingDistance = (train.speed * train.speed) / (2 * acceleration);
        double brakingNoFlicker = brakingDistance + 3 - (brakingDistance % 3);
        double preDeparture = train.holdingAtStation ? PRE_DEPARTURE_LOOKAHEAD : 0;
        double scanDistance = clamp(brakingNoFlicker, preDeparture, train.distanceToTarget);

        double signalStop = scanSignals(train, scanDistance, brakingNoFlicker);
        train.blockedEdge = signalStop >= 0 ? scanBlockedEdge : -1;

        // Signal wait bookkeeping (feeds the M4 conflict detectors).
        if (signalStop >= 0 && signalStop < 1 && train.speed < 1e-4) {
            if (!train.signalWaiting) {
                train.signalWaiting = true;
                train.signalWaitStart = tick;
                event(SimResult.EventType.SIGNAL_WAIT_START, tick, train, 0);
                train.result.waitBlocks.add(new long[] { tick, train.blockedEdge,
                        sectionHolder(train.blockedEdge, train.index) });
            }
        } else if (train.signalWaiting && signalStop < 0) {
            train.signalWaiting = false;
            event(SimResult.EventType.SIGNAL_WAIT_END, tick, train, tick - train.signalWaitStart);
        }

        // Create re-navigates a held train (Navigation keeps looking for a
        // way around, and buildPenalties already prices in blockages and
        // wait times) — without this, one permanently occupied platform
        // freezes its whole corridor and the network cascades into
        // gridlock instead of diverting like the real server does.
        if (train.signalWaiting && tick > train.signalWaitStart
                && (tick - train.signalWaitStart) % REPATH_WAIT_TICKS == 0)
            repath(train, tick);

        // SnR waypoints: the train rolls through without braking for the
        // target — only signals ahead still slow it down.
        boolean waypoint = isWaypointEntry(train);
        double targetDistance = waypoint
                ? (signalStop >= 0 ? signalStop + 0.25 : Double.MAX_VALUE)
                : (signalStop >= 0
                        ? Math.min(signalStop, train.distanceToTarget)
                        : train.distanceToTarget) + 0.25;

        // Don't leave the platform until the exit signal clears.
        if (targetDistance > ARRIVAL_EPS && train.holdingAtStation) {
            if (signalStop >= 0 && signalStop < PRE_DEPARTURE_LOOKAHEAD)
                return;
            train.holdingAtStation = false;
            train.currentStationId = null;
            train.currentStationName = "";
        }

        double topSpeed = train.spec.topSpeed;
        if (targetDistance - train.speed < ARRIVAL_EPS) {
            train.speed = Math.max(targetDistance, ARRIVAL_EPS);
        } else if (targetDistance < 10 && train.speed > topSpeed * (targetDistance / 10)) {
            train.speed += (topSpeed * (targetDistance / 10) - train.speed) * .5f;
        } else {
            double effectiveTop = topSpeed * train.throttle;
            double headSignCap = graph.edge(train.headEdge).signCap;
            if (headSignCap > 0)
                effectiveTop = Math.min(effectiveTop, headSignCap);
            double turnTop = Math.min(effectiveTop, train.spec.turnSpeed);

            double targetSpeed = targetDistance > brakingDistance ? effectiveTop : 0;

            double nextTurn = distanceToNextTurn(train, brakingNoFlicker);
            if (nextTurn >= 0) {
                double slowingDistance = brakingDistance - (turnTop * turnTop) / (2 * acceleration);
                targetSpeed = Math.min(targetSpeed, nextTurn > slowingDistance ? effectiveTop : turnTop);
            }
            double[] nextSignCap = distanceToLowerSignCap(train, brakingNoFlicker, effectiveTop);
            if (nextSignCap != null) {
                double slowingDistance = brakingDistance - (nextSignCap[1] * nextSignCap[1]) / (2 * acceleration);
                targetSpeed = Math.min(targetSpeed, nextSignCap[0] > slowingDistance ? effectiveTop : nextSignCap[1]);
            }

            if (train.speed < targetSpeed)
                train.speed = Math.min(train.speed + acceleration, targetSpeed);
            else if (train.speed > targetSpeed)
                train.speed = Math.max(train.speed - acceleration, targetSpeed);
        }

        double signalLimit = signalStop >= 0 ? Math.max(0, signalStop) : Double.MAX_VALUE;
        double step = Math.min(train.speed, Math.min(train.distanceToTarget, signalLimit));
        if (step > 0)
            advance(train, step);
        // Pinned at a red signal: the train halts and restarts from zero.
        if (signalStop >= 0 && signalStop - step <= ARRIVAL_EPS)
            train.speed = 0;

        if (train.distanceToTarget <= ARRIVAL_EPS && signalStop < 0) {
            if (waypoint)
                passWaypoint(train, tick);
            else
                arrive(train, tick);
        }
    }

    private boolean isWaypointEntry(TrainState train) {
        SimProgram program = train.spec.program;
        return program != null && train.currentEntry < program.entries.size()
                && program.entries.get(train.currentEntry).waypoint;
    }

    /**
     * Walks the route ahead looking for governed signals, mirroring Create's
     * signal scout: entry signals stop the scan when their section is taken;
     * chain signals collect whole chains and wait at the chain's first
     * signal; free sections within braking distance get reserved (per-tick
     * claims in train order).
     *
     * @return distance to the signal to stop at, or -1
     */
    /** The governed edge whose occupied section caused the last scan's stop. */
    private int scanBlockedEdge;

    private double scanSignals(TrainState train, double scanDistance, double brakingDistance) {
        scanBlockedEdge = -1;
        if (train.route == null)
            return -1;
        double distance = graph.edge(train.headEdge).length - train.headOffset;
        double chainStart = -1;
        List<Integer> chainSections = new ArrayList<>();
        double stop = -1;
        // Granted reservations sit within a chain's span of the train; a
        // bounded look past the braking scan is enough to keep them alive.
        int beyondScanBudget = 32;

        for (int i = train.routeIndex; i + 1 < train.route.length; i++) {
            if (distance >= train.distanceToTarget - 1e-6)
                break;
            boolean beyondScan = chainStart == -1 && distance > scanDistance;
            if (beyondScan && --beyondScanBudget < 0)
                break;

            SimEdge next = graph.edge(train.route[i + 1]);
            if (next.entrySignal != SimEdge.Signal.NONE) {
                int section = next.sectionId;
                if (beyondScan) {
                    // Committed reservations stay alive until traversed —
                    // Create's chain groups stay reserved for the granted
                    // train the whole way through, else a train crossing a
                    // long corridor loses its far platform to an opposing
                    // arrival and the two meet head-on. Only a contiguous
                    // owned run is refreshed; the first foreign signal ends
                    // the walk (nothing of ours can lie beyond it).
                    if (sectionReservedBy[section] == train.index)
                        claim(section, train);
                    else
                        break;
                    distance += next.length;
                    continue;
                }
                boolean occupied = occupiedByOther(section, train.index);
                boolean chain = next.entrySignal == SimEdge.Signal.CHAIN;

                if (chainStart == -1) {
                    if (chain) {
                        chainStart = distance;
                        chainSections.clear();
                        chainSections.add(section);
                    }
                    if (occupied) {
                        stop = distance;
                        if (scanBlockedEdge == -1)
                            scanBlockedEdge = next.id;
                        if (!chain)
                            return stop;
                    }
                    if (!occupied && !chain && distance < brakingDistance)
                        claim(section, train);
                } else {
                    chainSections.add(section);
                    if (occupied) {
                        stop = chainStart;
                        if (scanBlockedEdge == -1)
                            scanBlockedEdge = next.id;
                    }
                    if (!chain) {
                        if (stop == -1) {
                            for (int chained : chainSections)
                                claim(chained, train);
                            chainStart = -1;
                            chainSections.clear();
                        } else {
                            return stop;
                        }
                    }
                }
            }
            distance += next.length;
        }

        if (chainStart != -1 && stop == -1)
            for (int chained : chainSections)
                claim(chained, train);
        return stop;
    }

    /** Takes or refreshes a section claim; first come, first served. */
    private void claim(int section, TrainState train) {
        sectionReservedBy[section] = train.index;
        sectionClaimTick[section] = currentTick;
    }

    /**
     * Re-runs navigation for the current destination while the train is
     * held at a red signal. The wait bookkeeping survives the call: if the
     * fresh route clears the way, next tick's scan closes the wait window
     * naturally; if nothing better exists the search returns the same
     * route (or fails, keeping the old one) and the wait keeps aging
     * toward a conflict report.
     */
    private void repath(TrainState train, long tick) {
        SimProgram program = train.spec.program;
        if (program == null || train.currentEntry >= program.entries.size())
            return;
        SimProgram.Entry entry = program.entries.get(train.currentEntry);
        if (entry.kind != SimProgram.InstructionKind.DESTINATION)
            return;
        boolean waiting = train.signalWaiting;
        long waitStart = train.signalWaitStart;
        startNavigation(train, entry, tick);
        train.signalWaiting = waiting;
        train.signalWaitStart = waitStart;
    }

    /** Distance to the next turn region ahead (0 if inside one), or -1. */
    private double distanceToNextTurn(TrainState train, double lookahead) {
        SimEdge head = graph.edge(train.headEdge);
        if (head.inTurn(train.headOffset))
            return 0;
        double best = -1;
        for (double[] range : head.turnRanges)
            if (range[0] > train.headOffset) {
                double d = range[0] - train.headOffset;
                if (d <= lookahead && (best < 0 || d < best))
                    best = d;
            }
        if (train.route != null) {
            double base = head.length - train.headOffset;
            for (int i = train.routeIndex + 1; i < train.route.length && base <= lookahead; i++) {
                SimEdge edge = graph.edge(train.route[i]);
                for (double[] range : edge.turnRanges) {
                    double d = base + range[0];
                    if (d <= lookahead && (best < 0 || d < best))
                        best = d;
                }
                base += edge.length;
            }
        }
        return best;
    }

    /** Nearest upcoming {@code {distance, cap}} stricter than the current top. */
    private double[] distanceToLowerSignCap(TrainState train, double lookahead, double effectiveTop) {
        if (train.route == null)
            return null;
        double base = graph.edge(train.headEdge).length - train.headOffset;
        for (int i = train.routeIndex + 1; i < train.route.length && base <= lookahead; i++) {
            SimEdge edge = graph.edge(train.route[i]);
            if (edge.signCap > 0 && edge.signCap < effectiveTop)
                return new double[] { base, edge.signCap };
            base += edge.length;
        }
        return null;
    }

    /** Moves the head {@code step} blocks along the route, dragging the tail. */
    private void advance(TrainState train, double step) {
        double remaining = step;
        while (remaining > 1e-9) {
            SimEdge head = graph.edge(train.headEdge);
            double available = head.length - train.headOffset;
            double moved = Math.min(remaining, available);
            if (moved > 0) {
                train.headOffset += moved;
                remaining -= moved;
                double[] headSpan = train.occupied.peekLast();
                if (headSpan != null && (int) headSpan[0] == train.headEdge) {
                    headSpan[2] = train.headOffset;
                } else {
                    train.occupied.addLast(new double[] { train.headEdge,
                            train.headOffset - moved, train.headOffset });
                    train.occupiedVersion++;
                }
            }
            if (remaining <= 1e-9)
                break;
            if (train.route == null || train.routeIndex + 1 >= train.route.length)
                break;
            train.routeIndex++;
            train.headEdge = train.route[train.routeIndex];
            train.headOffset = 0;
            train.result.path.add(train.headEdge);
            train.occupied.addLast(new double[] { train.headEdge, 0, 0 });
            train.occupiedVersion++;
        }
        train.distanceToTarget -= step - remaining;
        trimTail(train);
    }

    private void trimTail(TrainState train) {
        double excess = train.occupiedLength() - train.spec.length;
        while (excess > 1e-9 && !train.occupied.isEmpty()) {
            double[] tail = train.occupied.peekFirst();
            double spanLength = tail[2] - tail[1];
            if (spanLength <= excess + 1e-9) {
                train.occupied.pollFirst();
                train.occupiedVersion++;
                excess -= spanLength;
            } else {
                tail[1] += excess;
                excess = 0;
            }
        }
    }

    /**
     * Passes a Steam 'n' Rails waypoint: a zero-dwell visit is recorded and
     * the next entry dispatches the same tick, keeping the current speed —
     * the momentary standstill this tick is the 1-tick quantization of
     * SnR's seamless roll-through.
     */
    private void passWaypoint(TrainState train, long tick) {
        train.route = null;
        train.distanceToTarget = 0;
        train.blockedEdge = -1;
        if (train.signalWaiting) {
            train.signalWaiting = false;
            event(SimResult.EventType.SIGNAL_WAIT_END, tick, train, tick - train.signalWaitStart);
        }
        train.result.visits.add(new SimResult.StationVisit(train.currentEntry, train.targetStation,
                train.targetStationName, tick, tick));
        event(SimResult.EventType.ARRIVAL, tick, train, train.currentEntry);
        event(SimResult.EventType.DEPARTURE, tick, train, train.currentEntry);
        sample(train, tick);
        train.currentEntry++;
        train.cooldown = 0;
        train.holdingAtStation = false;
        train.mode = TrainState.Mode.PRE_TRANSIT;
        tickPreTransit(train, tick);
    }

    private void arrive(TrainState train, long tick) {
        train.speed = 0;
        train.mode = TrainState.Mode.WAITING;
        train.route = null;
        train.distanceToTarget = 0;
        train.currentStationId = train.targetStation;
        train.currentStationName = train.targetStationName;
        train.arrivalTick = tick;
        train.holdingAtStation = true;
        train.blockedEdge = -1;
        if (train.signalWaiting) {
            train.signalWaiting = false;
            event(SimResult.EventType.SIGNAL_WAIT_END, tick, train, tick - train.signalWaitStart);
        }
        initColumns(train);
        train.result.visits.add(new SimResult.StationVisit(train.currentEntry, train.currentStationId,
                train.currentStationName, tick, -1));
        event(SimResult.EventType.ARRIVAL, tick, train, train.currentEntry);
        sample(train, tick);
    }

    /** Fills in station identity for trains that start mid-dwell. */
    private void resolveCurrentStation(TrainState train) {
        SimEdge head = graph.edge(train.headEdge);
        for (SimEdge.Station station : head.stations)
            if (Math.abs(station.offset() - train.headOffset) < 1.5) {
                train.currentStationId = station.id();
                train.currentStationName = station.name();
                return;
            }
    }

    private void sample(TrainState train, long tick) {
        List<SimResult.Sample> samples = train.result.samples;
        if (!samples.isEmpty()) {
            SimResult.Sample last = samples.get(samples.size() - 1);
            if (last.tick() == tick)
                return;
            if (last.edgeId() == train.headEdge && Math.abs(last.offset() - train.headOffset) < 1e-3
                    && Math.abs(last.speed() - train.speed) < 1e-4)
                return;
        }
        samples.add(new SimResult.Sample(tick, train.headEdge, (float) train.headOffset, (float) train.speed));
    }

    private void event(SimResult.EventType type, long tick, TrainState train, long data) {
        result.events.add(new SimResult.SimEvent(type, tick, train.index, train.headEdge,
                train.headOffset, data));
    }

    private static double clamp(double value, double min, double max) {
        if (max < min)
            return max;
        return Math.max(min, Math.min(max, value));
    }
}
