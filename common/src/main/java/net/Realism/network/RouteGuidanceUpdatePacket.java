package net.Realism.network;

import net.Realism.trains.RouteGuidanceManager;
import net.Realism.util.S2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Server -> client: echo the accepted route, or reject it when no forward path exists.
 * Only ever sent to the player that requested the change.
 */
public class RouteGuidanceUpdatePacket implements S2CPacket {

    private final String destinationName;
    private final boolean autoSteer;
    private final boolean valid;

    public RouteGuidanceUpdatePacket(String destinationName, boolean autoSteer, boolean valid) {
        this.destinationName = destinationName == null ? "" : destinationName;
        this.autoSteer = autoSteer;
        this.valid = valid;
    }

    public static RouteGuidanceUpdatePacket read(FriendlyByteBuf buf) {
        return new RouteGuidanceUpdatePacket(buf.readUtf(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(destinationName);
        buf.writeBoolean(autoSteer);
        buf.writeBoolean(valid);
    }

    @Override
    public void handle(Minecraft mc) {
        mc.execute(() -> {
            if (mc.player == null) return;

            if (!valid) {
                RouteGuidanceManager.clearClient(mc.player.getUUID());
                mc.player.displayClientMessage(
                        Component.translatable("realism.route_guidance.no_route", destinationName), true);
                return;
            }

            RouteGuidanceManager.setClient(mc.player.getUUID(), destinationName, autoSteer);
        });
    }
}
