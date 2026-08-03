package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SPEC §8's staged conflict scenarios, one per detector: single-track
 * contention (SECTION), opposing circulation (DEADLOCK), close following
 * under a CRN separation (HEADWAY), and two schedules into one station
 * (PLATFORM). Engine defaults: wait threshold 600 ticks, headway 200.
 */
class SimConflictTest {

    @Test
    void blockedSingleTrackReportsSectionConflict() {
        LineFixture line = new LineFixture()
                .nodes(0, 100, 300)
                .forwardSignal(1, SimEdge.Signal.ENTRY);
        line.station("End", 250);
        SimGraph graph = line.build();

        // A parks at End inside the governed section; B waits at the red
        // entry signal for the rest of the run.
        SimTrainSpec first = LineFixture.train("A", line, 150,
                LineFixture.program(false, LineFixture.destination("End",
                        new SimCondition.Delay(400))));
        SimTrainSpec second = LineFixture.train("B", line, 20,
                LineFixture.program(false, LineFixture.destination("End",
                        new SimCondition.Delay(400))));
        SimResult result = LineFixture.engine(graph, List.of(first, second), 2000).run();

        assertEquals(1, result.conflicts.size(), () -> "conflicts: " + result.conflicts);
        SimConflict conflict = result.conflicts.get(0);
        assertEquals(SimConflict.Type.SECTION, conflict.type());
        assertEquals(1, conflict.trains().get(0), "the waiter leads the train list");
        assertTrue(conflict.trains().contains(0), "the holder is involved");
        assertTrue(conflict.endTick() - conflict.startTick() >= 600);
        assertFalse(conflict.nonDeterministic());
    }

    @Test
    void opposingCirculationReportsDeadlock() {
        LineFixture line = new LineFixture()
                .nodes(0, 200, 400)
                .forwardSignal(1, SimEdge.Signal.ENTRY)
                .backwardSignal(1, SimEdge.Signal.ENTRY);
        line.station("EndA", 350);
        line.station("EndB", 50);
        SimGraph graph = line.build();
        approachBothWays(graph);

        // A eastbound in the left section, B westbound in the right one —
        // each needs the section the other stands in.
        SimTrainSpec eastbound = LineFixture.train("A", line, 100,
                LineFixture.program(false, LineFixture.destination("EndA",
                        new SimCondition.Delay(100))));
        SimTrainSpec westbound = backwardTrain("B", line, graph, 300,
                LineFixture.program(false, LineFixture.destination("EndB",
                        new SimCondition.Delay(100))));
        SimResult result = LineFixture.engine(graph, List.of(eastbound, westbound), 2000).run();

        // The two long signal waits are the deadlock, not separate records.
        assertEquals(1, result.conflicts.size(), () -> "conflicts: " + result.conflicts);
        SimConflict conflict = result.conflicts.get(0);
        assertEquals(SimConflict.Type.DEADLOCK, conflict.type());
        assertEquals(List.of(0, 1), conflict.trains());
    }

    @Test
    void closeFollowingViolatesCrnSeparation() {
        // Short governed sections the leader clears well before the
        // follower arrives, so the follower is never actually held at a
        // red — the gaps stay pure headway. The follower ends at Mid,
        // short of the section the leader parks in.
        LineFixture line = new LineFixture()
                .nodes(0, 500, 600, 700, 1500)
                .forwardSignal(1, SimEdge.Signal.ENTRY)
                .forwardSignal(2, SimEdge.Signal.ENTRY)
                .forwardSignal(3, SimEdge.Signal.ENTRY);
        line.station("Mid", 650);
        line.station("End", 1450);
        SimGraph graph = line.build();

        // B trails A by ~330 ticks: over the flat 200-tick threshold but far
        // under its own 1200-tick separation condition — only the
        // CRN-stricter rule can flag this.
        SimTrainSpec leader = LineFixture.train("A", line, 480,
                LineFixture.program(false, LineFixture.destination("End",
                        new SimCondition.Delay(100))));
        SimTrainSpec follower = LineFixture.train("B", line, 30,
                LineFixture.program(false, LineFixture.destination("Mid",
                        new SimCondition.Separation(1200, SimCondition.TrainFilter.ANY, ""))));
        SimResult result = LineFixture.engine(graph, List.of(leader, follower), 3000).run();

        assertFalse(result.conflicts.isEmpty(), "expected headway conflicts");
        for (SimConflict conflict : result.conflicts) {
            assertEquals(SimConflict.Type.HEADWAY, conflict.type(),
                    () -> "unexpected conflict: " + conflict);
            assertEquals(List.of(1, 0), conflict.trains(), "follower first, then leader");
            assertTrue(conflict.endTick() - conflict.startTick() > 200,
                    "the gap only violates the separation condition");
        }
    }

    @Test
    void overlappingDwellsReportPlatformConflict() {
        LineFixture line = new LineFixture().nodes(0, 400);
        line.station("Stop", 300);
        SimGraph graph = line.build();

        SimTrainSpec first = LineFixture.train("A", line, 100,
                LineFixture.program(false, LineFixture.destination("Stop",
                        new SimCondition.Delay(1000))));
        SimTrainSpec second = LineFixture.train("B", line, 20,
                LineFixture.program(false, LineFixture.destination("Stop",
                        new SimCondition.Delay(1000))));
        second.liveAnchored = true;
        SimResult result = LineFixture.engine(graph, List.of(first, second), 1500).run();

        assertEquals(1, result.conflicts.size(), () -> "conflicts: " + result.conflicts);
        SimConflict conflict = result.conflicts.get(0);
        assertEquals(SimConflict.Type.PLATFORM, conflict.type());
        assertEquals("Stop", conflict.resourceName());
        assertTrue(conflict.trains().contains(0) && conflict.trains().contains(1));
        assertTrue(conflict.nonDeterministic(), "an anchored train makes the conflict non-deterministic");
    }

    /** Platforms become approachable from both directions (island platform). */
    private static void approachBothWays(SimGraph graph) {
        for (SimEdge edge : graph.edges)
            for (int i = 0; i < edge.stations.size(); i++) {
                SimEdge.Station station = edge.stations.get(i);
                edge.stations.set(i, new SimEdge.Station(station.id(), station.name(),
                        station.offset(), true));
            }
    }

    /** A train facing −X: head on the backward edge at world {@code headX}. */
    private static SimTrainSpec backwardTrain(String id, LineFixture line, SimGraph graph,
                                              double headX, SimProgram program) {
        int forwardEdge = line.forwardEdgeAt(headX);
        SimEdge edge = graph.edge(forwardEdge);
        SimTrainSpec spec = new SimTrainSpec(id, id, 16, 0.01, 1.0, 0.5, 1.0, program,
                forwardEdge + 1, edge.length - line.offsetOn(forwardEdge, headX));
        spec.canReverse = false;
        return spec;
    }
}
