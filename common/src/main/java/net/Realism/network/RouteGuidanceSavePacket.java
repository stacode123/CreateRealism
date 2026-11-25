package net.Realism.network;

import net.Realism.trains.RouteGuidanceManager;
import net.Realism.util.C2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server: set (or clear, when the name is empty) this player's manual route guidance.
 */
public class RouteGuidanceSavePacket implements C2SPacket {

    private final String destinationName;

    public RouteGuidanceSavePacket(String destinationName) {
        this.destinationName = destinationName == null ? "" : destinationName;
    }

    public static RouteGuidanceSavePacket read(FriendlyByteBuf buf) {
        return new RouteGuidanceSavePacket(buf.readUtf());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(destinationName);
    }

    @Override
    public void handle(ServerPlayer player) {
        player.server.execute(() -> RouteGuidanceManager.apply(player, destinationName));
    }
}
