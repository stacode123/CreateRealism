package net.Realism.forge;

import net.Realism.RealismMod;
import net.Realism.util.C2SPacket;
import net.Realism.RNetworking;
import net.Realism.util.S2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class RNetworkingImpl {
    private static final SimpleChannel net =
            NetworkRegistry.ChannelBuilder.named(RealismMod.id("net"))
                    .networkProtocolVersion(() -> "null")
                    .clientAcceptedVersions((s) -> true)
                    .serverAcceptedVersions((s) -> true)
                    .simpleChannel();

    private record ForgePacket(FriendlyByteBuf message) {
        public static ForgePacket read(FriendlyByteBuf buf) {
            int length = buf.readVarInt();
            FriendlyByteBuf message = new FriendlyByteBuf(buf.readBytes(length));
            return new ForgePacket(message);
        }

        public void write(FriendlyByteBuf buf) {
            byte[] array = message.array();
            buf.writeVarInt(array.length);
            buf.writeBytes(array);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            switch (context.get().getDirection()) {
                case PLAY_TO_CLIENT -> RNetworking.handleInternal(message, Minecraft.getInstance());
                case PLAY_TO_SERVER -> RNetworking.handleInternal(message, context.get().getSender());
            }
        }
    }

    public static void init() {
        net.messageBuilder(ForgePacket.class, 0)
                .encoder(ForgePacket::write)
                .decoder(ForgePacket::read)
                .consumerMainThread(ForgePacket::handle)
                .add();
    }

    public static <T extends S2CPacket> void sendToAll(T message) {
        RNetworking.sendInternal(message, (buf) ->
                net.send(PacketDistributor.ALL.noArg(), new ForgePacket(buf))
        );
    }

    public static <T extends S2CPacket> void sendToNear(T message, Vec3 pos, int range, ResourceKey<Level> dimension) {
        RNetworking.sendInternal(message, (buf) ->
                net.send(
                        PacketDistributor.NEAR.with(
                                PacketDistributor.TargetPoint.p(pos.x, pos.y, pos.z, range, dimension)
                        ),
                        new ForgePacket(buf)
                )
        );
    }

    public static <T extends S2CPacket> void sendToPlayer(T message, ServerPlayer player) {
        RNetworking.sendInternal(message, (buf) ->
                net.send(PacketDistributor.PLAYER.with(() -> player), new ForgePacket(buf))
        );
    }

    public static <T extends C2SPacket> void sendToServer(T message) {
        RNetworking.sendInternal(message, (buf) ->
                net.sendToServer(new ForgePacket(buf))
        );
    }
}
