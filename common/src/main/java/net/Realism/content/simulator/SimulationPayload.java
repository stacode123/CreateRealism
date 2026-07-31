package net.Realism.content.simulator;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The client-facing outcome of a simulation request: either refusal reasons
 * (unsupported conditions, cooldown, caps...) or the projected timetable with
 * exclusions and notices. Kept free of any client class so the packet is safe
 * to verify on dedicated servers. Trajectory samples stay server-side until
 * M5's time-distance diagram needs them.
 */
public class SimulationPayload {

    public record Refusal(String translationKey, String detail) {}

    public record Visit(int entryIndex, String stationName, long arrivalTick, long departureTick) {}

    public record TrainLine(String name, boolean phantom, boolean obstacle, String endState,
                            List<String> notices, List<Visit> visits) {}

    public record ExcludedLine(String trainName, String translationKey, String detail) {}

    public final List<Refusal> refusals = new ArrayList<>();
    public final List<TrainLine> trains = new ArrayList<>();
    public final List<ExcludedLine> excluded = new ArrayList<>();
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
        return payload;
    }
}
