package net.Realism.content.graph.v2;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A directed edge of the collapsed rail graph. Every edge has an opposite
 * twin traversing the same track the other way ({@code oppositeId}), except
 * one-way constructs where Create itself has no reverse connection.
 */
public class RailEdge {
    public final int id;
    public final int from;
    public final int to;
    /** Id of the reverse-direction twin, or -1. */
    public int oppositeId = -1;
    public final double length;
    /** Curvature/sign speed cap in km/h for this direction; 0 = uncapped. */
    public final double speedCapKmh;
    /** Signal governing entry into this edge at its start node, if any. */
    public final SignalKind entrySignal;
    public final boolean interDimensional;
    public final List<RailStation> stations = new ArrayList<>();
    public final List<RailCrossing> crossings = new ArrayList<>();
    public final List<RailSign> signs = new ArrayList<>();
    /**
     * Curvature cap ranges as {@code [start, end, capKmh]} offsets along this
     * direction; positions not covered by any range are uncapped.
     */
    public final List<double[]> capProfile = new ArrayList<>();
    /**
     * Polyline through the underlying track nodes (and curve samples) from
     * start to end, world coordinates in the start node's dimension. Includes
     * both endpoints.
     */
    public final List<Vec3> shape = new ArrayList<>();

    /** Curvature speed cap at an offset along this edge; 0 = uncapped. */
    public double capAt(double offset) {
        double cap = 0;
        for (double[] range : capProfile) {
            if (offset < range[0] || offset > range[1])
                continue;
            cap = cap == 0 ? range[2] : Math.min(cap, range[2]);
        }
        return cap;
    }

    public RailEdge(int id, int from, int to, double length, double speedCapKmh,
                    SignalKind entrySignal, boolean interDimensional) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.length = length;
        this.speedCapKmh = speedCapKmh;
        this.entrySignal = entrySignal;
        this.interDimensional = interDimensional;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(from);
        buf.writeVarInt(to);
        buf.writeVarInt(oppositeId + 1);
        buf.writeDouble(length);
        buf.writeDouble(speedCapKmh);
        buf.writeByte(entrySignal.toByte());
        buf.writeBoolean(interDimensional);
        buf.writeVarInt(stations.size());
        for (RailStation station : stations)
            station.write(buf);
        buf.writeVarInt(crossings.size());
        for (RailCrossing crossing : crossings)
            crossing.write(buf);
        buf.writeVarInt(signs.size());
        for (RailSign sign : signs)
            sign.write(buf);
        buf.writeVarInt(capProfile.size());
        for (double[] range : capProfile) {
            buf.writeDouble(range[0]);
            buf.writeDouble(range[1]);
            buf.writeDouble(range[2]);
        }
        buf.writeVarInt(shape.size());
        for (Vec3 point : shape) {
            buf.writeFloat((float) point.x);
            buf.writeFloat((float) point.y);
            buf.writeFloat((float) point.z);
        }
    }

    public static RailEdge read(FriendlyByteBuf buf, int id) {
        int from = buf.readVarInt();
        int to = buf.readVarInt();
        int opposite = buf.readVarInt() - 1;
        double length = buf.readDouble();
        double speedCap = buf.readDouble();
        SignalKind entrySignal = SignalKind.fromByte(buf.readByte());
        boolean interDimensional = buf.readBoolean();
        RailEdge edge = new RailEdge(id, from, to, length, speedCap, entrySignal, interDimensional);
        edge.oppositeId = opposite;
        int stationCount = buf.readVarInt();
        for (int i = 0; i < stationCount; i++)
            edge.stations.add(RailStation.read(buf));
        int crossingCount = buf.readVarInt();
        for (int i = 0; i < crossingCount; i++)
            edge.crossings.add(RailCrossing.read(buf));
        int signCount = buf.readVarInt();
        for (int i = 0; i < signCount; i++)
            edge.signs.add(RailSign.read(buf));
        int profileCount = buf.readVarInt();
        for (int i = 0; i < profileCount; i++)
            edge.capProfile.add(new double[] { buf.readDouble(), buf.readDouble(), buf.readDouble() });
        int shapeCount = buf.readVarInt();
        for (int i = 0; i < shapeCount; i++)
            edge.shape.add(new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
        return edge;
    }
}
