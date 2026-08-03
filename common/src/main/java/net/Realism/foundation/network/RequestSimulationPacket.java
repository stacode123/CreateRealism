package net.Realism.foundation.network;

import net.Realism.content.simulator.SimulationService;
import net.Realism.foundation.util.C2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Asks the server to simulate the schedule on the Advanced Schedule in the
 * sender's main hand. Only phantom-train setup travels here — the schedule
 * itself is read server-side from the held item, never trusted from the
 * client.
 */
public record RequestSimulationPacket(SimulationService.Settings settings) implements C2SPacket {

    public static RequestSimulationPacket read(FriendlyByteBuf buf) {
        return new RequestSimulationPacket(new SimulationService.Settings(
                Mth.clamp(buf.readVarInt(), 1, 100),
                Mth.clamp(buf.readVarInt(), 1, 100),
                Mth.clamp(buf.readByte(), 0, 2),
                Mth.clamp(buf.readDouble(), 0.01, 50),
                Mth.clamp(buf.readVarInt(), 1, 336),
                buf.readBoolean(),
                Mth.clamp(buf.readVarInt(), 0, 23),
                Mth.clamp(buf.readVarInt(), 0, 59),
                Mth.clamp(buf.readVarInt(), -1, 600),
                buf.readBoolean()));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(settings.carriages());
        buf.writeVarInt(settings.locomotives());
        buf.writeByte(settings.accelerationMode());
        buf.writeDouble(settings.customAcceleration());
        buf.writeVarInt(settings.horizonHours());
        buf.writeBoolean(settings.startNow());
        buf.writeVarInt(settings.startHour());
        buf.writeVarInt(settings.startMinute());
        buf.writeVarInt(settings.headwaySeconds());
        buf.writeBoolean(settings.thorough());
    }

    @Override
    public void handle(ServerPlayer player) {
        SimulationService.request(player, settings);
    }
}
