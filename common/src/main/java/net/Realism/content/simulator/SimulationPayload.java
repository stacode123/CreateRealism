package net.Realism.content.simulator;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The client-facing outcome of a simulation request: either refusal reasons
 * (unsupported conditions, cooldown, caps...) or the projected timetable with
 * exclusions and notices. Kept free of any client class so the packet is safe
 * to verify on dedicated servers. Trajectories travel as corridor-projected
 * diagram polylines — raw samples stay server-side.
 */
public class SimulationPayload {

    public record Refusal(String translationKey, String detail) {}

    public record Visit(int entryIndex, String stationName, long arrivalTick, long departureTick) {}

    public record TrainLine(String name, boolean phantom, boolean obstacle, String endState,
                            List<String> notices, List<Visit> visits) {}

    public record ExcludedLine(String trainName, String translationKey, String detail) {}

    /**
     * One merged conflict; {@code type} is a {@code SimConflict.Type}
     * ordinal, {@code dimension} the dimension id string (for map badges)
     * and {@code diagramPos} the corridor position (NaN = off-corridor).
     */
    public record ConflictLine(int type, long startTick, long endTick, int count,
                               int x, int y, int z, String resourceName,
                               List<String> trainNames, boolean nonDeterministic,
                               String dimension, float diagramPos) {}

    public record DiagramStation(String name, float pos) {}

    public record DiagramPoint(long tick, float pos) {}

    /** One train's corridor polyline, split into continuous segments. */
    // category is a temporary debug aid for tuning Sim Diagram Hidden Categories.
    public record DiagramLine(String trainName, boolean phantom, String category,
                              List<List<DiagramPoint>> segments) {}

    /**
     * One resolved wait chain: the train every queued train is ultimately
     * stuck behind. {@code kind} is a {@code SimResult.RootCauseKind}
     * ordinal; {@code strandedNames} is the queue in formation order (may be
     * capped — {@code strandedCount} is the real total); position/dimension
     * locate the root train's head for the Map button.
     */
    public record RootCauseLine(String rootName, int kind, String detail,
                                List<String> strandedNames, int strandedCount,
                                boolean phantomStranded, long sinceTick,
                                int x, int y, int z, String dimension) {}

    public final List<Refusal> refusals = new ArrayList<>();
    public final List<TrainLine> trains = new ArrayList<>();
    public final List<ExcludedLine> excluded = new ArrayList<>();
    public final List<ConflictLine> conflicts = new ArrayList<>();
    /** Wait chains resolved to their root blockers; leads the results panel. */
    public final List<RootCauseLine> rootCauses = new ArrayList<>();
    // Time-distance diagram data (M5): the phantom's route corridor.
    public float diagramLength;
    public final List<DiagramStation> diagramStations = new ArrayList<>();
    public final List<DiagramLine> diagramLines = new ArrayList<>();
    /** Trains with corridor presence cut to keep the packet bounded. */
    public int diagramLinesDropped;
    /** How many further conflicts were cut to keep the packet bounded. */
    public int conflictsDropped;
    /** True when a baseline diff removed pre-existing network conflicts. */
    public boolean thorough;
    public long startDayTime;
    public double dayTimeRate;
    public long horizonTicks;
    public long ticksSimulated;
    public boolean truncated;
    /** Preformatted compute-time breakdown; empty on refusals. */
    public String perfSummary = "";

    public boolean refused() {
        return !refusals.isEmpty();
    }

    public static SimulationPayload refusal(String translationKey, String detail) {
        SimulationPayload payload = new SimulationPayload();
        payload.refusals.add(new Refusal(translationKey, detail));
        return payload;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(refusals.size());
        for (Refusal refusal : refusals) {
            buf.writeUtf(refusal.translationKey());
            buf.writeUtf(refusal.detail());
        }
        buf.writeVarInt(trains.size());
        for (TrainLine train : trains) {
            buf.writeUtf(train.name());
            buf.writeBoolean(train.phantom());
            buf.writeBoolean(train.obstacle());
            buf.writeUtf(train.endState());
            buf.writeVarInt(train.notices().size());
            for (String notice : train.notices())
                buf.writeUtf(notice);
            buf.writeVarInt(train.visits().size());
            for (Visit visit : train.visits()) {
                buf.writeVarInt(visit.entryIndex());
                buf.writeUtf(visit.stationName());
                buf.writeVarLong(visit.arrivalTick() + 1);
                buf.writeVarLong(visit.departureTick() + 1);
            }
        }
        buf.writeVarInt(excluded.size());
        for (ExcludedLine line : excluded) {
            buf.writeUtf(line.trainName());
            buf.writeUtf(line.translationKey());
            buf.writeUtf(line.detail());
        }
        buf.writeVarLong(startDayTime);
        buf.writeDouble(dayTimeRate);
        buf.writeVarLong(horizonTicks);
        buf.writeVarLong(ticksSimulated);
        buf.writeBoolean(truncated);
        buf.writeUtf(perfSummary);
        buf.writeVarInt(conflicts.size());
        for (ConflictLine conflict : conflicts) {
            buf.writeByte(conflict.type());
            buf.writeVarLong(conflict.startTick());
            buf.writeVarLong(conflict.endTick());
            buf.writeVarInt(conflict.count());
            buf.writeVarInt(conflict.x());
            buf.writeVarInt(conflict.y());
            buf.writeVarInt(conflict.z());
            buf.writeUtf(conflict.resourceName());
            buf.writeVarInt(conflict.trainNames().size());
            for (String name : conflict.trainNames())
                buf.writeUtf(name);
            buf.writeBoolean(conflict.nonDeterministic());
            buf.writeUtf(conflict.dimension());
            buf.writeFloat(conflict.diagramPos());
        }
        buf.writeVarInt(conflictsDropped);
        buf.writeBoolean(thorough);
        buf.writeFloat(diagramLength);
        buf.writeVarInt(diagramStations.size());
        for (DiagramStation station : diagramStations) {
            buf.writeUtf(station.name());
            buf.writeFloat(station.pos());
        }
        buf.writeVarInt(diagramLines.size());
        for (DiagramLine line : diagramLines) {
            buf.writeUtf(line.trainName());
            buf.writeBoolean(line.phantom());
            buf.writeUtf(line.category());
            buf.writeVarInt(line.segments().size());
            for (List<DiagramPoint> segment : line.segments()) {
                buf.writeVarInt(segment.size());
                for (DiagramPoint point : segment) {
                    buf.writeVarLong(point.tick());
                    buf.writeFloat(point.pos());
                }
            }
        }
        buf.writeVarInt(diagramLinesDropped);
        buf.writeVarInt(rootCauses.size());
        for (RootCauseLine cause : rootCauses) {
            buf.writeUtf(cause.rootName());
            buf.writeByte(cause.kind());
            buf.writeUtf(cause.detail());
            buf.writeVarInt(cause.strandedNames().size());
            for (String name : cause.strandedNames())
                buf.writeUtf(name);
            buf.writeVarInt(cause.strandedCount());
            buf.writeBoolean(cause.phantomStranded());
            buf.writeVarLong(cause.sinceTick());
            buf.writeVarInt(cause.x());
            buf.writeVarInt(cause.y());
            buf.writeVarInt(cause.z());
            buf.writeUtf(cause.dimension());
        }
    }

    public static SimulationPayload read(FriendlyByteBuf buf) {
        SimulationPayload payload = new SimulationPayload();
        int refusalCount = buf.readVarInt();
        for (int i = 0; i < refusalCount; i++)
            payload.refusals.add(new Refusal(buf.readUtf(), buf.readUtf()));
        int trainCount = buf.readVarInt();
        for (int i = 0; i < trainCount; i++) {
            String name = buf.readUtf();
            boolean phantom = buf.readBoolean();
            boolean obstacle = buf.readBoolean();
            String endState = buf.readUtf();
            List<String> notices = new ArrayList<>();
            int noticeCount = buf.readVarInt();
            for (int j = 0; j < noticeCount; j++)
                notices.add(buf.readUtf());
            List<Visit> visits = new ArrayList<>();
            int visitCount = buf.readVarInt();
            for (int j = 0; j < visitCount; j++)
                visits.add(new Visit(buf.readVarInt(), buf.readUtf(),
                        buf.readVarLong() - 1, buf.readVarLong() - 1));
            payload.trains.add(new TrainLine(name, phantom, obstacle, endState, notices, visits));
        }
        int excludedCount = buf.readVarInt();
        for (int i = 0; i < excludedCount; i++)
            payload.excluded.add(new ExcludedLine(buf.readUtf(), buf.readUtf(), buf.readUtf()));
        payload.startDayTime = buf.readVarLong();
        payload.dayTimeRate = buf.readDouble();
        payload.horizonTicks = buf.readVarLong();
        payload.ticksSimulated = buf.readVarLong();
        payload.truncated = buf.readBoolean();
        payload.perfSummary = buf.readUtf();
        int conflictCount = buf.readVarInt();
        for (int i = 0; i < conflictCount; i++) {
            int type = buf.readByte();
            long startTick = buf.readVarLong();
            long endTick = buf.readVarLong();
            int count = buf.readVarInt();
            int x = buf.readVarInt();
            int y = buf.readVarInt();
            int z = buf.readVarInt();
            String resourceName = buf.readUtf();
            List<String> trainNames = new ArrayList<>();
            int nameCount = buf.readVarInt();
            for (int j = 0; j < nameCount; j++)
                trainNames.add(buf.readUtf());
            payload.conflicts.add(new ConflictLine(type, startTick, endTick, count,
                    x, y, z, resourceName, trainNames, buf.readBoolean(),
                    buf.readUtf(), buf.readFloat()));
        }
        payload.conflictsDropped = buf.readVarInt();
        payload.thorough = buf.readBoolean();
        payload.diagramLength = buf.readFloat();
        int stationCount = buf.readVarInt();
        for (int i = 0; i < stationCount; i++)
            payload.diagramStations.add(new DiagramStation(buf.readUtf(), buf.readFloat()));
        int lineCount = buf.readVarInt();
        for (int i = 0; i < lineCount; i++) {
            String trainName = buf.readUtf();
            boolean phantom = buf.readBoolean();
            String category = buf.readUtf();
            List<List<DiagramPoint>> segments = new ArrayList<>();
            int segmentCount = buf.readVarInt();
            for (int j = 0; j < segmentCount; j++) {
                List<DiagramPoint> segment = new ArrayList<>();
                int pointCount = buf.readVarInt();
                for (int k = 0; k < pointCount; k++)
                    segment.add(new DiagramPoint(buf.readVarLong(), buf.readFloat()));
                segments.add(segment);
            }
            payload.diagramLines.add(new DiagramLine(trainName, phantom, category, segments));
        }
        payload.diagramLinesDropped = buf.readVarInt();
        int rootCauseCount = buf.readVarInt();
        for (int i = 0; i < rootCauseCount; i++) {
            String rootName = buf.readUtf();
            int kind = buf.readByte();
            String detail = buf.readUtf();
            List<String> strandedNames = new ArrayList<>();
            int strandedNameCount = buf.readVarInt();
            for (int j = 0; j < strandedNameCount; j++)
                strandedNames.add(buf.readUtf());
            payload.rootCauses.add(new RootCauseLine(rootName, kind, detail, strandedNames,
                    buf.readVarInt(), buf.readBoolean(), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
        }
        return payload;
    }
}
