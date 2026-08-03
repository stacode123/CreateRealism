package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-of-run wait chains resolve to the train actually causing the queue —
 * the field pattern where one train with a broken destination parks on the
 * running line and strands a whole corridor behind it, while every SECTION
 * record only names the immediate neighbour in front.
 */
class RootCauseTest {

    /**
     * A signalled line with a blocker parked at 700 (its destination never
     * resolves), a follower queued at the signal before it and a tail train
     * queued behind the follower. Specs: 0=follower, 1=tail, 2=blocker.
     */
    private SimResult run(String blockerDestination) {
        LineFixture line = new LineFixture().nodes(0, 300, 600, 1000);
        line.station("End", 900);
        line.station("Behind", 50);
        line.forwardSignal(1, SimEdge.Signal.ENTRY);
        line.forwardSignal(2, SimEdge.Signal.ENTRY);
        SimGraph graph = line.build();

        SimTrainSpec follower = LineFixture.train("Follower", line, 250,
                LineFixture.program(false, LineFixture.destination("End")));
        SimTrainSpec tail = LineFixture.train("Tail", line, 20,
                LineFixture.program(false, LineFixture.destination("End")));
        SimTrainSpec blocker = LineFixture.train("Blocker", line, 700,
                LineFixture.program(false, LineFixture.destination(blockerDestination)));
        return LineFixture.engine(graph, List.of(follower, tail, blocker), 4000).run();
    }

    @Test
    void queueBehindATypoDestinationNamesTheBlockerAsRoot() {
        SimResult result = run("Nowhere");
        assertEquals(1, result.rootCauses.size());
        SimResult.RootCause cause = result.rootCauses.get(0);
        assertEquals(2, cause.rootTrain(), "the blocker is the root, not the neighbour in front");
        assertEquals(SimResult.RootCauseKind.NO_MATCHING_STATION, cause.kind());
        assertEquals("Nowhere", cause.detail());
        assertEquals(Set.of(0, 1), Set.copyOf(cause.stranded()),
                "both queued trains resolve through the chain to the blocker");
        assertTrue(cause.sinceTick() > 0);
    }

    @Test
    void unreachableRealStationClassifiesAsNoPath() {
        SimResult result = run("Behind");
        assertEquals(1, result.rootCauses.size());
        SimResult.RootCause cause = result.rootCauses.get(0);
        assertEquals(SimResult.RootCauseKind.NO_PATH, cause.kind());
        assertEquals("Behind", cause.detail());
    }
}
