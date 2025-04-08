package net.Realism.network;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.Realism.RealismMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class RealismPackets {
    private static final Map<ResourceLocation, PacketEntry<?>> PACKETS = new HashMap<>();

    public static final ResourceLocation ETCS_SYNC = RealismMod.id("etcs_sync");

    public static void registerPackets() {
        register(ETCS_SYNC, ETCSSyncPacket::new, ETCSSyncPacket::write);
    }

    private static <T extends SimplePacketBase> void register(ResourceLocation id, Function<FriendlyByteBuf, T> factory, BiConsumer<T, FriendlyByteBuf> encoder) {
        PacketEntry<T> entry = new PacketEntry<>(factory, encoder);
        PACKETS.put(id, entry);

        // Register server->client packet
        ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buf, responseSender) -> {
            T packet = entry.factory.apply(buf);
            client.execute(() -> {
                // Instead of creating a Context object, call the Client packet handler directly
                if (packet instanceof ETCSSyncPacket etcsPacket) {
                    etcsPacket.handleClient(client);
                }
            });
        });
    }

    public static <T extends SimplePacketBase> void sendToClient(T packet, ServerPlayer player) {
        for (Map.Entry<ResourceLocation, PacketEntry<?>> entry : PACKETS.entrySet()) {
            PacketEntry<T> typedEntry = (PacketEntry<T>) entry.getValue();
            if (typedEntry.matches(packet)) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                typedEntry.encoder.accept(packet, buf);
                ServerPlayNetworking.send(player, entry.getKey(), buf);
                return;
            }
        }
        
        throw new IllegalArgumentException("Unknown packet type: " + packet.getClass().getName());
    }

    public static <T extends SimplePacketBase> void sendToAllClients(T packet, MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(player -> sendToClient(packet, player));
    }

    private static class PacketEntry<T extends SimplePacketBase> {
        private final Function<FriendlyByteBuf, T> factory;
        private final BiConsumer<T, FriendlyByteBuf> encoder;

        public PacketEntry(Function<FriendlyByteBuf, T> factory, BiConsumer<T, FriendlyByteBuf> encoder) {
            this.factory = factory;
            this.encoder = encoder;
        }

        @SuppressWarnings("unchecked")
        public boolean matches(SimplePacketBase packet) {
            return true; // We'll use map lookup to determine type
        }
    }
}
