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
    private final boolean autoSteer;

    public RouteGuidanceSavePacket(String destinationName, boolean autoSteer) {
        this.destinationName = destinationName == null ? "" : destinationName;
        this.autoSteer = autoSteer;
    }

    public static RouteGuidanceSavePacket read(FriendlyByteBuf buf) {
        return new RouteGuidanceSavePacket(buf.readUtf(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(destinationName);
        buf.writeBoolean(autoSteer);
    }

    @Override
    public void handle(ServerPlayer player) {
        player.server.execute(() -> RouteGuidanceManager.apply(player, destinationName, autoSteer));
    }
}
