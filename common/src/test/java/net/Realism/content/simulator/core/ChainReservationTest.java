package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A chain-granted reservation must survive the whole crossing — Create's
 * signal groups stay reserved for the committed train until it passes
 * through. Fixture (a miniature of the Nordende Hbf 109 standoff from
 * field data): a long chain-signalled single-track corridor leads to a
 * terminal platform, and a second train approaches the same platform from
 * a branch. If the committed train's platform claim lapses mid-corridor,
 * the branch train snatches the platform, then needs the corridor the
 * first train stands in — a head-on deadlock neither can leave.
 */
class ChainReservationTest {

    @Test
    void chainCommittedTrainKeepsItsPlatformToTheEnd() {
        SimGraph graph = new SimGraph();
        graph.nodes.add(new SimNode(0, false, 0, new SimVec(0, 0, 0)));
        graph.nodes.add(new SimNode(1, true, 0, new SimVec(100, 0, 0)));
        graph.nodes.add(new SimNode(2, true, 0, new SimVec(700, 0, 0)));
        graph.nodes.add(new SimNode(3, true, 0, new SimVec(820, 0, 0)));
        graph.nodes.add(new SimNode(4, false, 0, new SimVec(1220, 0, 0)));
        SimVec plusX = new SimVec(1, 0, 0);
        SimVec minusX = new SimVec(-1, 0, 0);
        SimEdge approach = new SimEdge(0, 0, 1, 1, 100, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge approachBack = new SimEdge(1, 1, 0, 0, 100, SimEdge.Signal.ENTRY, 0,
                new double[0][], minusX, minusX, false);
        SimEdge corridor = new SimEdge(2, 1, 2, 3, 600, SimEdge.Signal.CHAIN, 0,
                new double[0][], plusX, plusX, false);
        SimEdge corridorBack = new SimEdge(3, 2, 1, 2, 600, SimEdge.Signal.ENTRY, 0,
                new double[0][], minusX, minusX, false);
        SimEdge platform = new SimEdge(4, 2, 3, 5, 120, SimEdge.Signal.ENTRY, 0,
                new double[0][], plusX, plusX, false);
        SimEdge platformBack = new SimEdge(5, 3, 2, 4, 120, SimEdge.Signal.ENTRY, 0,
                new double[0][], minusX, minusX, false);
        SimEdge branch = new SimEdge(6, 4, 3, -1, 400, SimEdge.Signal.NONE, 0,
                new double[0][], minusX, minusX, false);
        approachBack.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("e".getBytes()), "E", 50, true));
        platform.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("p".getBytes()), "P", 100, true));
        platformBack.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("q".getBytes()), "P2", 20, true));
        graph.edges.add(approach);
        graph.edges.add(approachBack);
        graph.edges.add(corridor);
        graph.edges.add(corridorBack);
        graph.edges.add(platform);
        graph.edges.add(platformBack);
        graph.edges.add(branch);
        graph.computeDerived();

        SimProgram toPlatform = LineFixture.program(false,
                LineFixture.destination("P", new SimCondition.Delay(200)));
        SimTrainSpec committed = new SimTrainSpec("A", "A", 16, 0.01, 1.0, 0.5, 1.0,
                toPlatform, 0, 10);
        committed.canReverse = false;
        // Arrives at the platform's far side well after A committed into
        // the corridor, then wants to leave through that same corridor.
        SimProgram viaPlatform = LineFixture.program(false,
                LineFixture.destination("P2", new SimCondition.Delay(300)),
                LineFixture.destination("E", new SimCondition.Delay(100)));
        SimTrainSpec opposing = new SimTrainSpec("B", "B", 16, 0.01, 1.0, 0.5, 1.0,
                viaPlatform, 6, 10);
        opposing.canReverse = false;

        SimResult result = LineFixture.engine(graph, List.of(committed, opposing), 4000).run();

        assertTrue(result.trains.get(0).visits.stream()
                        .anyMatch(visit -> "P".equals(visit.stationName())),
                "the chain-committed train must reach its reserved platform; visits: "
                        + result.trains.get(0).visits + " events: " + result.events.size());
    }
}
