package net.Realism.content.simulator;

import net.Realism.content.simulator.core.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Renders a finished simulation into a self-contained HTML playback viewer
 * (SPEC §6's debug export): the network as chords, every train's full sample
 * trail, signal-wait windows and conflicts, with play/scrub/zoom controls.
 * A debugging tool behind the {@code Sim Debug Export} server config —
 * deliberately file-based so it carries no packet-size or GUI constraints.
 * Minecraft-free: fed from the sim core only, testable with JUnit.
 */
public class SimDebugExporter {

    private static final String DATA_MARKER = "/*__DATA__*/null";

    public static String buildHtml(SimGraph graph, SimResult result, List<SimTrainSpec> specs,
                                   List<String> dimensionNames, Map<String, String> stationGroups,
                                   long startDayTime, double dayTimeRate, int sampleStride) {
        String template = loadTemplate();
        return template.replace(DATA_MARKER,
                buildJson(graph, result, specs, dimensionNames, stationGroups,
                        startDayTime, dayTimeRate, sampleStride));
    }

    private static String loadTemplate() {
        try (InputStream stream =
                     SimDebugExporter.class.getResourceAsStream("/assets/realism/sim_debug.html")) {
            if (stream == null)
                throw new IllegalStateException("sim_debug.html template missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load sim_debug.html template", e);
        }
    }

    /** The raw dataset as JSON — also written next to the HTML for reading. */
    public static String buildJson(SimGraph graph, SimResult result, List<SimTrainSpec> specs,
                                   List<String> dimensionNames, Map<String, String> stationGroups,
                                   long startDayTime, double dayTimeRate, int sampleStride) {
        StringBuilder json = new StringBuilder(1 << 20);
        json.append("{\"meta\":{\"start\":").append(startDayTime)
                .append(",\"rate\":").append(number(dayTimeRate))
                .append(",\"ticks\":").append(result.ticksSimulated)
                .append(",\"stride\":").append(sampleStride)
                .append(",\"dims\":[");
        for (int i = 0; i < dimensionNames.size(); i++) {
            if (i > 0)
                json.append(',');
            string(json, dimensionNames.get(i));
        }
        if (dimensionNames.isEmpty())
            json.append("\"world\"");
        json.append("]},\"nodes\":[");

        for (int i = 0; i < graph.nodes.size(); i++) {
            SimNode node = graph.nodes.get(i);
            if (i > 0)
                json.append(',');
            json.append('[').append(number(node.position().x())).append(',')
                    .append(number(node.position().z())).append(',')
                    .append(node.dimension()).append(',')
                    .append(node.signalBoundary() ? 1 : 0).append(']');
        }

        json.append("],\"edges\":[");
        for (int i = 0; i < graph.edges.size(); i++) {
            SimEdge edge = graph.edges.get(i);
            if (i > 0)
                json.append(',');
            // The legal continuations are the engine's real routing table
            // (turn rule + one-way signals) — the ground truth for
            // debugging "why can't this train get there".
            json.append('[').append(edge.from).append(',').append(edge.to).append(',')
                    .append(number(edge.length)).append(',')
                    .append(edge.entrySignal.ordinal()).append(',')
                    .append(edge.oppositeId).append(",[");
            for (int k = 0; k < edge.nextEdges.length; k++) {
                if (k > 0)
                    json.append(',');
                json.append(edge.nextEdges[k]);
            }
            json.append("],").append(edge.sectionId).append(']');
        }

        json.append("],\"stations\":[");
        Set<UUID> seenStations = new HashSet<>();
        boolean firstStation = true;
        for (SimEdge edge : graph.edges)
            for (SimEdge.Station station : edge.stations) {
                if (!seenStations.add(station.id()))
                    continue;
                if (!firstStation)
                    json.append(',');
                firstStation = false;
                json.append('[').append(edge.id).append(',')
                        .append(number(station.offset())).append(',');
                string(json, station.name());
                json.append(']');
            }

        // CRN station tags in force during this run — which platforms the
        // diagram folds together as one logical station.
        json.append("],\"groups\":{");
        boolean firstGroup = true;
        for (Map.Entry<String, String> group : stationGroups.entrySet()) {
            if (!firstGroup)
                json.append(',');
            firstGroup = false;
            string(json, group.getKey());
            json.append(':');
            string(json, group.getValue());
        }
        json.append("},\"trains\":[");
        for (int i = 0; i < result.trains.size(); i++) {
            SimResult.TrainResult train = result.trains.get(i);
            if (i > 0)
                json.append(',');
            SimTrainSpec spec = specs.get(i);
            json.append("{\"n\":");
            string(json, train.name);
            json.append(",\"p\":").append(i == 0 ? 1 : 0)
                    .append(",\"o\":").append(train.obstacle ? 1 : 0)
                    .append(",\"len\":").append(number(spec.length))
                    .append(",\"ts\":").append(number(spec.topSpeed))
                    .append(",\"end\":");
            string(json, train.endState);
            json.append(",\"s\":[");
            for (int k = 0; k < train.samples.size(); k++) {
                SimResult.Sample sample = train.samples.get(k);
                if (k > 0)
                    json.append(',');
                json.append('[').append(sample.tick()).append(',').append(sample.edgeId())
                        .append(',').append(number(sample.offset())).append(',')
                        .append(number(sample.speed())).append(']');
            }
            // The traversed edge sequence, so playback can interpolate along
            // the track between samples instead of cutting corners.
            json.append("],\"path\":[");
            for (int k = 0; k < train.path.size(); k++) {
                if (k > 0)
                    json.append(',');
                json.append(train.path.get(k));
            }
            // The route still planned at run end — where a stuck train was
            // actually trying to go.
            json.append("],\"plan\":[");
            for (int k = 0; k < train.finalPlan.length; k++) {
                if (k > 0)
                    json.append(',');
                json.append(train.finalPlan[k]);
            }
            json.append("],\"v\":[");
            for (int k = 0; k < train.visits.size(); k++) {
                SimResult.StationVisit visit = train.visits.get(k);
                if (k > 0)
                    json.append(',');
                json.append('[').append(visit.arrivalTick()).append(',')
                        .append(visit.departureTick()).append(',');
                string(json, visit.stationName());
                json.append(']');
            }
            json.append("],\"fail\":[");
            appendFailedFilters(json, result, specs, i);
            json.append("],\"w\":[");
            appendWaits(json, result, i);
            json.append("]}");
        }

        json.append("],\"conflicts\":[");
        for (int i = 0; i < result.conflicts.size(); i++) {
            SimConflict conflict = result.conflicts.get(i);
            if (i > 0)
                json.append(',');
            List<String> names = new ArrayList<>();
            for (int trainIndex : conflict.trains())
                names.add(result.trains.get(trainIndex).name);
            json.append('[').append(conflict.type().ordinal()).append(',')
                    .append(conflict.startTick()).append(',').append(conflict.endTick()).append(',')
                    .append(number(conflict.position().x())).append(',')
                    .append(number(conflict.position().z())).append(',')
                    .append(conflict.dimension()).append(',');
            string(json, conflict.resourceName());
            json.append(',');
            string(json, String.join(", ", names));
            json.append(']');
        }
        return json.append("]}").toString();
    }

    /** Destination filters this train's navigation could not route to. */
    private static void appendFailedFilters(StringBuilder json, SimResult result,
                                            List<SimTrainSpec> specs, int trainIndex) {
        SimProgram program = specs.get(trainIndex).program;
        if (program == null)
            return;
        Set<Integer> seen = new HashSet<>();
        boolean first = true;
        for (SimResult.SimEvent event : result.events) {
            if (event.trainIndex() != trainIndex
                    || event.type() != SimResult.EventType.PATH_FAILED
                    || !seen.add((int) event.data())
                    || event.data() >= program.entries.size())
                continue;
            if (!first)
                json.append(',');
            first = false;
            string(json, program.entries.get((int) event.data()).filterText);
        }
    }

    /**
     * Signal-wait windows of one train, paired from the event stream and
     * joined with the wait-block records: [start, end, blockedEdge, holder].
     */
    private static void appendWaits(StringBuilder json, SimResult result, int trainIndex) {
        java.util.Map<Long, long[]> blocks = new java.util.HashMap<>();
        for (long[] block : result.trains.get(trainIndex).waitBlocks)
            blocks.put(block[0], block);
        long open = -1;
        boolean first = true;
        for (SimResult.SimEvent event : result.events) {
            if (event.trainIndex() != trainIndex)
                continue;
            if (event.type() == SimResult.EventType.SIGNAL_WAIT_START) {
                open = event.tick();
            } else if (event.type() == SimResult.EventType.SIGNAL_WAIT_END && open >= 0) {
                first = appendWait(json, first, open, event.tick(), blocks.get(open));
                open = -1;
            }
        }
        if (open >= 0)
            appendWait(json, first, open, result.ticksSimulated, blocks.get(open));
    }

    private static boolean appendWait(StringBuilder json, boolean first,
                                      long start, long end, long[] block) {
        if (!first)
            json.append(',');
        json.append('[').append(start).append(',').append(end).append(',')
                .append(block != null ? block[1] : -1).append(',')
                .append(block != null ? block[2] : -1).append(']');
        return false;
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15)
            return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** JSON string literal; also escapes {@code <} so names can't close the script tag. */
    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '<' -> json.append("\\u003c");
                default -> {
                    if (c < 0x20)
                        json.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else
                        json.append(c);
                }
            }
        }
        json.append('"');
    }
}
