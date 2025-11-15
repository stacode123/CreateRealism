package net.Realism.fabric;

import net.Realism.CommonEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class CommonEventsImpl {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
                CommonEvents.onPlayerJoin(listener.player)
        );
    }
}