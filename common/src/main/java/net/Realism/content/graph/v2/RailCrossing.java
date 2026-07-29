package net.Realism.content.graph.v2;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A diamond crossing (level junction of two edges without a switch). The same
 * {@code crossingId} appears on every edge passing through the crossing; the
 * simulator treats it as an exclusive point resource.
 */
public record RailCrossing(UUID crossingId, double offset, Vec3 position) {

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(crossingId);
        buf.writeDouble(offset);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
    }

    public static RailCrossing read(FriendlyByteBuf buf) {
        return new RailCrossing(buf.readUUID(), buf.readDouble(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }
}
