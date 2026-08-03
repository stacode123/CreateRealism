package net.Realism.content.simulator.core;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Time-distance diagram data (SPEC §4.8), derived from a finished run. The
 * distance axis is the corridor of the reference train (the phantom): its
 * journey laid out between <em>logical stations</em> — platforms grouped by
 * CRN station tags when available, otherwise by stripping a trailing
 * platform number ("Radom 1"/"Radom 2" → "Radom"). The first traversal
 * between two logical stations lays the axis; any later traversal between
 * already-anchored stations folds onto the existing span, whether it uses
 * the same track, the opposite direction, or a parallel one — so an
 * out-and-back on double track draws as the classic up-and-down sawtooth
 * and trains on either track project fully. Other trains appear wherever
 * their samples touch mapped track; their polylines break where they leave
 * the corridor.
 */
public class SimDiagram {

    public record StationMark(String name, double pos) {}

    public record Point(long tick, double pos) {}

    /** One train's polyline, split into continuous segments. */
    public record Line(int train, List<List<Point>> segments) {}

    /** Corridor-position deviation below which a middle point is redundant. */
    private static final double SIMPLIFY_EPS = 0.75;
    /** Allowed projection jump per tick beyond top speed before splitting. */
    private static final double JUMP_SLACK = 8.0;
    /**
     * A gap between kept points longer than this, with off-corridor
     * evidence inside it, breaks the line instead of bridging it.
     */
    private static final long OFF_CORRIDOR_BREAK_TICKS = 600;
    /** Same-station events this close along the path are the same platform. */
    private static final double DUPLICATE_ANCHOR_DISTANCE = 16;
    /** "Name 3", "Name Platform 2b", "Name peron 1" → "Name". */
    private static final Pattern TRAILING_PLATFORM = Pattern.compile(
            "(.*?)(?:\\s+(?:platform|peron|track|tor|gleis))?\\s+\\d{1,3}[a-zA-Z]?\\s*",
            Pattern.CASE_INSENSITIVE);

    public double corridorLength;
    public final List<StationMark> stations = new ArrayList<>();
    public final List<Line> lines = new ArrayList<>();
    /** Trains with corridor presence that were cut by the line cap. */
    public int linesDropped;

    /** Per edge: piecewise-linear offset → corridor position, null = unmapped. */
    private final double[][] edgeOffsets;
    private final double[][] edgePositions;
    private final SimGraph graph;
    /** Station name → tag/group name (CRN station tags); heuristic fallback. */
    private final Map<String, String> stationGroups;
    /** Raw station names whose marks are suppressed (CRN station blacklist). */
    private final Set<String> hiddenStations;
    /** Logical station → corridor position of its first appearance. */
    private final Map<String, Double> anchorOf = new LinkedHashMap<>();

    private SimDiagram(SimGraph graph, Map<String, String> stationGroups,
                       Set<String> hiddenStations) {
        this.graph = graph;
        this.stationGroups = stationGroups;
        this.hiddenStations = hiddenStations;
        this.edgeOffsets = new double[graph.edges.size()][];
        this.edgePositions = new double[graph.edges.size()][];
    }

    /** Corridor position of a point on an edge, or NaN when off-corridor. */
    public double project(int edgeId, double offset) {
        if (edgeId < 0 || edgeId >= edgeOffsets.length || edgeOffsets[edgeId] == null)
            return Double.NaN;
        double[] offsets = edgeOffsets[edgeId];
        double[] positions = edgePositions[edgeId];
        if (offset <= offsets[0])
            return positions[0];
        for (int i = 1; i < offsets.length; i++)
            if (offset <= offsets[i]) {
                double t = (offset - offsets[i - 1]) / (offsets[i] - offsets[i - 1]);
                return positions[i - 1] + t * (positions[i] - positions[i - 1]);
            }
        return positions[positions.length - 1];
    }

    private String groupOf(String stationName) {
        String tagged = stationGroups.get(stationName);
        return tagged != null ? tagged : baseName(stationName);
    }

    static String baseName(String name) {
        var matcher = TRAILING_PLATFORM.matcher(name);
        if (!matcher.matches())
            return name;
        String base = matcher.group(1).trim();
        return base.isEmpty() ? name : base;
    }

    public static SimDiagram build(SimGraph graph, SimResult result, List<SimTrainSpec> specs,
                                   int corridorTrain, int maxLines, int maxPointsPerLine,
                                   Map<String, String> stationGroups, Set<String> hiddenStations,
                                   Set<Integer> hiddenTrains) {
        SimDiagram diagram = new SimDiagram(graph, stationGroups, hiddenStations);
        Map<UUID, Double> stationPositions =
                diagram.buildCorridor(result.trains.get(corridorTrain).path);
        long endTick = result.ticksSimulated;

        List<Line> candidates = new ArrayList<>();
        for (int i = 0; i < result.trains.size(); i++) {
            if (i != corridorTrain && hiddenTrains.contains(i))
                continue;
            List<Long> foreignVisitTicks = new ArrayList<>();
            List<Point> points = diagram.collectPoints(result, i, specs.get(i).program,
                    stationPositions, endTick, foreignVisitTicks);
            List<List<Point>> segments = diagram.segment(points,
                    specs.get(i).topSpeed, specs.get(i).length, foreignVisitTicks);
            if (i != corridorTrain
                    && idleAtStationAllRun(segments, specs.get(i).length, stationPositions, endTick))
                continue;
            for (int s = 0; s < segments.size(); s++)
                segments.set(s, capPoints(simplify(segments.get(s)), maxPointsPerLine));
            segments.removeIf(segment -> segment.size() < 2);
            if (!segments.isEmpty())
                candidates.add(new Line(i, segments));
        }

        // The corridor train leads; the rest ranked by time spent on-corridor.
        candidates.sort(java.util.Comparator
                .comparingInt((Line line) -> line.train() == corridorTrain ? 0 : 1)
                .thenComparingLong(line -> -coveredTicks(line))
                .thenComparingInt(Line::train));
        for (Line line : candidates) {
            if (diagram.lines.size() >= maxLines) {
                diagram.linesDropped++;
                continue;
            }
            diagram.lines.add(line);
        }
        return diagram;
    }

    /**
     * A train that sits at a platform for the entire run is parked scenery,
     * not traffic — its full-width flat line would only clutter the diagram.
     * Judged after segmentation: one unbroken stationary segment spanning
     * the run means the train never really went anywhere, even if it
     * shuffled an edge at the start or its schedule loops on the station it
     * never leaves. Mid-track idlers are kept (a train obstructing the
     * corridor is worth seeing), and trains that leave the coverage and
     * come back carry break points, so their dwells survive as traffic.
     */
    private static boolean idleAtStationAllRun(List<List<Point>> segments, double trainLength,
                                               Map<UUID, Double> stationPositions, long endTick) {
        if (segments.size() != 1)
            return false;
        List<Point> points = segments.get(0);
        if (points.size() < 2)
            return false;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Point point : points) {
            min = Math.min(min, point.pos());
            max = Math.max(max, point.pos());
        }
        // Dwell projections and head samples of the same parked train can
        // differ by up to the train's length.
        double reach = trainLength + JUMP_SLACK;
        if (max - min > reach)
            return false;
        if (points.get(0).tick() > OFF_CORRIDOR_BREAK_TICKS
                || points.get(points.size() - 1).tick() < endTick - OFF_CORRIDOR_BREAK_TICKS)
            return false;
        double center = (min + max) / 2;
        for (double stationPos : stationPositions.values())
            if (Math.abs(stationPos - center) <= reach)
                return true;
        return false;
    }

    private static long coveredTicks(Line line) {
        long total = 0;
        for (List<Point> segment : line.segments())
            total += segment.get(segment.size() - 1).tick() - segment.get(0).tick();
        return total;
    }

    // ------------------------------------------------------------------
    // Corridor construction
    // ------------------------------------------------------------------

    /** A logical station encountered along the reference train's path. */
    private record Anchor(double pathPos, String group) {}

    /**
     * Unrolls the reference train's traversal into a 1-D path, marks every
     * logical station it passes, and folds the path onto the corridor: an
     * unseen station extends the axis, a station seen before pins its leg
     * back to the existing position — the fold is a piecewise-linear map
     * from path position to corridor position, sampled per edge.
     */
    private Map<UUID, Double> buildCorridor(List<Integer> path) {
        int count = path.size();
        double[] pathStart = new double[count];
        double cumulative = 0;
        for (int i = 0; i < count; i++) {
            pathStart[i] = cumulative;
            cumulative += graph.edge(path.get(i)).length;
        }

        List<Anchor> anchors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SimEdge edge = graph.edge(path.get(i));
            for (SimEdge.Station station : edge.stations)
                anchors.add(new Anchor(pathStart[i] + station.offset(), groupOf(station.name())));
            if (edge.oppositeId >= 0)
                for (SimEdge.Station station : graph.edge(edge.oppositeId).stations)
                    anchors.add(new Anchor(pathStart[i] + edge.length - station.offset(),
                            groupOf(station.name())));
        }
        anchors.sort(java.util.Comparator.comparingDouble(Anchor::pathPos));

        // Physical reversals flip the fold direction. Seen-station pins flip
        // it too, but a terminus whose platforms all share one group makes
        // only flat pins — and the first unseen station on the way back
        // (direction-specific waypoint names, parallel-track halts) would
        // then extend the axis onwards past the terminus instead of coming
        // back down.
        int firstDriven0 = count > 1
                && graph.edge(path.get(0)).oppositeId == path.get(1) ? 1 : 0;
        List<Double> reversalsAt = new ArrayList<>();
        for (int i = firstDriven0; i + 1 < count; i++)
            if (graph.edge(path.get(i)).oppositeId == path.get(i + 1))
                reversalsAt.add(pathStart[i + 1]);

        // Fold breakpoints {pathPos, corridorPos}. Mirrored twin platforms
        // produce duplicate events at (nearly) the same path position — only
        // the first of those counts; a genuine revisit further along does.
        List<double[]> breakpoints = new ArrayList<>();
        breakpoints.add(new double[] { 0, 0 });
        double direction = 1;
        int reversal = 0;
        String lastGroup = null;
        double lastGroupPos = Double.NEGATIVE_INFINITY;
        for (Anchor anchor : anchors) {
            while (reversal < reversalsAt.size()
                    && reversalsAt.get(reversal) <= anchor.pathPos()) {
                double revPos = reversalsAt.get(reversal++);
                double[] tipFrom = breakpoints.get(breakpoints.size() - 1);
                if (revPos > tipFrom[0] + 1e-9)
                    breakpoints.add(new double[] { revPos,
                            tipFrom[1] + direction * (revPos - tipFrom[0]) });
                direction = -direction;
            }
            if (anchor.group().equals(lastGroup)
                    && anchor.pathPos() - lastGroupPos < DUPLICATE_ANCHOR_DISTANCE) {
                lastGroupPos = anchor.pathPos();
                continue;
            }
            lastGroup = anchor.group();
            lastGroupPos = anchor.pathPos();
            double[] last = breakpoints.get(breakpoints.size() - 1);
            Double seen = anchorOf.get(anchor.group());
            double corridorPos = seen != null ? seen
                    : last[1] + direction * (anchor.pathPos() - last[0]);
            if (seen == null)
                anchorOf.put(anchor.group(), corridorPos);
            if (anchor.pathPos() > last[0] + 1e-9) {
                double legDirection = Math.signum(corridorPos - last[1]);
                if (legDirection != 0)
                    direction = legDirection;
                breakpoints.add(new double[] { anchor.pathPos(), corridorPos });
            }
        }
        while (reversal < reversalsAt.size()) {
            double revPos = reversalsAt.get(reversal++);
            double[] tipFrom = breakpoints.get(breakpoints.size() - 1);
            if (revPos > tipFrom[0] + 1e-9)
                breakpoints.add(new double[] { revPos,
                        tipFrom[1] + direction * (revPos - tipFrom[0]) });
            direction = -direction;
        }
        double finalDirection = direction;

        // Per-edge maps, first traversal wins, sampling the fold function
        // with its interior breakpoints. An edge's opposite twin is the same
        // physical track, so it is always mapped alongside (mirrored) —
        // opposing traffic projects even where the reference train only ever
        // drove one way. A train that starts facing a terminal stub reverses
        // before actually driving: its facing edge carries no real traversal
        // and would bake the station throat in backwards — the outbound
        // drive (its twin) defines the geography and mirrors back onto it.
        int firstDriven = count > 1
                && graph.edge(path.get(0)).oppositeId == path.get(1) ? 1 : 0;
        for (int i = firstDriven; i < count; i++) {
            int edgeId = path.get(i);
            if (edgeOffsets[edgeId] != null)
                continue;
            SimEdge edge = graph.edge(edgeId);
            double from = pathStart[i];
            double to = from + edge.length;
            List<Double> offsets = new ArrayList<>();
            List<Double> positions = new ArrayList<>();
            offsets.add(0.0);
            positions.add(fold(breakpoints, finalDirection, from));
            for (double[] breakpoint : breakpoints)
                if (breakpoint[0] > from + 1e-9 && breakpoint[0] < to - 1e-9) {
                    offsets.add(breakpoint[0] - from);
                    positions.add(breakpoint[1]);
                }
            offsets.add(edge.length);
            positions.add(fold(breakpoints, finalDirection, to));
            edgeOffsets[edgeId] = offsets.stream().mapToDouble(Double::doubleValue).toArray();
            edgePositions[edgeId] = positions.stream().mapToDouble(Double::doubleValue).toArray();
            mirrorToTwin(edgeId);
        }

        normalizeCorridor();

        // Every platform in the network: exact projection when its track is
        // mapped, its group's anchor otherwise — so a train dwelling at a
        // sibling platform of a corridor station still shows.
        Map<UUID, Double> stationPositions = new HashMap<>();
        Set<String> marked = new HashSet<>();
        for (SimEdge edge : graph.edges)
            for (SimEdge.Station station : edge.stations) {
                String group = groupOf(station.name());
                double pos = project(edge.id, station.offset());
                if (Double.isNaN(pos)) {
                    Double anchor = anchorOf.get(group);
                    if (anchor == null)
                        continue;
                    pos = anchor;
                }
                stationPositions.putIfAbsent(station.id(), pos);
                // The projected platform position, not the raw fold anchor —
                // reversal bookkeeping can offset the anchor slightly, and
                // the label should sit exactly on the dwell lines.
                // A blacklisted platform never marks its group, so a group
                // disappears only when every one of its platforms is hidden.
                if (!hiddenStations.contains(station.name()) && marked.add(group))
                    stations.add(new StationMark(group, pos));
            }
        stations.sort(java.util.Comparator.comparingDouble(StationMark::pos));
        return stationPositions;
    }

    /** Copies an edge's mapping onto its unmapped opposite twin, mirrored. */
    private void mirrorToTwin(int edgeId) {
        SimEdge edge = graph.edge(edgeId);
        if (edge.oppositeId < 0 || edgeOffsets[edge.oppositeId] != null)
            return;
        double[] offsets = edgeOffsets[edgeId];
        double[] positions = edgePositions[edgeId];
        int points = offsets.length;
        double[] twinOffsets = new double[points];
        double[] twinPositions = new double[points];
        for (int k = 0; k < points; k++) {
            twinOffsets[k] = edge.length - offsets[points - 1 - k];
            twinPositions[k] = positions[points - 1 - k];
        }
        edgeOffsets[edge.oppositeId] = twinOffsets;
        edgePositions[edge.oppositeId] = twinPositions;
    }

    /** The path→corridor fold: linear between breakpoints, extended past them. */
    private static double fold(List<double[]> breakpoints, double finalDirection, double pathPos) {
        double[] previous = breakpoints.get(0);
        if (pathPos <= previous[0])
            return previous[1] + (pathPos - previous[0]);
        for (int i = 1; i < breakpoints.size(); i++) {
            double[] next = breakpoints.get(i);
            if (pathPos <= next[0]) {
                double t = (pathPos - previous[0]) / (next[0] - previous[0]);
                return previous[1] + t * (next[1] - previous[1]);
            }
            previous = next;
        }
        return previous[1] + finalDirection * (pathPos - previous[0]);
    }

    /** Shifts corridor positions to start at 0 and fixes the axis length. */
    private void normalizeCorridor() {
        double min = 0;
        double max = 0;
        for (double[] positions : edgePositions)
            if (positions != null)
                for (double pos : positions) {
                    min = Math.min(min, pos);
                    max = Math.max(max, pos);
                }
        if (min < 0) {
            double shift = min;
            for (double[] positions : edgePositions)
                if (positions != null)
                    for (int i = 0; i < positions.length; i++)
                        positions[i] -= shift;
            anchorOf.replaceAll((group, pos) -> pos - shift);
        }
        corridorLength = max - min;
    }

    // ------------------------------------------------------------------
    // Train polylines
    // ------------------------------------------------------------------

    /**
     * One tick-ordered point stream per train, merged from three sources:
     * movement samples, station dwells (exact horizontal segments — samples
     * skip the standstill) and signal-wait windows (exact elbows).
     * Off-corridor points are dropped: unmapped stretches (a sibling
     * platform, an express bypass, a whole parallel track) bridge with a
     * connecting line — a train skipping stops is still traveling the
     * corridor. But a visit to a station that does not project at all is
     * recorded in {@code foreignVisitTicks}: the train called somewhere on
     * another line, and {@link #segment} breaks the line there instead of
     * inventing a bridge across the whole diagram.
     */
    private List<Point> collectPoints(SimResult result, int trainIndex, SimProgram program,
                                      Map<UUID, Double> stationPositions, long endTick,
                                      List<Long> foreignVisitTicks) {
        SimResult.TrainResult train = result.trains.get(trainIndex);
        List<Point> points = new ArrayList<>();
        for (SimResult.Sample sample : train.samples) {
            double pos = project(sample.edgeId(), sample.offset());
            if (!Double.isNaN(pos))
                points.add(new Point(sample.tick(), pos));
        }
        for (SimResult.StationVisit visit : train.visits) {
            Double pos = visit.stationId() != null ? stationPositions.get(visit.stationId()) : null;
            if (pos == null) {
                // Rolling through a foreign SnR waypoint is routing, not
                // service — the train is still en route between corridor
                // calls, so it must not break the line.
                boolean waypoint = program != null && visit.entryIndex() >= 0
                        && visit.entryIndex() < program.entries.size()
                        && program.entries.get(visit.entryIndex()).waypoint;
                if (!waypoint)
                    foreignVisitTicks.add(visit.arrivalTick());
                continue;
            }
            points.add(new Point(visit.arrivalTick(), pos));
            points.add(new Point(visit.departureTick() >= 0 ? visit.departureTick() : endTick, pos));
        }
        for (SimResult.SimEvent event : result.events) {
            if (event.trainIndex() != trainIndex
                    || (event.type() != SimResult.EventType.SIGNAL_WAIT_START
                            && event.type() != SimResult.EventType.SIGNAL_WAIT_END))
                continue;
            double pos = project(event.edgeId(), event.offset());
            if (!Double.isNaN(pos))
                points.add(new Point(event.tick(), pos));
        }
        points.sort(java.util.Comparator.comparingLong(Point::tick));
        // Equal ticks: first wins.
        List<Point> deduped = new ArrayList<>(points.size());
        for (Point point : points) {
            if (!deduped.isEmpty() && deduped.get(deduped.size() - 1).tick() == point.tick())
                continue;
            deduped.add(point);
        }
        // A static obstacle sampled once blocks its spot for the whole run.
        if (train.obstacle && deduped.size() == 1)
            deduped.add(new Point(endTick, deduped.get(0).pos()));
        return deduped;
    }

    /**
     * Splits a point stream into continuous corridor segments in two cases.
     * A position jump no train movement can explain (the train left the
     * corridor and re-entered somewhere else — drawing through would invent
     * a diagonal): folding can compress or stretch legs slightly, hence the
     * generous factor, and the train's own length is always allowed because
     * reversing at a terminus swaps head and tail. And a long gap holding a
     * foreign station visit: the train demonstrably served another line in
     * between, so bridging would draw a fake straight line across the
     * diagram. Long gaps without foreign calls stay connected — that's a
     * train skipping stops or running a parallel track, still en route.
     */
    private List<List<Point>> segment(List<Point> points, double topSpeed, double trainLength,
                                      List<Long> foreignVisitTicks) {
        List<List<Point>> segments = new ArrayList<>();
        List<Point> current = new ArrayList<>();
        Point previous = null;
        int foreign = 0;
        for (Point point : points) {
            if (previous != null) {
                while (foreign < foreignVisitTicks.size()
                        && foreignVisitTicks.get(foreign) <= previous.tick())
                    foreign++;
                boolean wasAway = point.tick() - previous.tick() > OFF_CORRIDOR_BREAK_TICKS
                        && foreign < foreignVisitTicks.size()
                        && foreignVisitTicks.get(foreign) < point.tick();
                double reachable = (point.tick() - previous.tick()) * topSpeed * 1.5
                        + JUMP_SLACK + trainLength;
                if (wasAway || Math.abs(point.pos() - previous.pos()) > reachable) {
                    if (current.size() >= 2)
                        segments.add(current);
                    current = new ArrayList<>();
                }
            }
            current.add(point);
            previous = point;
        }
        if (current.size() >= 2)
            segments.add(current);
        return segments;
    }

    /** Drops points that sit on the line between their kept neighbors. */
    private static List<Point> simplify(List<Point> segment) {
        if (segment.size() <= 2)
            return segment;
        List<Point> kept = new ArrayList<>(segment.size());
        kept.add(segment.get(0));
        for (int i = 1; i < segment.size() - 1; i++) {
            Point anchor = kept.get(kept.size() - 1);
            Point next = segment.get(i + 1);
            Point candidate = segment.get(i);
            double span = next.tick() - anchor.tick();
            double expected = span <= 0 ? anchor.pos()
                    : anchor.pos() + (next.pos() - anchor.pos())
                            * (candidate.tick() - anchor.tick()) / span;
            if (Math.abs(candidate.pos() - expected) > SIMPLIFY_EPS)
                kept.add(candidate);
        }
        kept.add(segment.get(segment.size() - 1));
        return kept;
    }

    /** Uniform decimation to the cap, endpoints always kept. */
    private static List<Point> capPoints(List<Point> segment, int maxPoints) {
        if (segment.size() <= maxPoints)
            return segment;
        List<Point> reduced = new ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints - 1; i++)
            reduced.add(segment.get((int) ((long) i * (segment.size() - 1) / (maxPoints - 1))));
        reduced.add(segment.get(segment.size() - 1));
        return reduced;
    }
}
