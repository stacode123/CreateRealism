package net.Realism.content.graph.v2;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * A station platform on a directed edge. Offset is measured from the edge's
 * start node in travel direction. {@code approachable} is true when a train
 * traveling in this edge's direction can stop at the platform.
 */
public record RailStation(UUID stationId, String name, double offset, boolean approachable) {

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(stationId);
        buf.writeUtf(name);
        buf.writeDouble(offset);
        buf.writeBoolean(approachable);
    }

    public static RailStation read(FriendlyByteBuf buf) {
        return new RailStation(buf.readUUID(), buf.readUtf(), buf.readDouble(), buf.readBoolean());
    }
}
