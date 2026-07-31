package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimEngineTest {

    /** Accelerate to top speed, cruise, brake into the platform, dwell, park. */
    @Test
    void travelsDwellsAndParks() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("A", 10);
        line.station("B", 900);
        SimGraph graph = line.build();

        SimProgram program = LineFixture.program(false,
                LineFixture.destination("B", new SimCondition.Delay(100)));
        SimTrainSpec train = LineFixture.train("t1", line, 10, program);

        SimResult result = LineFixture.engine(graph, List.of(train), 5000).run();

        SimResult.TrainResult trainResult = result.trains.get(0);
        assertEquals("PARKED", trainResult.endState);
        assertEquals(1, trainResult.visits.size());
        SimResult.StationVisit visit = trainResult.visits.get(0);
        assertEquals("B", visit.stationName());
        // 890 blocks at top speed 1.0 with accel 0.01: ~890 + ramp losses.
        assertTrue(visit.arrivalTick() > 890 && visit.arrivalTick() < 1050,
                "arrival at " + visit.arrivalTick());
        long dwell = visit.departureTick() - visit.arrivalTick();
        assertTrue(dwell >= 100 && dwell <= 110, "dwell " + dwell);
        for (SimResult.Sample sample : trainResult.samples)
            assertTrue(sample.speed() <= 1.3f, "speed " + sample.speed());
    }

    /** An entry signal guards an occupied section: the train stops and waits. */
    @Test
    void entrySignalHoldsTrainOutsideOccupiedSection() {
        LineFixture line = new LineFixture().nodes(0, 500, 1000);
        line.station("A", 10);
        line.station("B", 900);
        line.forwardSignal(1, SimEdge.Signal.ENTRY);
        SimGraph graph = line.build();

        SimTrainSpec obstacle = new SimTrainSpec("obstacle", "obstacle", 16, 0.01, 1.0, 0.5, 1.0,
                null, line.forwardEdgeAt(600), line.offsetOn(line.forwardEdgeAt(600), 600));
        SimProgram program = LineFixture.program(false,
                LineFixture.destination("B", new SimCondition.Delay(20)));
        SimTrainSpec train = LineFixture.train("t1", line, 10, program);

        SimResult result = LineFixture.engine(graph, List.of(obstacle, train), 4000).run();

        SimResult.TrainResult trainResult = result.trains.get(1);
        assertTrue(trainResult.visits.isEmpty(), "train should never reach B");
        assertTrue(result.events.stream()
                        .anyMatch(e -> e.type() == SimResult.EventType.SIGNAL_WAIT_START && e.trainIndex() == 1),
                "expected a signal wait");
        for (SimResult.Sample sample : trainResult.samples)
            assertTrue(line.xOf(sample) <= 500.01, "crossed the signal: x=" + line.xOf(sample));
    }

    /**
     * A chain signal must hold the train at the chain, not between chain and
     * the blocked entry signal beyond it — even though that middle section is
     * free.
     */
    @Test
    void chainSignalReservesAtomically() {
        LineFixture line = new LineFixture().nodes(0, 400, 500, 1000);
        line.station("A", 10);
        line.station("B", 950);
        line.forwardSignal(1, SimEdge.Signal.CHAIN);
        line.forwardSignal(2, SimEdge.Signal.ENTRY);
        SimGraph graph = line.build();

        SimTrainSpec obstacle = new SimTrainSpec("obstacle", "obstacle", 16, 0.01, 1.0, 0.5, 1.0,
                null, line.forwardEdgeAt(700), line.offsetOn(line.forwardEdgeAt(700), 700));
        SimProgram program = LineFixture.program(false,
                LineFixture.destination("B", new SimCondition.Delay(20)));
        SimTrainSpec train = LineFixture.train("t1", line, 10, program);

        SimResult result = LineFixture.engine(graph, List.of(obstacle, train), 4000).run();

        SimResult.TrainResult trainResult = result.trains.get(1);
        assertTrue(trainResult.visits.isEmpty());
        for (SimResult.Sample sample : trainResult.samples)
            assertTrue(line.xOf(sample) <= 400.01,
                    "entered the chain section: x=" + line.xOf(sample));
    }

    /** Create's time-of-day window: hour 7 = day-time 1000 with rotation 24000. */
    @Test
    void timeOfDayReleasesAtTargetTime() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("A", 10);
        line.station("B", 900);
        SimGraph graph = line.build();

        SimProgram program = LineFixture.program(false,
                LineFixture.destination("A", new SimCondition.TimeOfDay(7, 0, 24000)),
                LineFixture.destination("B", new SimCondition.Delay(50)));
        SimTrainSpec train = LineFixture.train("t1", line, 10, program);
        train.startWaiting = true;

        SimResult result = LineFixture.engine(graph, List.of(train), 5000).run();

        SimResult.TrainResult trainResult = result.trains.get(0);
        assertFalse(trainResult.visits.isEmpty());
        long departure = trainResult.visits.get(0).departureTick();
        assertTrue(departure >= 1000 && departure <= 1045, "departed at " + departure);
    }

    /** CRN train separation: the second train departs >= Ticks after the first. */
    @Test
    void trainSeparationSpacesDepartures() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("A", 10);
        line.station("A", 30);
        line.station("B", 900);
        SimGraph graph = line.build();

        SimProgram program1 = LineFixture.program(false,
                LineFixture.destination("A",
                        new SimCondition.Separation(200, SimCondition.TrainFilter.ANY, "")),
                LineFixture.destination("B", new SimCondition.Delay(10)));
        SimProgram program2 = LineFixture.program(false,
                LineFixture.destination("A",
                        new SimCondition.Separation(200, SimCondition.TrainFilter.ANY, "")),
                LineFixture.destination("B", new SimCondition.Delay(10)));

        SimTrainSpec first = LineFixture.train("t1", line, 30, program1);
        first.startWaiting = true;
        SimTrainSpec second = LineFixture.train("t2", line, 10, program2);
        second.startWaiting = true;

        SimResult result = LineFixture.engine(graph, List.of(first, second), 6000).run();

        long departure1 = result.trains.get(0).visits.get(0).departureTick();
        long departure2 = result.trains.get(1).visits.get(0).departureTick();
        assertTrue(departure1 >= 0, "first train departed");
        assertTrue(departure2 - departure1 >= 200,
                "separation was " + (departure2 - departure1));
        assertTrue(departure2 - departure1 <= 215,
                "separation overshot: " + (departure2 - departure1));
    }

    /** The core promise: identical inputs, identical results. */
    @Test
    void identicalInputsProduceIdenticalResults() {
        SimResult first = runSeparationScenario();
        SimResult second = runSeparationScenario();

        assertEquals(first.ticksSimulated, second.ticksSimulated);
        assertEquals(first.events, second.events);
        assertEquals(first.trains.size(), second.trains.size());
        for (int i = 0; i < first.trains.size(); i++) {
            assertEquals(first.trains.get(i).visits, second.trains.get(i).visits);
            assertEquals(first.trains.get(i).samples, second.trains.get(i).samples);
        }
    }

    private SimResult runSeparationScenario() {
        LineFixture line = new LineFixture().nodes(0, 500, 1000);
        line.station("A", 10);
        line.station("A", 30);
        line.station("B", 900);
        line.forwardSignal(1, SimEdge.Signal.ENTRY);
        SimGraph graph = line.build();

        SimProgram program1 = LineFixture.program(true,
                LineFixture.destination("A",
                        new SimCondition.Separation(100, SimCondition.TrainFilter.ANY, "")),
                LineFixture.destination("B", new SimCondition.Delay(40)));
        SimProgram program2 = LineFixture.program(true,
                LineFixture.destination("A",
                        new SimCondition.Separation(100, SimCondition.TrainFilter.ANY, "")),
                LineFixture.destination("B", new SimCondition.Delay(40)));

        SimTrainSpec first = LineFixture.train("t1", line, 30, program1);
        first.startWaiting = true;
        SimTrainSpec second = LineFixture.train("t2", line, 10, program2);
        second.startWaiting = true;

        return LineFixture.engine(graph, List.of(first, second), 3000).run();
    }

    /** Sections linked by a diamond crossing share occupancy transitively. */
    @Test
    void crossingLinksSectionOccupancy() {
        LineFixture line = new LineFixture().nodes(0, 500, 1000);
        line.forwardSignal(1, SimEdge.Signal.ENTRY);
        SimGraph graph = line.build();

        java.util.UUID crossing = java.util.UUID.nameUUIDFromBytes("x".getBytes());
        graph.edge(0).crossings.add(new SimEdge.Crossing(crossing, 250));
        graph.edge(2).crossings.add(new SimEdge.Crossing(crossing, 250));
        graph.computeDerived();

        int sectionA = graph.edge(0).sectionId;
        int sectionB = graph.edge(2).sectionId;
        assertTrue(sectionA != sectionB);
        assertEquals(2, graph.sectionClosure(sectionA).length);
        assertEquals(2, graph.sectionClosure(sectionB).length);
    }

    /**
     * A parked idle train on the direct track: pathfinding must divert over
     * the (longer) passing loop, mirroring Create's navigation penalties.
     */
    @Test
    void routesAroundIdleTrainViaLoop() {
        LineFixture line = new LineFixture().nodes(0, 100, 200, 300);
        line.station("A", 10);
        line.station("B", 290);
        SimGraph graph = line.build();
        LineFixture.addLoop(graph, 1, 2, 150);

        int directEdge = line.forwardEdgeAt(150);
        SimTrainSpec obstacle = new SimTrainSpec("obstacle", "obstacle", 16, 0.01, 1.0, 0.5, 1.0,
                null, directEdge, line.offsetOn(directEdge, 150));
        SimProgram program = LineFixture.program(false,
                LineFixture.destination("B", new SimCondition.Delay(20)));
        SimTrainSpec train = LineFixture.train("t1", line, 10, program);

        SimResult result = LineFixture.engine(graph, List.of(obstacle, train), 4000).run();

        SimResult.TrainResult trainResult = result.trains.get(1);
        assertEquals(1, trainResult.visits.size(), "train should reach B via the loop");
        for (SimResult.Sample sample : trainResult.samples)
            assertTrue(sample.edgeId() != directEdge,
                    "train drove through the parked train instead of the loop");
    }

    /**
     * Vertically stacked conditions are ONE sequential column in Create's
     * UI: wait 60s, then wait for the next 8:10am. Arriving after 8:10 with
     * a daily rotation means departing tomorrow.
     */
    @Test
    void sequentialDelayThenTimeOfDayWaitsForNextOccurrence() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("A", 10);
        line.station("B", 900);
        SimGraph graph = line.build();

        SimProgram.Entry entryA = SimProgram.Entry.destination("A");
        entryA.columns.add(List.of(new SimCondition.Delay(1200),
                new SimCondition.TimeOfDay(8, 10, 24000)));
        SimProgram program = LineFixture.program(false, entryA,
                LineFixture.destination("B", new SimCondition.Delay(50)));

        SimTrainSpec train = LineFixture.train("t1", line, 10, program);
        train.startWaiting = true;

        // Sim starts at 08:00 (day time 2000); 8:10 = day time 2167.
        SimEngine engine = new SimEngine(graph, List.of(train), new SimClock(2000, 1),
                30000, 20, 0);
        SimResult result = engine.run();

        long departure = result.trains.get(0).visits.get(0).departureTick();
        assertTrue(departure >= 24160 && departure <= 24215,
                "expected next-day 8:10 departure, got " + departure);
    }

    @Test
    void globMatchesLikeCreate() {
        assertTrue(SimGlob.compile("Central*").matcher("Central Station 1").matches());
        assertTrue(SimGlob.compile("*").matcher("anything").matches());
        assertTrue(SimGlob.compile("A*B").matcher("A middle B").matches());
        assertFalse(SimGlob.compile("Central").matcher("Central 2").matches());
        assertFalse(SimGlob.compile("Dep[o]t*").matcher("Depot 1").matches());
        assertNotNull(SimGlob.compile("weird[(regex*"));
    }

    /**
     * SAME_LINE separation only gates against departures of the same CRN
     * line: a same-line follower waits out the spacing while a train of a
     * different line departs freely in between.
     */
    @Test
    void sameLineSeparationIgnoresOtherLines() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("A", 10);
        line.station("A", 30);
        line.station("A", 50);
        line.station("B", 900);
        SimGraph graph = line.build();

        SimProgram lineA1 = separationProgram(SimCondition.TrainFilter.SAME_LINE, "line-A");
        SimProgram lineA2 = separationProgram(SimCondition.TrainFilter.SAME_LINE, "line-A");
        SimProgram lineB = separationProgram(SimCondition.TrainFilter.SAME_LINE, "line-B");

        SimTrainSpec first = LineFixture.train("a1", line, 50, lineA1);
        first.startWaiting = true;
        SimTrainSpec other = LineFixture.train("b1", line, 30, lineB);
        other.startWaiting = true;
        SimTrainSpec second = LineFixture.train("a2", line, 10, lineA2);
        second.startWaiting = true;

        SimResult result = LineFixture.engine(graph, List.of(first, other, second), 8000).run();

        long departA1 = result.trains.get(0).visits.get(0).departureTick();
        long departB = result.trains.get(1).visits.get(0).departureTick();
        long departA2 = result.trains.get(2).visits.get(0).departureTick();
        assertTrue(departA1 >= 0 && departB >= 0 && departA2 >= 0, "all trains departed");
        assertTrue(departA2 - departA1 >= 400, "same-line follower spaced by the gate");
        assertTrue(departB - departA1 < 400, "different line departs inside the spacing window");
    }

    private static SimProgram separationProgram(SimCondition.TrainFilter filter, String lineToken) {
        SimProgram program = LineFixture.program(false,
                LineFixture.destination("A", new SimCondition.Separation(400, filter, "")),
                LineFixture.destination("B", new SimCondition.Delay(10)));
        for (SimProgram.Entry entry : program.entries)
            entry.lineToken = lineToken;
        return program;
    }
}
