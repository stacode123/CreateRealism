package net.Realism.network;

import net.Realism.trains.RouteGuidanceManager;
import net.Realism.trains.etcs.ETCSMessages;
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
    private final boolean valid;
    private final boolean throttled;

    public RouteGuidanceUpdatePacket(String destinationName, boolean valid) {
        this(destinationName, valid, false);
    }

    private RouteGuidanceUpdatePacket(String destinationName, boolean valid, boolean throttled) {
        this.destinationName = destinationName == null ? "" : destinationName;
        this.valid = valid;
        this.throttled = throttled;
    }

    public static RouteGuidanceUpdatePacket throttled(String currentDestination) {
        boolean valid = currentDestination != null && !currentDestination.isBlank();
        return new RouteGuidanceUpdatePacket(currentDestination, valid, true);
    }

    public static RouteGuidanceUpdatePacket read(FriendlyByteBuf buf) {
        return new RouteGuidanceUpdatePacket(buf.readUtf(256), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(destinationName, 256);
        buf.writeBoolean(valid);
        buf.writeBoolean(throttled);
    }

    @Override
    public void handle(Minecraft mc) {
        mc.execute(() -> {
            if (mc.player == null) return;

            if (throttled) {
                if (valid) {
                    RouteGuidanceManager.setClient(mc.player.getUUID(), destinationName);
                } else {
                    RouteGuidanceManager.clearClient(mc.player.getUUID());
                }
                ETCSMessages.push("route_throttled", ETCSMessages.Severity.WARNING,
                        Component.translatable("realism.route_guidance.throttled").getString(), 2000);
                return;
            }

            if (!valid) {
                RouteGuidanceManager.clearClient(mc.player.getUUID());
                ETCSMessages.push("no_route", ETCSMessages.Severity.WARNING,
                        Component.translatable("realism.route_guidance.no_route", destinationName).getString(), 5000);
                return;
            }

            RouteGuidanceManager.setClient(mc.player.getUUID(), destinationName);
        });
    }
}
