package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Steam 'n' Rails waypoints: the train routes through the waypoint station
 * with a zero-dwell visit and without braking for it, so it reaches the
 * following destination sooner than a train that stops there.
 */
class WaypointTest {

    @Test
    void waypointIsPassedWithoutDwellAndWithoutBraking() {
        SimResult waypointRun = run(true);
        SimResult stopRun = run(false);

        List<SimResult.StationVisit> visits = waypointRun.trains.get(0).visits;
        assertEquals(2, visits.size(), () -> "visits: " + visits);
        assertEquals("Mid", visits.get(0).stationName());
        assertEquals(visits.get(0).arrivalTick(), visits.get(0).departureTick(),
                "a waypoint visit has no dwell");
        assertEquals("End", visits.get(1).stationName());

        long waypointEnd = visits.get(1).arrivalTick();
        long stopEnd = stopRun.trains.get(0).visits.get(1).arrivalTick();
        assertTrue(waypointEnd < stopEnd,
                () -> "pass-through (" + waypointEnd + ") should beat stopping (" + stopEnd + ")");
    }

    private static SimResult run(boolean waypoint) {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("Mid", 400);
        line.station("End", 800);
        SimGraph graph = line.build();

        SimProgram program = new SimProgram();
        program.cyclic = false;
        program.entries.add(waypoint
                ? SimProgram.Entry.waypointDestination("Mid")
                : LineFixture.destination("Mid", new SimCondition.Delay(0)));
        program.entries.add(LineFixture.destination("End", new SimCondition.Delay(100)));

        SimTrainSpec spec = LineFixture.train("T", line, 50, program);
        return LineFixture.engine(graph, List.of(spec), 3000).run();
    }
}
