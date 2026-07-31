package net.Realism.content.simulator.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds straight synthetic lines along +X for engine tests: nodes at given
 * positions, bidirectional edge pairs (forward id {@code 2i}, backward
 * {@code 2i+1}), optional signals at nodes and named platforms at positions.
 */
class LineFixture {

    private final List<Double> xs = new ArrayList<>();
    private final Set<Integer> signalNodes = new HashSet<>();
    private final Map<Integer, SimEdge.Signal> forwardSignals = new HashMap<>();
    private final Map<Integer, SimEdge.Signal> backwardSignals = new HashMap<>();

    private record Platform(String name, double x, UUID id) {}

    private final List<Platform> platforms = new ArrayList<>();

    LineFixture nodes(double... positions) {
        for (double x : positions)
            xs.add(x);
        return this;
    }

    /** Signal at node {@code index} governing travel in +X direction. */
    LineFixture forwardSignal(int index, SimEdge.Signal kind) {
        signalNodes.add(index);
        forwardSignals.put(index, kind);
        return this;
    }

    LineFixture backwardSignal(int index, SimEdge.Signal kind) {
        signalNodes.add(index);
        backwardSignals.put(index, kind);
        return this;
    }

    UUID station(String name, double x) {
        UUID id = UUID.nameUUIDFromBytes((name + "@" + x).getBytes());
        platforms.add(new Platform(name, x, id));
        return id;
    }

    SimGraph build() {
        SimGraph graph = new SimGraph();
        for (int i = 0; i < xs.size(); i++)
            graph.nodes.add(new SimNode(i, signalNodes.contains(i), 0, new SimVec(xs.get(i), 0, 0)));

        SimVec plusX = new SimVec(1, 0, 0);
        SimVec minusX = new SimVec(-1, 0, 0);
        for (int i = 0; i < xs.size() - 1; i++) {
            double length = xs.get(i + 1) - xs.get(i);
            SimEdge forward = new SimEdge(2 * i, i, i + 1, 2 * i + 1, length,
                    forwardSignals.getOrDefault(i, SimEdge.Signal.NONE), 0, new double[0][],
                    plusX, plusX, false);
            SimEdge backward = new SimEdge(2 * i + 1, i + 1, i, 2 * i, length,
                    backwardSignals.getOrDefault(i + 1, SimEdge.Signal.NONE), 0, new double[0][],
                    minusX, minusX, false);
            for (Platform platform : platforms) {
                if (platform.x() < xs.get(i) || platform.x() > xs.get(i + 1))
                    continue;
                double offset = platform.x() - xs.get(i);
                forward.stations.add(new SimEdge.Station(platform.id(), platform.name(), offset, true));
                backward.stations.add(new SimEdge.Station(platform.id(), platform.name(), length - offset, false));
            }
            graph.edges.add(forward);
            graph.edges.add(backward);
        }
        graph.computeDerived();
        return graph;
    }

    /** Adds a parallel bidirectional track between two nodes (a passing loop). */
    static int addLoop(SimGraph graph, int fromNode, int toNode, double length) {
        SimVec plusX = new SimVec(1, 0, 0);
        SimVec minusX = new SimVec(-1, 0, 0);
        int forwardId = graph.edges.size();
        graph.edges.add(new SimEdge(forwardId, fromNode, toNode, forwardId + 1, length,
                SimEdge.Signal.NONE, 0, new double[0][], plusX, plusX, false));
        graph.edges.add(new SimEdge(forwardId + 1, toNode, fromNode, forwardId, length,
                SimEdge.Signal.NONE, 0, new double[0][], minusX, minusX, false));
        graph.computeDerived();
        return forwardId;
    }

    /** Forward edge id of the segment containing {@code x}. */
    int forwardEdgeAt(double x) {
        for (int i = 0; i < xs.size() - 1; i++)
            if (x >= xs.get(i) && x <= xs.get(i + 1))
                return 2 * i;
        throw new IllegalArgumentException("x outside line: " + x);
    }

    double offsetOn(int forwardEdge, double x) {
        return x - xs.get(forwardEdge / 2);
    }

    /** World X of a sample, for assertions. */
    double xOf(SimResult.Sample sample) {
        int segment = sample.edgeId() / 2;
        double start = xs.get(segment);
        double end = xs.get(segment + 1);
        return sample.edgeId() % 2 == 0 ? start + sample.offset() : end - sample.offset();
    }

    static SimTrainSpec train(String id, LineFixture line, double headX, SimProgram program) {
        SimTrainSpec spec = new SimTrainSpec(id, id, 16, 0.01, 1.0, 0.5, 1.0, program,
                line.forwardEdgeAt(headX), line.offsetOn(line.forwardEdgeAt(headX), headX));
        spec.canReverse = false;
        return spec;
    }

    static SimProgram program(boolean cyclic, SimProgram.Entry... entries) {
        SimProgram program = new SimProgram();
        program.cyclic = cyclic;
        for (SimProgram.Entry entry : entries)
            program.entries.add(entry);
        return program;
    }

    static SimProgram.Entry destination(String filter, SimCondition... conditions) {
        SimProgram.Entry entry = SimProgram.Entry.destination(filter);
        List<SimCondition> column = new ArrayList<>(List.of(conditions));
        entry.columns.add(column);
        return entry;
    }

    static SimEngine engine(SimGraph graph, List<SimTrainSpec> specs, long horizon) {
        return new SimEngine(graph, specs, new SimClock(0, 1), horizon, 20, 0);
    }
}
