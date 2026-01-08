package net.Realism.foundation.network;

import net.Realism.foundation.util.C2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SteerDirectionPacket implements C2SPacket {
    // Define an enum for the key press types
    public enum KeyPressType {
        PLUS,
        MINUS,
        NONE
    }

    // Static map to store player key presses
    private static final Map<UUID, KeyPressType> playerKeyPresses = new ConcurrentHashMap<>();

    // The key being pressed in this packet
    private final KeyPressType keyPress;

    public SteerDirectionPacket(KeyPressType keyPress) {
        this.keyPress = keyPress;
    }

    // Constructor for deserialization
    public SteerDirectionPacket(FriendlyByteBuf buf) {
        this.keyPress = buf.readEnum(KeyPressType.class);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(keyPress);
    }

    @Override
    public void handle(ServerPlayer player) {
        // Store this player's key press in the map
        playerKeyPresses.put(player.getUUID(), keyPress);
    }

    public static SteerDirectionPacket read(FriendlyByteBuf buffer) {
        KeyPressType keys = buffer.readEnum(KeyPressType.class);
        return new SteerDirectionPacket(keys);

    }

    // Static method to get a player's current key press state
    public static KeyPressType getPlayerKeyPress(UUID playerId) {
        return playerKeyPresses.getOrDefault(playerId, KeyPressType.NONE);
    }
    public static void  setPlayerKeyPresses(UUID playerId, KeyPressType keyPress) {
        playerKeyPresses.put(playerId, keyPress);
    }
}
