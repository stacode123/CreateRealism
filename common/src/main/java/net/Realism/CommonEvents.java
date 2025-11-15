package net.Realism;

import net.minecraft.server.level.ServerPlayer;

public class CommonEvents {
    public static void onPlayerJoin(ServerPlayer player) {
        RNetworking.onPlayerJoin(player);
    }

}
