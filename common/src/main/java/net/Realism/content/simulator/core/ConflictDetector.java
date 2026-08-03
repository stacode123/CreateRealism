package net.Realism.content.simulator.core;

import java.util.*;

/**
 * The M4 conflict detectors (SPEC §4.5): section waits, deadlocks, headway
 * and platform overlaps. Strictly an observer — it reads engine state after
 * each tick and never writes anything the engine reads, so simulation
 * results (and the benchmark digest) are bit-identical with or without it.
 *
 * <p>Waits and headway are judged live each tick; deadlock candidates are
 * confirmed at run end (a real deadlock never resolves in-sim, so any cycle
 * whose members moved again was transient signal pressure, not a deadlock);
 * platform overlaps are computed from the finished visit windows.
 */
class ConflictDetector {

    /** How often the wait-for graph is checked for cycles. */
    private static final int DEADLOCK_CHECK_INTERVAL = 20;
    /** A train must have waited this long to join the wait-for graph. */
    private static final int DEADLOCK_MIN_WAIT = 100;

    private final SimGraph graph;
    private final List<TrainState> trains;
    private final int[] sectionReservedBy;
    private final long waitTicks;
    private final long headwayTicks;

    /** A merged conflict being accumulated during the run. */
    private static class Pending {
        SimConflict.Type type;
        long start;
        long end;
        int count = 1;
        int anchorEdge;
        double anchorOffset;
        String resourceName = "";
        final LinkedHashSet<Integer> members = new LinkedHashSet<>();
    }

    private final Map<String, Pending> merged = new LinkedHashMap<>();
    /** The SECTION record a currently-waiting train is extending, per train. */
    private final Pending[] activeWait;
    /** Wait-start tick of the episode each active record is tracking. */
    private final long[] activeWaitStart;

    /** A discovered wait-for cycle, kept until run end for confirmation. */
    private static class PendingDeadlock {
        int[] members;
        long[] waitStarts;
        int[] blockedEdges;
        long discoveredTick;
    }

    private final Map<String, PendingDeadlock> pendingDeadlocks = new LinkedHashMap<>();

    // Headway: last time each section was vacated, and by whom.
    private final long[] lastExitTick;
    private final int[] lastExitTrain;
    private final int[][] prevSections;
    /** {@code occupiedVersion} at last diff — unchanged means same sections. */
    private final int[] prevVersion;
    private final boolean[] sectionsInitialized;
    private int[] scratchSections = new int[16];

    /** Section → occupying trains, rebuilt at most once per tick. */
    private Map<Integer, List<Integer>> occupantsCache;
    private boolean occupantsCacheValid;

    ConflictDetector(SimGraph graph, List<TrainState> trains, int[] sectionReservedBy,
                     long waitTicks, long headwayTicks) {
        this.graph = graph;
        this.trains = trains;
        this.sectionReservedBy = sectionReservedBy;
        this.waitTicks = waitTicks;
        this.headwayTicks = headwayTicks;
        this.activeWait = new Pending[trains.size()];
        this.activeWaitStart = new long[trains.size()];
        this.lastExitTick = new long[graph.sectionCount()];
        this.lastExitTrain = new int[graph.sectionCount()];
        java.util.Arrays.fill(lastExitTrain, -1);
        this.prevSections = new int[trains.size()][];
        this.prevVersion = new int[trains.size()];
        this.sectionsInitialized = new boolean[trains.size()];
    }

    void tick(long tick) {
        occupantsCacheValid = false;
        tickWaits(tick);
        if (tick % DEADLOCK_CHECK_INTERVAL == 0)
            checkDeadlocks(tick);
        tickHeadway(tick);
    }

    // ------------------------------------------------------------------
    // Section waits
    // ------------------------------------------------------------------

    private void tickWaits(long tick) {
        if (waitTicks <= 0)
            return;
        for (TrainState train : trains) {
            Pending active = activeWait[train.index];
            if (active != null) {
                // Same uninterrupted wait: keep extending its window.
                if (train.signalWaiting && train.signalWaitStart == activeWaitStart[train.index]) {
                    active.end = tick;
                    continue;
                }
                activeWait[train.index] = null;
            }
            if (!train.signalWaiting || train.blockedEdge < 0
                    || tick - train.signalWaitStart != waitTicks)
                continue;
            int section = graph.edge(train.blockedEdge).sectionId;
            String key = "S" + train.index + ":" + section;
            Pending pending = merged.get(key);
            if (pending == null) {
                pending = newPending(SimConflict.Type.SECTION, train.signalWaitStart,
                        train.blockedEdge, 0);
                merged.put(key, pending);
            } else {
                pending.count++;
            }
            pending.end = tick;
            pending.members.add(train.index);
            for (int holder : holdersOf(section, train.index))
                pending.members.add(holder);
            activeWait[train.index] = pending;
            activeWaitStart[train.index] = train.signalWaitStart;
        }
    }

    // ------------------------------------------------------------------
    // Deadlocks
    // ------------------------------------------------------------------

    private void checkDeadlocks(long tick) {
        List<TrainState> candidates = new ArrayList<>();
        for (TrainState train : trains)
            if (train.signalWaiting && train.blockedEdge >= 0
                    && tick - train.signalWaitStart >= DEADLOCK_MIN_WAIT)
                candidates.add(train);
        // Re-validate earlier discoveries: if any member moved on, the cycle
        // was transient — drop it so a genuine re-formation can re-register.
        pendingDeadlocks.values().removeIf(pending -> !stillDeadlocked(pending));
        if (candidates.size() < 2)
            return;

        Map<Integer, int[]> waitFor = new HashMap<>();
        for (TrainState train : candidates) {
            List<Integer> holders = holdersOf(graph.edge(train.blockedEdge).sectionId, train.index);
            waitFor.put(train.index, holders.stream().mapToInt(Integer::intValue).toArray());
        }
        for (List<Integer> cycle : stronglyConnected(waitFor)) {
            int[] members = cycle.stream().mapToInt(Integer::intValue).sorted().toArray();
            String key = java.util.Arrays.toString(members);
            if (pendingDeadlocks.containsKey(key))
                continue;
            PendingDeadlock pending = new PendingDeadlock();
            pending.members = members;
            pending.waitStarts = new long[members.length];
            pending.blockedEdges = new int[members.length];
            for (int i = 0; i < members.length; i++) {
                TrainState member = trains.get(members[i]);
                pending.waitStarts[i] = member.signalWaitStart;
                pending.blockedEdges[i] = member.blockedEdge;
            }
            pending.discoveredTick = tick;
            pendingDeadlocks.put(key, pending);
        }
    }

    private boolean stillDeadlocked(PendingDeadlock pending) {
        for (int i = 0; i < pending.members.length; i++) {
            TrainState member = trains.get(pending.members[i]);
            if (!member.signalWaiting || member.signalWaitStart != pending.waitStarts[i])
                return false;
        }
        return true;
    }

    /** Tarjan SCC over the wait-for graph; returns components of size ≥ 2. */
    private List<List<Integer>> stronglyConnected(Map<Integer, int[]> waitFor) {
        List<List<Integer>> cycles = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>();
        Map<Integer, Integer> lowLink = new HashMap<>();
        LinkedHashSet<Integer> onStack = new LinkedHashSet<>();
        java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
        int[] counter = { 0 };
        for (int node : waitFor.keySet())
            if (!index.containsKey(node))
                tarjan(node, waitFor, index, lowLink, onStack, stack, counter, cycles);
        return cycles;
    }

    private void tarjan(int node, Map<Integer, int[]> waitFor, Map<Integer, Integer> index,
                        Map<Integer, Integer> lowLink, LinkedHashSet<Integer> onStack,
                        java.util.Deque<Integer> stack, int[] counter, List<List<Integer>> cycles) {
        index.put(node, counter[0]);
        lowLink.put(node, counter[0]);
        counter[0]++;
        stack.push(node);
        onStack.add(node);
        for (int next : waitFor.getOrDefault(node, new int[0])) {
            if (!waitFor.containsKey(next))
                continue; // waiting on a train that isn't itself blocked
            if (!index.containsKey(next)) {
                tarjan(next, waitFor, index, lowLink, onStack, stack, counter, cycles);
                lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(next)));
            } else if (onStack.contains(next)) {
                lowLink.put(node, Math.min(lowLink.get(node), index.get(next)));
            }
        }
        if (lowLink.get(node).intValue() == index.get(node).intValue()) {
            List<Integer> component = new ArrayList<>();
            int popped;
            do {
                popped = stack.pop();
                onStack.remove(popped);
                component.add(popped);
            } while (popped != node);
            if (component.size() >= 2)
                cycles.add(component);
        }
    }

    // ------------------------------------------------------------------
    // Headway
    // ------------------------------------------------------------------

    private void tickHeadway(long tick) {
        // Two phases so a section vacated and re-entered on the same tick is
        // judged against this tick's exit, whatever the train order.
        List<long[]> entries = null;
        for (TrainState train : trains) {
            if (train.mode == TrainState.Mode.OBSTACLE || train.mode == TrainState.Mode.PARKED)
                continue;
            // Fast path: the span edge set hasn't changed since last diff.
            if (sectionsInitialized[train.index]
                    && prevVersion[train.index] == train.occupiedVersion)
                continue;

            int count = 0;
            for (double[] span : train.occupied) {
                int section = graph.edge((int) span[0]).sectionId;
                boolean seen = false;
                for (int j = 0; j < count; j++)
                    if (scratchSections[j] == section) {
                        seen = true;
                        break;
                    }
                if (seen)
                    continue;
                if (count == scratchSections.length)
                    scratchSections = java.util.Arrays.copyOf(scratchSections, count * 2);
                scratchSections[count++] = section;
            }
            int[] current = java.util.Arrays.copyOf(scratchSections, count);
            int[] previous = prevSections[train.index];
            if (sectionsInitialized[train.index]) {
                for (int section : previous)
                    if (!contains(current, section)) {
                        lastExitTick[section] = tick;
                        lastExitTrain[section] = train.index;
                    }
                for (int section : current)
                    if (!contains(previous, section)) {
                        if (entries == null)
                            entries = new ArrayList<>();
                        entries.add(new long[] { section, train.index });
                    }
            }
            prevSections[train.index] = current;
            prevVersion[train.index] = train.occupiedVersion;
            sectionsInitialized[train.index] = true;
        }
        if (entries == null)
            return;
        for (long[] entry : entries) {
            int section = (int) entry[0];
            TrainState follower = trains.get((int) entry[1]);
            int leaderIndex = lastExitTrain[section];
            if (leaderIndex < 0 || leaderIndex == follower.index)
                continue;
            long gap = tick - lastExitTick[section];
            long threshold = Math.max(headwayTicks, separationBetween(follower, trains.get(leaderIndex)));
            if (threshold <= 0 || gap >= threshold)
                continue;
            String key = "H" + section + ":" + leaderIndex + ":" + follower.index;
            Pending pending = merged.get(key);
            if (pending == null) {
                pending = newPending(SimConflict.Type.HEADWAY, lastExitTick[section],
                        follower.headEdge, 0);
                pending.end = tick;
                pending.members.add(follower.index);
                pending.members.add(leaderIndex);
                merged.put(key, pending);
            } else {
                pending.count++;
            }
        }
    }

    /**
     * The strictest CRN separation the follower declares that matches the
     * leader (by name, line or category — SPEC: violations are judged
     * against the stricter of the CRN condition and the threshold). Station
     * filters are ignored here: separation intends route-wide spacing.
     */
    private long separationBetween(TrainState follower, TrainState leader) {
        SimProgram program = follower.spec.program;
        if (program == null || follower.currentEntry >= program.entries.size())
            return 0;
        SimProgram.Entry entry = program.entries.get(follower.currentEntry);
        SimProgram.Entry leaderEntry = leader.spec.program != null
                && leader.currentEntry < leader.spec.program.entries.size()
                ? leader.spec.program.entries.get(leader.currentEntry)
                : null;
        long strictest = 0;
        for (List<SimCondition> column : entry.columns)
            for (SimCondition condition : column) {
                if (!(condition instanceof SimCondition.Separation separation))
                    continue;
                boolean applies = switch (separation.filter()) {
                    case ANY -> true;
                    case SAME_NAME -> follower.spec.name.equals(leader.spec.name);
                    case SAME_LINE -> entry.lineToken != null && leaderEntry != null
                            && entry.lineToken.equals(leaderEntry.lineToken);
                    case SAME_CATEGORY -> entry.categoryToken != null && leaderEntry != null
                            && entry.categoryToken.equals(leaderEntry.categoryToken);
                };
                if (applies)
                    strictest = Math.max(strictest, separation.ticks());
            }
        return strictest;
    }

    // ------------------------------------------------------------------
    // Platform overlaps + finalization
    // ------------------------------------------------------------------

    void finish(SimResult result, long endTick) {
        detectPlatformOverlaps(endTick);

        // Confirm deadlocks: every member still stuck in the same wait.
        List<Pending> deadlocks = new ArrayList<>();
        for (PendingDeadlock pending : pendingDeadlocks.values()) {
            if (!stillDeadlocked(pending))
                continue;
            long lockedSince = 0;
            for (long waitStart : pending.waitStarts)
                lockedSince = Math.max(lockedSince, waitStart);
            Pending conflict = newPending(SimConflict.Type.DEADLOCK, lockedSince,
                    pending.blockedEdges[0], 0);
            conflict.end = endTick;
            for (int member : pending.members)
                conflict.members.add(member);
            deadlocks.add(conflict);
        }

        // A deadlocked train's long signal wait is the deadlock, not a
        // separate section conflict — drop the redundant record.
        List<Pending> emitted = new ArrayList<>();
        for (Pending pending : merged.values()) {
            boolean shadowed = false;
            if (pending.type == SimConflict.Type.SECTION)
                for (Pending deadlock : deadlocks)
                    if (deadlock.members.contains(pending.members.iterator().next())
                            && pending.end >= deadlock.start) {
                        shadowed = true;
                        break;
                    }
            if (!shadowed)
                emitted.add(pending);
        }
        emitted.addAll(deadlocks);

        emitted.sort(java.util.Comparator.comparingLong((Pending pending) -> pending.start)
                .thenComparingInt(pending -> pending.type.ordinal())
                .thenComparingInt(pending -> pending.members.iterator().next()));
        for (Pending pending : emitted) {
            boolean nonDeterministic = false;
            for (int member : pending.members) {
                SimTrainSpec spec = trains.get(member).spec;
                if (spec.liveAnchored || spec.program == null)
                    nonDeterministic = true;
            }
            SimVec position = positionOn(pending.anchorEdge, pending.anchorOffset);
            result.conflicts.add(new SimConflict(pending.type, pending.start, pending.end,
                    pending.count, position, graph.nodes.get(graph.edge(pending.anchorEdge).from).dimension(),
                    pending.resourceName, List.copyOf(pending.members), nonDeterministic,
                    pending.anchorEdge, pending.anchorOffset));
        }

        analyzeRootCauses(result, endTick, deadlocks);
    }

    private SimVec positionOn(int edgeId, double offset) {
        SimEdge edge = graph.edge(edgeId);
        SimVec from = graph.nodes.get(edge.from).position();
        SimVec to = graph.nodes.get(edge.to).position();
        double fraction = edge.length > 0 ? offset / edge.length : 0;
        return new SimVec(from.x() + (to.x() - from.x()) * fraction,
                from.y() + (to.y() - from.y()) * fraction,
                from.z() + (to.z() - from.z()) * fraction);
    }

    // ------------------------------------------------------------------
    // Root causes: who is ultimately blocking each end-of-run wait chain
    // ------------------------------------------------------------------

    /**
     * Resolves every train still held at a signal when the run ended to the
     * far end of its wait chain — the train that isn't waiting on anyone and
     * won't move again (no route, parked, obstacle, deadlock, held by its
     * schedule). Immediate SECTION records only ever name the neighbour in
     * front; this names the train actually causing the queue.
     */
    private void analyzeRootCauses(SimResult result, long endTick, List<Pending> deadlocks) {
        long gate = waitTicks > 0 ? waitTicks : 1200;
        Map<Integer, Integer> waitsOn = new LinkedHashMap<>();
        for (TrainState train : trains)
            if (train.signalWaiting && train.blockedEdge >= 0) {
                List<Integer> holders = holdersOf(graph.edge(train.blockedEdge).sectionId, train.index);
                waitsOn.put(train.index, holders.isEmpty() ? -1 : holders.get(0));
            }
        if (waitsOn.isEmpty())
            return;

        // Confirmed deadlock cycles, keyed by a representative member.
        Map<Integer, LinkedHashSet<Integer>> cycleByMember = new HashMap<>();
        for (Pending deadlock : deadlocks)
            for (int member : deadlock.members)
                cycleByMember.put(member, deadlock.members);

        Map<Integer, List<Integer>> strandedByRoot = new LinkedHashMap<>();
        for (int origin : waitsOn.keySet()) {
            int root = resolveRoot(origin, waitsOn, cycleByMember);
            if (root < 0 || root == origin)
                continue;
            LinkedHashSet<Integer> cycle = cycleByMember.get(root);
            if (cycle != null && cycle.contains(origin))
                continue; // a cycle member isn't "stuck behind" its own deadlock
            strandedByRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(origin);
        }

        List<SimResult.RootCause> causes = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> group : strandedByRoot.entrySet()) {
            TrainState root = trains.get(group.getKey());
            List<Integer> stranded = group.getValue();
            boolean significant = false;
            for (int index : stranded)
                if (endTick - trains.get(index).signalWaitStart >= gate)
                    significant = true;
            if (!significant)
                continue;
            stranded.sort(java.util.Comparator.comparingLong(
                    index -> trains.get(index).signalWaitStart));
            long since = trains.get(stranded.get(0)).signalWaitStart;
            LinkedHashSet<Integer> cycle = cycleByMember.get(root.index);
            SimResult.RootCauseKind kind;
            String detail = "";
            if (cycle != null) {
                kind = SimResult.RootCauseKind.DEADLOCK;
                List<String> others = new ArrayList<>();
                for (int member : cycle)
                    if (member != root.index)
                        others.add(trains.get(member).spec.name);
                detail = String.join(", ", others);
            } else if (root.signalWaiting) {
                kind = SimResult.RootCauseKind.SIGNAL_UNKNOWN;
            } else {
                switch (root.mode) {
                    case OBSTACLE -> kind = SimResult.RootCauseKind.OBSTACLE;
                    case PARKED -> kind = SimResult.RootCauseKind.FINISHED_PARKED;
                    case WAITING -> {
                        kind = SimResult.RootCauseKind.SCHEDULE_HOLD;
                        detail = root.currentStationName;
                    }
                    default -> {
                        SimProgram.Entry entry = failedEntryOf(root);
                        if (entry != null) {
                            detail = entry.filterText;
                            kind = anyStationExists(entry)
                                    ? SimResult.RootCauseKind.NO_PATH
                                    : SimResult.RootCauseKind.NO_MATCHING_STATION;
                        } else {
                            kind = SimResult.RootCauseKind.SCHEDULE_HOLD;
                            detail = root.currentStationName;
                        }
                    }
                }
            }
            causes.add(new SimResult.RootCause(root.index, kind, detail,
                    List.copyOf(stranded), since, positionOn(root.headEdge, root.headOffset),
                    graph.nodes.get(graph.edge(root.headEdge).from).dimension(),
                    root.headEdge, root.headOffset));
        }
        causes.sort(java.util.Comparator
                .comparingInt((SimResult.RootCause cause) -> -cause.stranded().size())
                .thenComparingLong(SimResult.RootCause::sinceTick)
                .thenComparingInt(SimResult.RootCause::rootTrain));
        result.rootCauses.addAll(causes);
    }

    /**
     * Follows first-holder links from {@code origin} until a train that isn't
     * itself signal-waiting. Returns -1 when the chain ends in still-moving
     * traffic (a transient queue, not a permanent block); a revisited train
     * (an unconfirmed cycle) or an unidentifiable holder roots the chain at
     * the deepest waiting train.
     */
    private int resolveRoot(int origin, Map<Integer, Integer> waitsOn,
                            Map<Integer, LinkedHashSet<Integer>> cycleByMember) {
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        int current = origin;
        while (true) {
            if (cycleByMember.containsKey(current))
                return cycleByMember.get(current).iterator().next();
            if (!seen.add(current))
                return current;
            Integer next = waitsOn.get(current);
            if (next == null) {
                TrainState state = trains.get(current);
                return state.mode == TrainState.Mode.MOVING ? -1 : current;
            }
            if (next < 0)
                return current;
            current = next;
        }
    }

    /** The destination entry the root train keeps failing to path toward. */
    private SimProgram.Entry failedEntryOf(TrainState root) {
        SimProgram program = root.spec.program;
        if (root.lastPathFailEntry < 0 || program == null
                || root.lastPathFailEntry >= program.entries.size())
            return null;
        SimProgram.Entry entry = program.entries.get(root.lastPathFailEntry);
        return entry.kind == SimProgram.InstructionKind.DESTINATION ? entry : null;
    }

    private boolean anyStationExists(SimProgram.Entry entry) {
        for (java.util.regex.Pattern pattern : entry.patterns)
            if (!graph.findStations(pattern).isEmpty())
                return true;
        return false;
    }

    private void detectPlatformOverlaps(long endTick) {
        // Dwell windows per platform; a null station id (unresolved snapshot
        // dwell) falls back to grouping by name.
        Map<String, List<long[]>> byStation = new LinkedHashMap<>();
        Map<String, long[]> stationAnchor = new HashMap<>();
        Map<String, String> stationName = new HashMap<>();
        for (TrainState train : trains)
            for (SimResult.StationVisit visit : train.result.visits) {
                String key = visit.stationId() != null
                        ? visit.stationId().toString()
                        : "n:" + visit.stationName();
                long departure = visit.departureTick() >= 0 ? visit.departureTick() : endTick;
                byStation.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new long[] { visit.arrivalTick(), departure, train.index });
                stationName.putIfAbsent(key, visit.stationName());
            }
        for (SimEdge edge : graph.edges)
            for (SimEdge.Station station : edge.stations)
                stationAnchor.putIfAbsent(station.id().toString(),
                        new long[] { edge.id, (long) station.offset() });

        for (Map.Entry<String, List<long[]>> entry : byStation.entrySet()) {
            List<long[]> dwells = entry.getValue();
            if (dwells.size() < 2)
                continue;
            dwells.sort(java.util.Comparator.comparingLong((long[] dwell) -> dwell[0])
                    .thenComparingLong(dwell -> dwell[2]));
            for (int i = 0; i < dwells.size(); i++)
                for (int j = i + 1; j < dwells.size(); j++) {
                    long[] first = dwells.get(i);
                    long[] second = dwells.get(j);
                    if (second[0] >= first[1])
                        break; // sorted by arrival: no later dwell overlaps either
                    if (first[2] == second[2])
                        continue;
                    long[] anchor = stationAnchor.get(entry.getKey());
                    String key = "P" + entry.getKey() + ":" + Math.min(first[2], second[2])
                            + ":" + Math.max(first[2], second[2]);
                    Pending pending = merged.get(key);
                    if (pending == null) {
                        pending = newPending(SimConflict.Type.PLATFORM, second[0],
                                anchor != null ? (int) anchor[0] : trains.get((int) first[2]).headEdge,
                                anchor != null ? anchor[1] : 0);
                        pending.end = Math.min(first[1], second[1]);
                        pending.resourceName = stationName.getOrDefault(entry.getKey(), "");
                        pending.members.add((int) first[2]);
                        pending.members.add((int) second[2]);
                        merged.put(key, pending);
                    } else {
                        pending.count++;
                    }
                }
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private Pending newPending(SimConflict.Type type, long start, int anchorEdge, double anchorOffset) {
        Pending pending = new Pending();
        pending.type = type;
        pending.start = start;
        pending.end = start;
        pending.anchorEdge = anchorEdge;
        pending.anchorOffset = anchorOffset;
        return pending;
    }

    /** Trains occupying or reserving any section linked to {@code section}. */
    private List<Integer> holdersOf(int section, int excludeTrain) {
        Map<Integer, List<Integer>> occupants = occupantsBySection();
        LinkedHashSet<Integer> holders = new LinkedHashSet<>();
        for (int linked : graph.sectionClosure(section)) {
            if (sectionReservedBy[linked] >= 0 && sectionReservedBy[linked] != excludeTrain)
                holders.add(sectionReservedBy[linked]);
            for (int occupant : occupants.getOrDefault(linked, List.of()))
                if (occupant != excludeTrain)
                    holders.add(occupant);
        }
        return new ArrayList<>(holders);
    }

    /** Cached within a tick: several waiters/checks share one build. */
    private Map<Integer, List<Integer>> occupantsBySection() {
        if (occupantsCacheValid)
            return occupantsCache;
        Map<Integer, List<Integer>> occupants = new HashMap<>();
        for (TrainState train : trains) {
            int last = -1;
            for (double[] span : train.occupied) {
                int section = graph.edge((int) span[0]).sectionId;
                if (section == last)
                    continue;
                last = section;
                List<Integer> here = occupants.computeIfAbsent(section, k -> new ArrayList<>(2));
                if (here.isEmpty() || here.get(here.size() - 1) != train.index)
                    here.add(train.index);
            }
        }
        occupantsCache = occupants;
        occupantsCacheValid = true;
        return occupants;
    }

    private static boolean contains(int[] array, int value) {
        for (int element : array)
            if (element == value)
                return true;
        return false;
    }
}
