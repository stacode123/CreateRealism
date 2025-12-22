package net.Realism.foundation.network;

import net.Realism.foundation.util.S2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;

import java.util.UUID;


public class RollSyncPacket implements S2CPacket {
    private final float roll;
    private final float prevRoll;
    private final UUID UUID;

    public RollSyncPacket(float roll, float prevRoll, UUID UUID) {
        this.roll = roll;
        this.prevRoll = prevRoll;
        this.UUID = UUID;
    }
    public static RollSyncPacket read(FriendlyByteBuf buf) {
        return new RollSyncPacket(buf.readFloat(), buf.readFloat(),buf.readUUID());
    }
    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(roll);
        buf.writeFloat(prevRoll);
        buf.writeUUID(UUID);
    }

    @Override
    public void handle(Minecraft mc) {
        mc.execute(() -> {
            if (mc.level == null) return;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(UUID)) {
                    if (entity instanceof net.Realism.Interfaces.IOrientedContraptionEntity orientedEntity) {
                        orientedEntity.realism$setRoll(roll);
                        orientedEntity.realism$setPrevRoll(prevRoll);
                    }
                    break;
                }
            }
        });

    }
}
