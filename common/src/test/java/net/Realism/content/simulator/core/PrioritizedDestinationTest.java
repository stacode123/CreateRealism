package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * CRN prioritized-destination selection (mirrors
 * {@code PrioritizedDestinationInstruction.start}): first reachable filter
 * wins by default; avoid-trains prefers a later filter over a busy station.
 */
class PrioritizedDestinationTest {

    @Test
    void firstReachableFilterWinsWithoutAvoidTrains() {
        assertEquals("P1", firstStopWith(List.of("P1", "P2"), false));
    }

    @Test
    void avoidTrainsFallsToTheFreeStation() {
        assertEquals("P2", firstStopWith(List.of("P1", "P2"), true));
    }

    @Test
    void unreachableFilterFallsThrough() {
        assertEquals("P2", firstStopWith(List.of("Nowhere *", "P2"), false));
    }

    /**
     * A line with platform P1 blocked by a static obstacle and P2 free; a
     * train runs one prioritized-destination entry and reports where it
     * actually stopped first.
     */
    private static String firstStopWith(List<String> filters, boolean avoidTrains) {
        LineFixture line = new LineFixture().nodes(0, 700);
        line.station("P1", 300);
        line.station("P2", 500);
        SimGraph graph = line.build();

        SimProgram.Entry entry = SimProgram.Entry.destinationPrioritized(filters, avoidTrains);
        SimProgram program = new SimProgram();
        program.cyclic = false;
        program.entries.add(entry);

        SimTrainSpec traveler = LineFixture.train("T", line, 50, program);
        SimTrainSpec obstacle = LineFixture.train("X", line, 300, null);
        SimResult result = LineFixture.engine(graph, List.of(traveler, obstacle), 1500).run();

        List<SimResult.StationVisit> visits = result.trains.get(0).visits;
        assertFalse(visits.isEmpty(), "the traveler never arrived anywhere");
        return visits.get(0).stationName();
    }
}
