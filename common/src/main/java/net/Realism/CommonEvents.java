package net.Realism;

import net.Realism.trains.RouteGuidanceManager;
import net.minecraft.server.level.ServerPlayer;

public class CommonEvents {
    public static void onPlayerJoin(ServerPlayer player) {
        RNetworking.onPlayerJoin(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        RouteGuidanceManager.clearServer(player.getUUID());
    }
}
