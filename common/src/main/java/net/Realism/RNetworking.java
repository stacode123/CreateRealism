package net.Realism;

import dev.architectury.injectables.annotations.ExpectPlatform;
import io.netty.buffer.Unpooled;
import net.Realism.network.ETCSStartStopPacket;
import net.Realism.network.ETCSSyncPacket;
import net.Realism.network.SteerDirectionPacket;
import net.Realism.util.C2SPacket;
import net.Realism.util.S2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class RNetworking {
    private static final String VERSION = "4";
    private static int id = 0;

    private static final Map<Class<? extends C2SPacket>, Integer> c2sIdentifiers = new HashMap<>();
    private static final Map<Class<? extends S2CPacket>, Integer> s2cIdentifiers = new HashMap<>();
    private static final Map<Integer, Function<FriendlyByteBuf, ? extends C2SPacket>> c2sReaders = new HashMap<>();
    private static final Map<Integer, Function<FriendlyByteBuf, ? extends S2CPacket>> s2cReaders = new HashMap<>();

    private record CheckVersionS2CPacket(String serverVersion) implements S2CPacket {

        public static CheckVersionS2CPacket read(FriendlyByteBuf buf) {
                return new CheckVersionS2CPacket(buf.readUtf());
            }

            @Override
            public void write(FriendlyByteBuf buf) {
                buf.writeUtf(serverVersion);
            }

            @Override
            public void handle(Minecraft mc) {
                if (RNetworking.VERSION.equals(serverVersion))
                    return;

                mc.getConnection().onDisconnect(
                        Component.literal(
                                "Create: Tramways network versions do not match! Server expected %s, client has %s"
                                        .formatted(serverVersion, RNetworking.VERSION)
                        )
                );
            }
        }

    private static <T extends S2CPacket> void registerS2C(
            Class<T> clazz,
            Function<FriendlyByteBuf, T> read
    ) {
        int packetId = id++;
        s2cIdentifiers.put(clazz, packetId);
        s2cReaders.put(packetId, read);
    }

    private static <T extends C2SPacket> void registerC2S(
            Class<T> clazz,
            Function<FriendlyByteBuf, T> read
    ) {
        int packetId = id++;
        c2sIdentifiers.put(clazz, packetId);
        c2sReaders.put(packetId, read);
    }

    public static <T extends C2SPacket> void sendInternal(T message, Consumer<FriendlyByteBuf> consumer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(c2sIdentifiers.get(message.getClass()));
        message.write(buf);
        consumer.accept(buf);
    }

    public static <T extends S2CPacket> void sendInternal(T message, Consumer<FriendlyByteBuf> consumer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(s2cIdentifiers.get(message.getClass()));
        message.write(buf);
        consumer.accept(buf);
    }

    public static void handleInternal(FriendlyByteBuf buf, Minecraft mc) {
        int packetId = buf.readVarInt();
        S2CPacket packet = s2cReaders.get(packetId).apply(buf);
        mc.execute(() ->
                packet.handle(mc)
        );
    }

    public static void handleInternal(FriendlyByteBuf buf, ServerPlayer player) {
        int packetId = buf.readVarInt();
        C2SPacket packet = c2sReaders.get(packetId).apply(buf);
        player.server.execute(() ->
                packet.handle(player)
        );
    }

    public static void onPlayerJoin(ServerPlayer player) {
        sendToPlayer(new CheckVersionS2CPacket(RNetworking.VERSION), player);
    }

    @ExpectPlatform
    public static <T extends S2CPacket> void sendToAll(T message) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static <T extends S2CPacket> void sendToNear(T message, Vec3 pos, int range, ResourceKey<Level> dimension) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static <T extends S2CPacket> void sendToPlayer(T message, ServerPlayer player) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static <T extends C2SPacket> void sendToServer(T message) {
        throw new AssertionError();
    }

    public static void register() {
        registerS2C(
                CheckVersionS2CPacket.class,
                CheckVersionS2CPacket::read
        );
        registerS2C(
                ETCSSyncPacket.class,
                ETCSSyncPacket::read

        );
        registerC2S(
                SteerDirectionPacket.class
                , SteerDirectionPacket::read
        );
        registerC2S(
                ETCSStartStopPacket.class,
                ETCSStartStopPacket::read

        );

    }
}
