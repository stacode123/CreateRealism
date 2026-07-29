package net.Realism.content.graph.v2;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * A Tramways speed sign on a directed edge (only present when Tramways is
 * installed). {@code capKmh} is the resolved limit for travel in this edge's
 * direction; 0 means the sign carries no usable limit.
 */
public record RailSign(double offset, Vec3 position, double capKmh) {

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(offset);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeDouble(capKmh);
    }

    public static RailSign read(FriendlyByteBuf buf) {
        return new RailSign(buf.readDouble(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readDouble());
    }
}
