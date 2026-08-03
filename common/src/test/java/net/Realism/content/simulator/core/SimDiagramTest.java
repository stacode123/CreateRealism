package net.Realism.content.simulator.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The time-distance diagram builder: corridor construction from the
 * reference train's traversal, logical-station folding (platform grouping),
 * dwell/wait point synthesis, projection of other trains (including
 * opposing direction and parallel tracks), and segment breaks where a
 * train's run leaves the corridor.
 */
class SimDiagramTest {

    private static final int MAX_LINES = 40;
    private static final int MAX_POINTS = 300;

    private static SimDiagram build(SimGraph graph, SimResult result, List<SimTrainSpec> specs) {
        return SimDiagram.build(graph, result, specs, 0, MAX_LINES, MAX_POINTS,
                Map.of(), Set.of(), Set.of());
    }

    @Test
    void corridorCoversRouteWithStationsAndDwells() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("A", 100);
        line.station("B", 600);
        line.station("C", 900);
        SimGraph graph = line.build();

        SimProgram forward = LineFixture.program(false,
                LineFixture.destination("A", new SimCondition.Delay(200)),
                LineFixture.destination("C", new SimCondition.Delay(100)));
        SimTrainSpec spec = LineFixture.train("T", line, 50, forward);
        List<SimTrainSpec> specs = List.of(spec);
        SimResult result = LineFixture.engine(graph, specs, 5000).run();
        SimDiagram diagram = build(graph, result, specs);

        assertEquals(1000, diagram.corridorLength, 1e-6);
        assertEquals(List.of("A", "B", "C"),
                diagram.stations.stream().map(SimDiagram.StationMark::name).toList());
        assertEquals(100, diagram.stations.get(0).pos(), 1e-6);
        assertEquals(900, diagram.stations.get(2).pos(), 1e-6);

        SimDiagram.Line trainLine = diagram.lines.get(0);
        assertEquals(0, trainLine.train());
        assertEquals(1, trainLine.segments().size());
        List<SimDiagram.Point> points = trainLine.segments().get(0);
        long monotonic = 0;
        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i).tick() > points.get(i - 1).tick(), "ticks strictly increase");
            assertTrue(points.get(i).pos() >= points.get(i - 1).pos() - 1e-6, "run never goes backwards");
            monotonic++;
        }
        assertTrue(monotonic > 0);
        // The 200-tick dwell at A survives as an exact horizontal stretch.
        boolean dwellAtA = false;
        for (int i = 1; i < points.size(); i++)
            if (Math.abs(points.get(i).pos() - 100) < 1e-6
                    && Math.abs(points.get(i - 1).pos() - 100) < 1e-6
                    && points.get(i).tick() - points.get(i - 1).tick() >= 200)
                dwellAtA = true;
        assertTrue(dwellAtA, "dwell at A should be a horizontal segment: " + points);
        assertEquals(900, points.get(points.size() - 1).pos(), 1.0);
    }

    @Test
    void opposingTrainDescendsOnTheSameAxis() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("End", 900);
        line.station("Back", 100);
        SimGraph graph = line.build();

        // "Back" must be approachable against the line's forward direction
        // for the opposing train to stop there.
        SimEdge backApproach = graph.edge(line.forwardEdgeAt(100) + 1);
        for (int i = 0; i < backApproach.stations.size(); i++) {
            SimEdge.Station station = backApproach.stations.get(i);
            if (station.name().equals("Back"))
                backApproach.stations.set(i, new SimEdge.Station(
                        station.id(), station.name(), station.offset(), true));
        }

        SimTrainSpec forward = LineFixture.train("F", line, 50, LineFixture.program(false,
                LineFixture.destination("End", new SimCondition.Delay(100))));
        // Same track, opposite direction: head on the backward edge.
        int forwardEdge = line.forwardEdgeAt(950);
        SimTrainSpec backward = new SimTrainSpec("O", "O", 16, 0.01, 1.0, 0.5, 1.0,
                LineFixture.program(false, LineFixture.destination("Back", new SimCondition.Delay(100))),
                forwardEdge + 1, graph.edge(forwardEdge).length - line.offsetOn(forwardEdge, 950));
        backward.canReverse = false;

        List<SimTrainSpec> specs = List.of(forward, backward);
        SimResult result = LineFixture.engine(graph, specs, 5000).run();
        SimDiagram diagram = build(graph, result, specs);

        SimDiagram.Line opposing = diagram.lines.stream()
                .filter(candidate -> candidate.train() == 1).findFirst().orElseThrow();
        List<SimDiagram.Point> points = opposing.segments().get(0);
        assertTrue(points.get(0).pos() > points.get(points.size() - 1).pos(),
                "opposing traffic descends: " + points);
        assertEquals(100, points.get(points.size() - 1).pos(), 1.0);
    }

    @Test
    void offCorridorTravelBreaksTheLine() {
        LineFixture line = new LineFixture().nodes(0, 500, 1000);
        line.station("Start", 100);
        line.station("End", 900);
        SimGraph graph = line.build();

        // The corridor train only ever travels the second segment.
        SimTrainSpec corridor = LineFixture.train("T", line, 600, LineFixture.program(false,
                LineFixture.destination("End", new SimCondition.Delay(100))));
        // The other train crosses both; only its second half projects.
        SimTrainSpec crossing = LineFixture.train("U", line, 100, LineFixture.program(false,
                LineFixture.destination("End", new SimCondition.Delay(100))));

        List<SimTrainSpec> specs = List.of(corridor, crossing);
        SimResult result = LineFixture.engine(graph, specs, 5000).run();
        SimDiagram diagram = build(graph, result, specs);

        assertEquals(500, diagram.corridorLength, 1e-6);
        assertEquals(List.of("End"),
                diagram.stations.stream().map(SimDiagram.StationMark::name).toList());
        assertEquals(400, diagram.stations.get(0).pos(), 1e-6);
        assertTrue(Double.isNaN(diagram.project(line.forwardEdgeAt(100), 100)),
                "first segment is off-corridor");

        SimDiagram.Line crossingLine = diagram.lines.stream()
                .filter(candidate -> candidate.train() == 1).findFirst().orElseThrow();
        for (List<SimDiagram.Point> segment : crossingLine.segments())
            for (SimDiagram.Point point : segment) {
                assertTrue(point.pos() >= -1e-6 && point.pos() <= 500 + 1e-6,
                        "projected points stay inside the corridor: " + point);
                assertTrue(point.tick() > 0, "the crossing train enters the corridor later");
            }
        assertFalse(crossingLine.segments().isEmpty());
    }

    /**
     * A one-way double-track line W→K / K→W with direction-specific
     * platforms, the structure that broke the first corridor design.
     */
    private static SimGraph doubleTrack(String[] outboundNames, String[] returnNames) {
        SimGraph graph = new SimGraph();
        graph.nodes.add(new SimNode(0, false, 0, new SimVec(0, 0, 0)));
        graph.nodes.add(new SimNode(1, false, 0, new SimVec(1000, 0, 0)));
        SimVec plusX = new SimVec(1, 0, 0);
        // Both tangents +X so the continuation through each end's crossover
        // is turn-legal; the tracks are one-way (no opposite edges).
        SimEdge outbound = new SimEdge(0, 0, 1, -1, 1000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge back = new SimEdge(1, 1, 0, -1, 1000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        double[] offsets = { 50, 500, 950 };
        for (int i = 0; i < 3; i++) {
            outbound.stations.add(new SimEdge.Station(
                    UUID.nameUUIDFromBytes(outboundNames[i].getBytes()), outboundNames[i],
                    offsets[i], true));
            back.stations.add(new SimEdge.Station(
                    UUID.nameUUIDFromBytes(returnNames[i].getBytes()), returnNames[i],
                    offsets[i], true));
        }
        graph.edges.add(outbound);
        graph.edges.add(back);
        graph.computeDerived();
        return graph;
    }

    private static SimResult runDoubleTrack(SimGraph graph, List<SimTrainSpec> specs) {
        return LineFixture.engine(graph, specs, 8000).run();
    }

    @Test
    void doubleTrackReturnFoldsOntoTheOutboundTrack() {
        SimGraph graph = doubleTrack(
                new String[] { "Warszawa 1", "Radom 1", "Kraków 1" },
                new String[] { "Kraków 2", "Radom 2", "Warszawa 2" });
        SimProgram program = LineFixture.program(false,
                LineFixture.destination("Kraków*", new SimCondition.Delay(200)),
                LineFixture.destination("Warszawa*", new SimCondition.Delay(100)));
        SimTrainSpec spec = new SimTrainSpec("T", "T", 16, 0.01, 1.0, 0.5, 1.0, program, 0, 50);
        spec.canReverse = false;
        List<SimTrainSpec> specs = List.of(spec);
        SimDiagram diagram = build(graph, runDoubleTrack(graph, specs), specs);

        // One logical station per platform pair, at the outbound position.
        assertEquals(List.of("Warszawa", "Radom", "Kraków"),
                diagram.stations.stream().map(SimDiagram.StationMark::name).toList());
        assertEquals(50, diagram.stations.get(0).pos(), 1e-6);
        assertEquals(500, diagram.stations.get(1).pos(), 1e-6);
        assertEquals(950, diagram.stations.get(2).pos(), 1e-6);
        // The return track maps onto the same span, mirrored.
        assertEquals(500, diagram.project(1, 500), 1e-6);
        assertTrue(diagram.project(1, 60) > diagram.project(1, 940),
                "the return track descends the axis");

        // The out-and-back run rises to Kraków and comes back down.
        List<SimDiagram.Point> points = diagram.lines.get(0).segments().stream()
                .flatMap(List::stream).toList();
        double maxPos = points.stream().mapToDouble(SimDiagram.Point::pos).max().orElseThrow();
        assertEquals(950, maxPos, 5.0);
        assertEquals(50, points.get(points.size() - 1).pos(), 5.0);
    }

    @Test
    void stationTagsGroupPlatformsWithUnrelatedNames() {
        SimGraph graph = doubleTrack(
                new String[] { "Wschodnia", "Centrum", "Lotnisko" },
                new String[] { "Balice", "Middle", "Wilenska" });
        Map<String, String> tags = Map.of(
                "Wschodnia", "Warszawa", "Wilenska", "Warszawa",
                "Centrum", "Radom", "Middle", "Radom",
                "Lotnisko", "Kraków", "Balice", "Kraków");
        SimProgram program = LineFixture.program(false,
                LineFixture.destination("Lotnisko", new SimCondition.Delay(200)),
                LineFixture.destination("Wilenska", new SimCondition.Delay(100)));
        SimTrainSpec spec = new SimTrainSpec("T", "T", 16, 0.01, 1.0, 0.5, 1.0, program, 0, 50);
        spec.canReverse = false;
        List<SimTrainSpec> specs = List.of(spec);
        SimDiagram diagram = SimDiagram.build(graph, runDoubleTrack(graph, specs), specs,
                0, MAX_LINES, MAX_POINTS, tags, Set.of(), Set.of());

        assertEquals(List.of("Warszawa", "Radom", "Kraków"),
                diagram.stations.stream().map(SimDiagram.StationMark::name).toList());
        assertEquals(500, diagram.project(1, 500), 1e-6);
    }

    /**
     * Station blocks on the last piece of track: the train dwells nose-in
     * at a terminal stub and reverses out. The facing edge carries no real
     * traversal — mapping it naively inverts the station throat and every
     * arrival/departure spikes below the station on the axis.
     */
    @Test
    void terminalStubDepartureDoesNotDipBelowTheStation() {
        LineFixture line = new LineFixture().nodes(0, 200, 1000);
        line.station("Warszawa", 5);
        line.station("Radom", 500);
        line.station("Kraków", 950);
        SimGraph graph = line.build();

        SimProgram program = LineFixture.program(false,
                LineFixture.destination("Warszawa", new SimCondition.Delay(50)),
                LineFixture.destination("Kraków", new SimCondition.Delay(100)));
        // Head on the backward edge, facing the dead end at node 0, already
        // dwelling at Warszawa — the service's phantom setup at a terminus.
        SimTrainSpec spec = new SimTrainSpec("T", "T", 16, 0.01, 1.0, 0.5, 1.0,
                program, 1, 195);
        spec.startWaiting = true;
        List<SimTrainSpec> specs = List.of(spec);
        SimResult result = LineFixture.engine(graph, specs, 6000).run();
        SimDiagram diagram = build(graph, result, specs);

        assertEquals(List.of("Warszawa", "Radom", "Kraków"),
                diagram.stations.stream().map(SimDiagram.StationMark::name).toList());
        double warszawa = diagram.stations.get(0).pos();
        List<SimDiagram.Point> points = diagram.lines.get(0).segments().stream()
                .flatMap(List::stream).toList();
        for (SimDiagram.Point point : points)
            assertTrue(point.pos() >= warszawa - 1e-6,
                    "no point may dip below the terminus: " + point + " vs " + warszawa);
        double maxPos = points.stream().mapToDouble(SimDiagram.Point::pos).max().orElseThrow();
        assertEquals(diagram.stations.get(2).pos(), maxPos, 5.0);
    }

    @Test
    void hiddenTrainsAreLeftOutOfTheDiagram() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("End", 900);
        SimGraph graph = line.build();

        SimTrainSpec phantom = LineFixture.train("T", line, 50, LineFixture.program(false,
                LineFixture.destination("End", new SimCondition.Delay(100))));
        SimTrainSpec hiddenBus = LineFixture.train("Bus", line, 20, LineFixture.program(false,
                LineFixture.destination("End", new SimCondition.Delay(100))));
        List<SimTrainSpec> specs = List.of(phantom, hiddenBus);
        SimResult result = LineFixture.engine(graph, specs, 4000).run();
        SimDiagram diagram = SimDiagram.build(graph, result, specs,
                0, MAX_LINES, MAX_POINTS, Map.of(), Set.of(), Set.of(1));

        assertTrue(diagram.lines.stream().noneMatch(candidate -> candidate.train() == 1),
                "hidden trains draw no line");
        assertTrue(diagram.lines.stream().anyMatch(candidate -> candidate.train() == 0),
                "the corridor train always draws");
        assertEquals(0, diagram.linesDropped, "hidden trains don't count as dropped");
    }

    /**
     * A train skipping a stop via an unmapped express bypass is still en
     * route on the corridor: however long the crossing takes, its line must
     * bridge the unmapped stretch, not fragment. Only foreign station
     * visits (see the excursion test) justify a break.
     */
    @Test
    void skippingAStopOnABypassKeepsTheLineConnected() {
        SimGraph graph = new SimGraph();
        graph.nodes.add(new SimNode(0, false, 0, new SimVec(0, 0, 0)));
        graph.nodes.add(new SimNode(1, false, 0, new SimVec(400, 0, 0)));
        graph.nodes.add(new SimNode(2, false, 0, new SimVec(500, 0, 0)));
        graph.nodes.add(new SimNode(3, false, 0, new SimVec(900, 0, 0)));
        SimVec plusX = new SimVec(1, 0, 0);
        SimEdge m01 = new SimEdge(0, 0, 1, -1, 400, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge m12 = new SimEdge(1, 1, 2, -1, 200, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge m23 = new SimEdge(2, 2, 3, -1, 400, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        // Shorter than m12, so through trains route around the M platform.
        SimEdge bypass = new SimEdge(3, 1, 2, -1, 150, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        m01.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("a".getBytes()), "A", 50, true));
        m12.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("m".getBytes()), "M", 100, true));
        m23.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("b".getBytes()), "B", 350, true));
        // A routing waypoint on the bypass — foreign to the corridor, but
        // rolling through it must not count as serving another line.
        bypass.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("v".getBytes()), "ViaHS", 75, true));
        graph.edges.add(m01);
        graph.edges.add(m12);
        graph.edges.add(m23);
        graph.edges.add(bypass);
        graph.computeDerived();

        SimProgram allStops = LineFixture.program(false,
                LineFixture.destination("A", new SimCondition.Delay(100)),
                LineFixture.destination("M", new SimCondition.Delay(100)),
                LineFixture.destination("B", new SimCondition.Delay(100)));
        SimTrainSpec phantom = new SimTrainSpec("T", "T", 16, 0.01, 1.0, 0.5, 1.0,
                allStops, 0, 30);
        phantom.canReverse = false;
        // Slow enough that the bypass crossing far exceeds the break gate;
        // routed through the foreign waypoint like an SnR express service.
        SimProgram through = LineFixture.program(false,
                SimProgram.Entry.waypointDestination("ViaHS"),
                LineFixture.destination("B", new SimCondition.Delay(100)));
        SimTrainSpec skipper = new SimTrainSpec("S", "S", 16, 0.01, 0.2, 0.5, 1.0,
                through, 0, 100);
        skipper.canReverse = false;

        List<SimTrainSpec> specs = List.of(phantom, skipper);
        SimResult result = LineFixture.engine(graph, specs, 8000).run();
        SimDiagram diagram = build(graph, result, specs);

        SimDiagram.Line skipLine = diagram.lines.stream()
                .filter(candidate -> candidate.train() == 1).findFirst().orElseThrow();
        assertEquals(1, skipLine.segments().size(),
                "skipping a stop must not break the line: " + skipLine.segments());
        List<SimDiagram.Point> points = skipLine.segments().get(0);
        assertTrue(points.get(points.size() - 1).pos() - points.get(0).pos() > 500,
                "the through run progresses along the corridor: " + points);
        boolean bridged = false;
        for (int i = 1; i < points.size(); i++)
            bridged |= points.get(i).tick() - points.get(i - 1).tick() > 600;
        assertTrue(bridged, "the bypass crossing spans a bridged gap: " + points);
    }

    /**
     * A train that leaves the corridor's coverage for hours and comes back
     * must draw two separate legs. Speed-plausibility alone can't catch
     * this: given enough time away, a straight bridge across the whole
     * diagram looks reachable.
     */
    @Test
    void longOffCorridorExcursionBreaksTheLine() {
        SimGraph graph = new SimGraph();
        graph.nodes.add(new SimNode(0, false, 0, new SimVec(0, 0, 0)));
        graph.nodes.add(new SimNode(1, false, 0, new SimVec(1000, 0, 0)));
        graph.nodes.add(new SimNode(2, false, 0, new SimVec(3000, 0, 0)));
        SimVec plusX = new SimVec(1, 0, 0);
        SimEdge out = new SimEdge(0, 0, 1, -1, 1000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge back = new SimEdge(1, 1, 0, -1, 1000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge away = new SimEdge(2, 1, 2, -1, 2000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge home = new SimEdge(3, 2, 1, -1, 2000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        out.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("end".getBytes()), "End 1", 950, true));
        back.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("home".getBytes()), "Home 2", 950, true));
        back.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("mid".getBytes()), "Mid 2", 500, true));
        away.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("far".getBytes()), "Far", 1900, true));
        graph.edges.add(out);
        graph.edges.add(back);
        graph.edges.add(away);
        graph.edges.add(home);
        graph.computeDerived();

        SimProgram corridorRun = LineFixture.program(false,
                LineFixture.destination("End 1", new SimCondition.Delay(200)),
                LineFixture.destination("Home 2", new SimCondition.Delay(100)));
        SimTrainSpec phantom = new SimTrainSpec("T", "T", 16, 0.01, 1.0, 0.5, 1.0,
                corridorRun, 0, 50);
        phantom.canReverse = false;
        // Roams off through the excursion loop and returns much later.
        SimProgram excursion = LineFixture.program(false,
                LineFixture.destination("Far", new SimCondition.Delay(5000)),
                LineFixture.destination("Mid 2", new SimCondition.Delay(100)));
        SimTrainSpec roamer = new SimTrainSpec("R", "R", 16, 0.01, 1.0, 0.5, 1.0,
                excursion, 0, 300);
        roamer.canReverse = false;

        List<SimTrainSpec> specs = List.of(phantom, roamer);
        SimResult result = LineFixture.engine(graph, specs, 15000).run();
        SimDiagram diagram = build(graph, result, specs);

        SimDiagram.Line roamerLine = diagram.lines.stream()
                .filter(candidate -> candidate.train() == 1).findFirst().orElseThrow();
        assertTrue(roamerLine.segments().size() >= 2,
                "the excursion must break the line: " + roamerLine.segments());
        for (List<SimDiagram.Point> segment : roamerLine.segments())
            for (int i = 1; i < segment.size(); i++)
                assertTrue(segment.get(i).tick() - segment.get(i - 1).tick() < 5000,
                        "no segment may bridge the time away: " + segment);
    }

    @Test
    void blacklistedStationsLoseTheirMarksButNotTheCorridor() {
        SimGraph graph = doubleTrack(
                new String[] { "Warszawa 1", "Radom 1", "Kraków 1" },
                new String[] { "Kraków 2", "Radom 2", "Warszawa 2" });
        SimProgram program = LineFixture.program(false,
                LineFixture.destination("Kraków*", new SimCondition.Delay(200)),
                LineFixture.destination("Warszawa*", new SimCondition.Delay(100)));
        SimTrainSpec spec = new SimTrainSpec("T", "T", 16, 0.01, 1.0, 0.5, 1.0, program, 0, 50);
        spec.canReverse = false;
        List<SimTrainSpec> specs = List.of(spec);
        SimResult result = runDoubleTrack(graph, specs);

        // One blacklisted platform: the sibling still marks the group.
        SimDiagram partial = SimDiagram.build(graph, result, specs,
                0, MAX_LINES, MAX_POINTS, Map.of(), Set.of("Radom 1"), Set.of());
        assertEquals(List.of("Warszawa", "Radom", "Kraków"),
                partial.stations.stream().map(SimDiagram.StationMark::name).toList());

        // Every platform blacklisted: the group disappears, geometry stays.
        SimDiagram hidden = SimDiagram.build(graph, result, specs,
                0, MAX_LINES, MAX_POINTS, Map.of(), Set.of("Radom 1", "Radom 2"), Set.of());
        assertEquals(List.of("Warszawa", "Kraków"),
                hidden.stations.stream().map(SimDiagram.StationMark::name).toList());
        assertEquals(partial.corridorLength, hidden.corridorLength, 1e-6);
        assertFalse(hidden.lines.isEmpty(), "the train line still renders");
    }

    /**
     * A long station area whose platforms share one group collapses onto a
     * single corridor height — the fold pins later same-group platforms
     * back to the group's anchor, so all its dwell lines and the label sit
     * together (grouping is what keeps sibling platforms from drawing as
     * separate stations).
     */
    @Test
    void groupedPlatformsOfALongStationCollapseToOneHeight() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("Eis 1", 700);
        line.station("Eis 2", 950);
        SimGraph graph = line.build();

        SimTrainSpec spec = LineFixture.train("T", line, 50, LineFixture.program(false,
                LineFixture.destination("Eis 2", new SimCondition.Delay(100))));
        List<SimTrainSpec> specs = List.of(spec);
        SimResult result = LineFixture.engine(graph, specs, 4000).run();
        SimDiagram diagram = build(graph, result, specs);

        SimDiagram.StationMark mark = diagram.stations.stream()
                .filter(candidate -> candidate.name().equals("Eis")).findFirst().orElseThrow();
        assertEquals(700, mark.pos(), 1.0, "one logical station, one height");
        assertEquals(700, diagram.project(line.forwardEdgeAt(950), 950), 1.0,
                "the far platform folds onto the group anchor");
        List<SimDiagram.Point> points = diagram.lines.get(0).segments().get(0);
        double maxPos = points.stream().mapToDouble(SimDiagram.Point::pos).max().orElseThrow();
        assertEquals(700, maxPos, 5.0, "the dwell line sits on the label");
    }

    /**
     * The Salzingen field case: the phantom reverses at a terminal stub
     * and returns via a parallel one-way track whose waypoint station has
     * a direction-specific name the outbound never anchored. Without a
     * direction flip at the physical reversal, that unseen station extends
     * the axis onwards past the terminus and the whole return leg draws as
     * a mountain above the top station.
     */
    @Test
    void reversalFlipsTheFoldDirectionForUnseenReturnStations() {
        SimGraph graph = new SimGraph();
        graph.nodes.add(new SimNode(0, false, 0, new SimVec(0, 0, 0)));
        graph.nodes.add(new SimNode(1, false, 0, new SimVec(1000, 0, 0)));
        graph.nodes.add(new SimNode(2, false, 0, new SimVec(1120, 0, 0)));
        SimVec plusX = new SimVec(1, 0, 0);
        SimEdge out = new SimEdge(0, 0, 1, -1, 1000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge stub = new SimEdge(1, 1, 2, 2, 120, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge stubBack = new SimEdge(2, 2, 1, 1, 120, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        SimEdge back = new SimEdge(3, 1, 0, -1, 1000, SimEdge.Signal.NONE, 0,
                new double[0][], plusX, plusX, false);
        out.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("s1".getBytes()), "Start 1", 50, true));
        stub.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("t".getBytes()), "Term 1", 100, true));
        back.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("wp".getBytes()), "WP North", 400, true));
        back.stations.add(new SimEdge.Station(
                UUID.nameUUIDFromBytes("s2".getBytes()), "Start 2", 950, true));
        graph.edges.add(out);
        graph.edges.add(stub);
        graph.edges.add(stubBack);
        graph.edges.add(back);
        graph.computeDerived();

        SimProgram program = LineFixture.program(false,
                LineFixture.destination("Term 1", new SimCondition.Delay(200)),
                LineFixture.destination("Start 2", new SimCondition.Delay(100)));
        SimTrainSpec spec = new SimTrainSpec("T", "T", 16, 0.01, 1.0, 0.5, 1.0,
                program, 0, 30);
        List<SimTrainSpec> specs = List.of(spec);
        SimResult result = LineFixture.engine(graph, specs, 8000).run();
        SimDiagram diagram = build(graph, result, specs);

        double term = diagram.stations.stream()
                .filter(mark -> mark.name().equals("Term"))
                .findFirst().orElseThrow().pos();
        assertTrue(diagram.corridorLength <= term + 25 + 1e-6,
                "nothing extends past the reversal tip: length "
                        + diagram.corridorLength + " vs terminus " + term);
        double wpNorth = diagram.stations.stream()
                .filter(mark -> mark.name().equals("WP North"))
                .findFirst().orElseThrow().pos();
        assertEquals(600, wpNorth, 30.0,
                "the return waypoint folds back onto the outbound span: " + diagram.stations);
    }

    @Test
    void platformNumberHeuristic() {
        assertEquals("Radom", SimDiagram.baseName("Radom 1"));
        assertEquals("Radom", SimDiagram.baseName("Radom Platform 2"));
        assertEquals("Radom", SimDiagram.baseName("Radom peron 3a"));
        assertEquals("Radom Główny", SimDiagram.baseName("Radom Główny"));
        assertEquals("Kraków", SimDiagram.baseName("Kraków 2"));
    }

    @Test
    void trainIdleAtAStationAllRunDrawsNoLine() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("End", 900);
        line.station("Yard", 950);
        line.station("Depot", 980);
        SimGraph graph = line.build();

        SimTrainSpec mover = LineFixture.train("T", line, 50, LineFixture.program(false,
                LineFixture.destination("End", new SimCondition.Delay(100))));
        // Parked at the Yard platform, beyond the phantom's stop.
        SimTrainSpec idler = LineFixture.train("P", line, 950, null);
        // Scheduled but going nowhere: loops on the station it stands at,
        // producing endless re-visits without ever moving.
        SimTrainSpec looper = LineFixture.train("Q", line, 980, LineFixture.program(true,
                LineFixture.destination("Depot", new SimCondition.Delay(100))));
        looper.startWaiting = true;

        List<SimTrainSpec> specs = List.of(mover, idler, looper);
        SimResult result = LineFixture.engine(graph, specs, 4000).run();
        SimDiagram diagram = build(graph, result, specs);

        assertTrue(diagram.lines.stream().noneMatch(candidate -> candidate.train() == 1),
                "a train idle at a platform for the whole run draws no line");
        assertTrue(diagram.lines.stream().noneMatch(candidate -> candidate.train() == 2),
                "a schedule looping on its own station is still idle");
        assertTrue(diagram.lines.stream().anyMatch(candidate -> candidate.train() == 0),
                "the corridor train still draws");
    }

    @Test
    void obstacleBlocksItsSpotForTheWholeRun() {
        LineFixture line = new LineFixture().nodes(0, 1000);
        line.station("End", 900);
        SimGraph graph = line.build();

        SimTrainSpec mover = LineFixture.train("T", line, 50, LineFixture.program(false,
                LineFixture.destination("End", new SimCondition.Delay(100))));
        SimTrainSpec obstacle = LineFixture.train("X", line, 700, null);

        List<SimTrainSpec> specs = List.of(mover, obstacle);
        SimResult result = LineFixture.engine(graph, specs, 4000).run();
        SimDiagram diagram = build(graph, result, specs);

        SimDiagram.Line obstacleLine = diagram.lines.stream()
                .filter(candidate -> candidate.train() == 1).findFirst().orElseThrow();
        List<SimDiagram.Point> points = obstacleLine.segments().get(0);
        assertEquals(2, points.size());
        assertEquals(0, points.get(0).tick());
        assertEquals(result.ticksSimulated, points.get(1).tick());
        assertEquals(points.get(0).pos(), points.get(1).pos(), 1e-6);
        assertEquals(700, points.get(0).pos(), 1.0);
    }
}
