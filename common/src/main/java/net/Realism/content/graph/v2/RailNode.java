package net.Realism.content.graph.v2;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class RailNode {
    public enum NodeType {
        JUNCTION, DEAD_END, SIGNAL, PORTAL;

        public byte toByte() {
            return (byte) ordinal();
        }

        public static NodeType fromByte(byte b) {
            NodeType[] values = values();
            return b >= 0 && b < values.length ? values[b] : JUNCTION;
        }
    }

    public final int id;
    public final NodeType type;
    public final int dimension;
    public final Vec3 position;
    /** Ids of edges leaving this node; rebuilt on read, not serialized. */
    public final transient List<Integer> outgoing = new ArrayList<>();

    public RailNode(int id, NodeType type, int dimension, Vec3 position) {
        this.id = id;
        this.type = type;
        this.dimension = dimension;
        this.position = position;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(type.toByte());
        buf.writeVarInt(dimension);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
    }

    public static RailNode read(FriendlyByteBuf buf, int id) {
        NodeType type = NodeType.fromByte(buf.readByte());
        int dimension = buf.readVarInt();
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new RailNode(id, type, dimension, position);
    }
}
