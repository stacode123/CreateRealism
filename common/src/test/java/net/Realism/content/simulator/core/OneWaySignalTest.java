package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A single-sided signal makes the track one-way (Create's
 * {@code SignalBoundary.canNavigateVia}): the boundary can only be crossed
 * toward a signal head. Trains must not route through it from the back.
 */
class OneWaySignalTest {

    @Test
    void boundaryTransitionsRequireASignalHead() {
        LineFixture line = new LineFixture()
                .nodes(0, 500, 1000)
                .forwardSignal(1, SimEdge.Signal.ENTRY);
        SimGraph graph = line.build();

        assertTrue(contains(graph.edge(0).nextEdges, 2),
                "crossing toward the signal head stays legal");
        assertFalse(contains(graph.edge(3).nextEdges, 1),
                "crossing the boundary from the back is one-way-blocked");
    }

    @Test
    void trainsCannotRouteThroughTheBackOfAOneWaySignal() {
        LineFixture line = new LineFixture()
                .nodes(0, 500, 1000)
                .forwardSignal(1, SimEdge.Signal.ENTRY);
        line.station("Back", 100);
        SimGraph graph = line.build();
        // "Back" approachable against the line direction, so the target
        // exists — only the route through the boundary is illegal.
        SimEdge backApproach = graph.edge(1);
        for (int i = 0; i < backApproach.stations.size(); i++) {
            SimEdge.Station station = backApproach.stations.get(i);
            backApproach.stations.set(i, new SimEdge.Station(
                    station.id(), station.name(), station.offset(), true));
        }

        SimTrainSpec wrongWay = new SimTrainSpec("W", "W", 16, 0.01, 1.0, 0.5, 1.0,
                LineFixture.program(false, LineFixture.destination("Back",
                        new SimCondition.Delay(100))),
                3, 400);
        wrongWay.canReverse = false;
        SimResult result = LineFixture.engine(graph, List.of(wrongWay), 1500).run();

        assertTrue(result.trains.get(0).visits.isEmpty(),
                "no route may exist through the one-way boundary");
        assertTrue(result.events.stream().anyMatch(event ->
                        event.type() == SimResult.EventType.PATH_FAILED),
                "the failed navigation is reported");
    }

    private static boolean contains(int[] array, int value) {
        for (int element : array)
            if (element == value)
                return true;
        return false;
    }
}
