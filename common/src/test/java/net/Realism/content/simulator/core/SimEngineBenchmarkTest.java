package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Large synthetic-network benchmark, sized like a busy public server:
 * parallel lines with diagonal crossovers (geometrically legal turns, so the
 * network behaves like real rail and Euclidean heuristics stay admissible),
 * block signals at every node, hundreds of trains including parked obstacles
 * and trains whose destination exists but is unreachable (the retry storm).
 *
 * <p>Too slow for CI, so it only runs when explicitly enabled:
 * <pre>./gradlew :common:test --tests '*Benchmark*' -Dsim.benchmark=true</pre>
 *
 * <p>Prints wall time, the engine's internal timing split, and a result
 * digest. The digest must stay identical across pure performance work — a
 * changed digest means an optimization altered simulation semantics.
 */
class SimEngineBenchmarkTest {

    private static final int COLS = 100;
    private static final int LINES = 15;
    private static final double COL_SPACING = 100;
    private static final double LINE_SPACING = 50;
    private static final int CROSSOVER_EVERY = 2;
    private static final int STATION_EVERY = 6;
    private static final int OBSTACLES = 300;
    private static final int SCHEDULED = 190;
    private static final int UNREACHABLE = 10;
    private static final long HORIZON = 20_000;

    @Test
    void largeNetworkBenchmark() {
        Assumptions.assumeTrue(Boolean.getBoolean("sim.benchmark"),
                "enable with -Dsim.benchmark=true");

        Mesh mesh = buildMesh();
        List<SimTrainSpec> specs = placeTrains(mesh);
        SimEngine engine = new SimEngine(mesh.graph(), specs, new SimClock(0, 1), HORIZON, 200, 0);

        long wallStart = System.nanoTime();
        SimResult result = engine.run();
        double wallSeconds = (System.nanoTime() - wallStart) / 1e9;

        SimResult.Stats stats = result.stats;
        int visits = result.trains.stream().mapToInt(train -> train.visits.size()).sum();
        System.out.printf("[sim-bench] graph: %d edges, %d sections | trains: %d (%d obstacles, %d scheduled, %d unreachable)%n",
                mesh.graph().edges.size(), mesh.graph().sectionCount(), specs.size(),
                OBSTACLES, SCHEDULED, UNREACHABLE);
        System.out.printf("[sim-bench] wall %.2fs for %d ticks (%.0f ticks/s)%n",
                wallSeconds, result.ticksSimulated, result.ticksSimulated / wallSeconds);
        System.out.printf("[sim-bench] init %.2fs | occupancy %.2fs | train loop %.2fs | pathfinding %.2fs (%d calls, %d failed, %d memoized)%n",
                stats.initNanos / 1e9, stats.occupancyNanos / 1e9, stats.trainLoopNanos / 1e9,
                stats.pathfindNanos / 1e9, stats.pathfindCalls, stats.pathfindFails,
                stats.pathfindMemoHits);
        System.out.printf("[sim-bench] memo size %d%n", stats.pathfindMemoSize);
        System.out.printf("[sim-bench] conflicts %.2fs for %d records%n",
                stats.conflictNanos / 1e9, result.conflicts.size());
        System.out.printf("[sim-bench] digest %016x | visits %d | events %d%n",
                digest(result), visits, result.events.size());
        org.junit.jupiter.api.Assertions.assertTrue(visits > 100,
                "benchmark network is broken - almost nothing arrived anywhere");
    }

    // ------------------------------------------------------------------
    // Network construction
    // ------------------------------------------------------------------

    record Mesh(SimGraph graph, int[][] horizontalForward, List<String> stationNames,
                        List<Integer> stationEdges, List<Integer> sidingEdges) {}

    static Mesh buildMesh() {
        SimGraph graph = new SimGraph();
        int[][] node = new int[LINES][COLS];
        SimVec[][] position = new SimVec[LINES][COLS];
        for (int line = 0; line < LINES; line++)
            for (int col = 0; col < COLS; col++) {
                position[line][col] = new SimVec(col * COL_SPACING, 0, line * LINE_SPACING);
                node[line][col] = addNode(graph, position[line][col]);
            }

        int[][] horizontalForward = new int[LINES][COLS - 1];
        List<String> stationNames = new ArrayList<>();
        List<Integer> stationEdges = new ArrayList<>();
        for (int line = 0; line < LINES; line++)
            for (int col = 0; col < COLS - 1; col++) {
                int forward = addPair(graph, node[line][col], node[line][col + 1],
                        position[line][col], position[line][col + 1]);
                horizontalForward[line][col] = forward;
                if (col % STATION_EVERY == 3) {
                    String name = "St " + line + "-" + col;
                    addStation(graph, forward, name);
                    stationNames.add(name);
                    stationEdges.add(forward);
                }
            }
        // Diagonal crossovers between neighboring lines. dot(direction, +X) =
        // 100/111.8 ≈ 0.894 > 7/8, so line changes are turn-legal like real
        // crossovers.
        for (int line = 0; line + 1 < LINES; line++)
            for (int col = 0; col < COLS - 1; col += CROSSOVER_EVERY) {
                addPair(graph, node[line][col], node[line + 1][col + 1],
                        position[line][col], position[line + 1][col + 1]);
                addPair(graph, node[line + 1][col], node[line][col + 1],
                        position[line + 1][col], position[line][col + 1]);
            }

        // Depot sidings hanging off the mesh at shallow (turn-legal) angles;
        // parked obstacles live here like on a real server, occupying their
        // own signal section instead of paralyzing a main line.
        List<Integer> sidingEdges = new ArrayList<>();
        for (int i = 0; i < OBSTACLES; i++) {
            int line = i % LINES;
            int col = 1 + (i * 7) % (COLS - 2);
            SimVec from = position[line][col];
            SimVec stub = new SimVec(from.x() + 100, 0, from.z() + (i % 2 == 0 ? 30 : -30));
            sidingEdges.add(addPair(graph, node[line][col], addNode(graph, stub), from, stub));
        }

        // Islands: stations whose pattern matches but that no path reaches —
        // every navigation attempt explores the full graph and fails.
        for (int k = 0; k < UNREACHABLE; k++) {
            SimVec a = new SimVec(k * 400, 0, -5000);
            SimVec b = new SimVec(k * 400 + 200, 0, -5000);
            int forward = addPair(graph, addNode(graph, a), addNode(graph, b), a, b);
            addStation(graph, forward, "Island " + k);
        }

        graph.computeDerived();
        return new Mesh(graph, horizontalForward, stationNames, stationEdges, sidingEdges);
    }

    private static int addNode(SimGraph graph, SimVec position) {
        int id = graph.nodes.size();
        graph.nodes.add(new SimNode(id, true, 0, position));
        return id;
    }

    /** Bidirectional edge pair with block signals; returns the forward id. */
    private static int addPair(SimGraph graph, int from, int to, SimVec fromPos, SimVec toPos) {
        SimVec delta = new SimVec(toPos.x() - fromPos.x(), toPos.y() - fromPos.y(),
                toPos.z() - fromPos.z());
        double length = Math.sqrt(delta.dot(delta));
        SimVec direction = delta.normalize();
        int forward = graph.edges.size();
        graph.edges.add(new SimEdge(forward, from, to, forward + 1, length, SimEdge.Signal.ENTRY,
                0, new double[0][], direction, direction, false));
        graph.edges.add(new SimEdge(forward + 1, to, from, forward, length, SimEdge.Signal.ENTRY,
                0, new double[0][], direction.negate(), direction.negate(), false));
        return forward;
    }

    /**
     * A platform approachable from both directions (same id/name), like the
     * per-direction platform pairs real networks build — otherwise westbound
     * journeys could never terminate, since the two directed edge families
     * only connect through reversals.
     */
    private static void addStation(SimGraph graph, int forwardEdge, String name) {
        SimEdge forward = graph.edges.get(forwardEdge);
        SimEdge backward = graph.edges.get(forward.oppositeId);
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        double offset = forward.length / 2;
        forward.stations.add(new SimEdge.Station(id, name, offset, true));
        backward.stations.add(new SimEdge.Station(id, name, forward.length - offset, true));
    }

    // ------------------------------------------------------------------
    // Train placement (seeded — the benchmark is fully deterministic)
    // ------------------------------------------------------------------

    /**
     * Corridor-style services: each train runs stations on its own line ±1,
     * columns at least 12 apart. With crossovers every 2 columns the
     * directional reachability cone allows 1 line-change per 2 columns, so
     * every leg (east- or westbound via reversal) is reachable by
     * construction — like schedules people actually build, and no train
     * strands on a geometrically impossible destination.
     */
    private static List<SimTrainSpec> placeTrains(Mesh mesh) {
        Random random = new Random(20260730);
        List<SimTrainSpec> specs = new ArrayList<>();
        for (int i = 0; i < OBSTACLES; i++)
            specs.add(new SimTrainSpec("obstacle-" + i, "Obstacle " + i,
                    16 + random.nextInt(4) * 8, 0.0075, 1.0, 0.5, 1.0, null,
                    mesh.sidingEdges().get(i), 60));
        for (int i = 0; i < SCHEDULED; i++) {
            int line = random.nextInt(LINES);
            List<Integer> cols = new ArrayList<>();
            while (cols.size() < 3) {
                int candidate = 3 + STATION_EVERY * random.nextInt((COLS - 5) / STATION_EVERY + 1);
                if (cols.stream().allMatch(chosen -> Math.abs(chosen - candidate) >= 12))
                    cols.add(candidate);
            }
            SimProgram.Entry[] entries = new SimProgram.Entry[3];
            for (int s = 0; s < 3; s++) {
                int stopLine = Math.max(0, Math.min(LINES - 1, line + random.nextInt(3) - 1));
                entries[s] = LineFixture.destination("St " + stopLine + "-" + cols.get(s),
                        new SimCondition.Delay(200));
            }
            int startCol = Math.max(1, cols.get(0) - 2 - random.nextInt(12));
            if (startCol % STATION_EVERY == 3)
                startCol--;
            SimTrainSpec spec = new SimTrainSpec("train-" + i, "Train " + i,
                    16 + random.nextInt(4) * 8, 0.0075, 1.0, 0.5, 1.0,
                    LineFixture.program(true, entries),
                    mesh.horizontalForward()[line][startCol], 30);
            spec.canReverse = true;
            specs.add(spec);
        }
        for (int i = 0; i < UNREACHABLE; i++) {
            SimTrainSpec spec = new SimTrainSpec("seeker-" + i, "Seeker " + i, 16,
                    0.0075, 1.0, 0.5, 1.0,
                    LineFixture.program(true,
                            LineFixture.destination("Island *", new SimCondition.Delay(100))),
                    mesh.horizontalForward()[i % LINES][1 + i], 30);
            spec.canReverse = true;
            specs.add(spec);
        }
        return specs;
    }

    // ------------------------------------------------------------------
    // Result digest
    // ------------------------------------------------------------------

    private static long digest(SimResult result) {
        long h = 1469598103934665603L;
        for (SimResult.TrainResult train : result.trains) {
            h = mix(h, train.name.hashCode());
            h = mix(h, train.endState.hashCode());
            for (SimResult.StationVisit visit : train.visits) {
                h = mix(h, visit.entryIndex());
                h = mix(h, visit.stationName().hashCode());
                h = mix(h, visit.arrivalTick());
                h = mix(h, visit.departureTick());
            }
        }
        for (SimResult.SimEvent event : result.events) {
            h = mix(h, event.type().ordinal());
            h = mix(h, event.tick());
            h = mix(h, event.trainIndex());
        }
        return mix(h, result.ticksSimulated);
    }

    private static long mix(long h, long value) {
        h ^= value;
        return h * 1099511628211L;
    }
}
