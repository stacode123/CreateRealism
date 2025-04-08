package net.Realism.fabric;

import io.github.fabricators_of_create.porting_lib.util.ServerLifecycleHooks;
import net.Realism.RNetworking;
import net.Realism.RealismMod;
import net.Realism.util.S2CPacket;
import net.Realism.util.C2SPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;


public class RNetworkingImpl {
    private static final ResourceLocation fabricChannel = RealismMod.id("net");

    @Environment(EnvType.CLIENT)
    public static void clientInit() {
        ClientPlayNetworking.registerGlobalReceiver(fabricChannel, (mc, listener, buf, sender) ->
                RNetworking.handleInternal(buf, mc)
        );
    }

    public static void serverInit() {
        ServerPlayNetworking.registerGlobalReceiver(fabricChannel, (server, player, listener, buf, sender) ->
                RNetworking.handleInternal(buf, player)
        );
    }

    public static <T extends S2CPacket> void sendToAll(T message) {
        RNetworking.sendInternal(message, (buf) ->
                PlayerLookup.all(ServerLifecycleHooks.getCurrentServer())
                        .forEach((player) -> ServerPlayNetworking.send(player, fabricChannel, buf))
        );
    }

    public static <T extends S2CPacket> void sendToNear(T message, Vec3 pos, int range, ResourceKey<Level> dimension) {
        RNetworking.sendInternal(message, (buf) ->
                PlayerLookup.around(ServerLifecycleHooks.getCurrentServer().getLevel(dimension), pos, range)
                        .forEach((player) -> ServerPlayNetworking.send(player, fabricChannel, buf))
        );
    }

    public static <T extends S2CPacket> void sendToPlayer(T message, ServerPlayer player) {
        RNetworking.sendInternal(message, (buf) ->
                ServerPlayNetworking.send(player, fabricChannel, buf)
        );
    }

    public static <T extends C2SPacket> void sendToServer(T message) {
        RNetworking.sendInternal(message, (buf) ->
                ClientPlayNetworking.send(fabricChannel, buf)
        );
    }
}
