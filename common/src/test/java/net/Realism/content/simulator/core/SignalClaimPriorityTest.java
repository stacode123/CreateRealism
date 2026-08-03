package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signal claims are first come, first served (Create's signal groups stay
 * reserved for the train that committed first). Two platforms merge into
 * one governed junction section; the train that starts approaching first
 * must cross first — even though the other train is processed earlier in
 * the per-tick train order (the phantom is always train 0). Re-deriving
 * reservations every tick let the later train steal the block, which is
 * exactly the field bug: a phantom departing one minute later beat the
 * train already committed to the junction.
 */
class SignalClaimPriorityTest {

    @Test
    void earlierClaimantCrossesTheJunctionFirst() {
        SimGraph graph = new SimGraph();
        SimVec plusX = new SimVec(1, 0, 0);
        graph.nodes.add(new SimNode(0, false, 0, new SimVec(-200, 0, 0))); // A platform start
        graph.nodes.add(new SimNode(1, false, 0, new SimVec(-100, 0, 0))); // B platform start
        graph.nodes.add(new SimNode(2, true, 0, new SimVec(0, 0, 0)));     // junction (signal)
        graph.nodes.add(new SimNode(3, true, 0, new SimVec(300, 0, 0)));   // exit boundary
        graph.nodes.add(new SimNode(4, false, 0, new SimVec(400, 0, 0)));  // EndA
        graph.nodes.add(new SimNode(5, false, 0, new SimVec(400, 0, 0)));  // EndB

        SimEdge platformA = new SimEdge(0, 0, 2, -1, 200, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge platformB = new SimEdge(1, 1, 2, -1, 100, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge junction = new SimEdge(2, 2, 3, -1, 300, SimEdge.Signal.ENTRY, 0,
                new double[0][], plusX, plusX, false);
        // Entry signals guard the exits too: the boundary at the junction's
        // far end may only be crossed toward a signal head.
        SimEdge exitA = new SimEdge(3, 3, 4, -1, 100, SimEdge.Signal.ENTRY, 0,
                new double[0][], plusX, plusX, false);
        SimEdge exitB = new SimEdge(4, 3, 5, -1, 100, SimEdge.Signal.ENTRY, 0,
                new double[0][], plusX, plusX, false);
        platformA.stations.add(new SimEdge.Station(UUID.randomUUID(), "PlatA", 50, true));
        platformB.stations.add(new SimEdge.Station(UUID.randomUUID(), "PlatB", 50, true));
        exitA.stations.add(new SimEdge.Station(UUID.randomUUID(), "EndA", 50, true));
        exitB.stations.add(new SimEdge.Station(UUID.randomUUID(), "EndB", 50, true));
        graph.edges.add(platformA);
        graph.edges.add(platformB);
        graph.edges.add(junction);
        graph.edges.add(exitA);
        graph.edges.add(exitB);
        graph.computeDerived();

        // B is train 0 (the phantom's slot) but departs later; A commits to
        // the junction first while still outside it.
        SimTrainSpec trainB = spec("B", 1, LineFixture.program(false,
                LineFixture.destination("PlatB", new SimCondition.Delay(180)),
                LineFixture.destination("EndB", new SimCondition.Delay(50))));
        SimTrainSpec trainA = spec("A", 0, LineFixture.program(false,
                LineFixture.destination("PlatA", new SimCondition.Delay(100)),
                LineFixture.destination("EndA", new SimCondition.Delay(50))));

        SimResult result = LineFixture.engine(graph, List.of(trainB, trainA), 3000).run();

        long aArrival = arrivalAt(result, 1, "EndA");
        long bArrival = arrivalAt(result, 0, "EndB");
        assertTrue(aArrival >= 0 && bArrival >= 0,
                "both trains must finish: A=" + aArrival + " B=" + bArrival);
        assertTrue(aArrival < bArrival,
                "the first claimant crosses first: A=" + aArrival + " B=" + bArrival);
        boolean bWaited = result.events.stream().anyMatch(event ->
                event.type() == SimResult.EventType.SIGNAL_WAIT_START && event.trainIndex() == 0);
        assertTrue(bWaited, "the later train is the one held at its signal");
    }

    private static SimTrainSpec spec(String name, int headEdge, SimProgram program) {
        SimTrainSpec spec = new SimTrainSpec(name, name, 16, 0.01, 1.0, 0.5, 1.0,
                program, headEdge, 50);
        spec.canReverse = false;
        spec.startWaiting = true;
        return spec;
    }

    private static long arrivalAt(SimResult result, int train, String station) {
        for (SimResult.StationVisit visit : result.trains.get(train).visits)
            if (station.equals(visit.stationName()))
                return visit.arrivalTick();
        return -1;
    }
}
