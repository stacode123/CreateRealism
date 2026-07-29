package net.Realism.foundation.network;

import com.simibubi.create.content.trains.schedule.Schedule;
import net.Realism.RealismMod;
import net.Realism.content.trains.schedule.AdvancedScheduleItem;
import net.Realism.foundation.util.C2SPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Saves an edited schedule onto the Advanced Schedule item in the sender's
 * main hand. The schedule is round-tripped through {@link Schedule#fromTag}
 * and {@link Schedule#write} server-side, and only the {@code Schedule}
 * sub-tag is ever written — the rest of the item tag is untouched.
 */
public class AdvancedScheduleSavePacket implements C2SPacket {
    /** Hard cap on the incoming schedule NBT, enforced while decoding. */
    private static final long MAX_TAG_BYTES = 262144;

    private final CompoundTag scheduleTag;

    public AdvancedScheduleSavePacket(Schedule schedule) {
        this.scheduleTag = schedule.write();
    }

    private AdvancedScheduleSavePacket(CompoundTag scheduleTag) {
        this.scheduleTag = scheduleTag;
    }

    public static AdvancedScheduleSavePacket read(FriendlyByteBuf buf) {
        return new AdvancedScheduleSavePacket(buf.readNbt(new NbtAccounter(MAX_TAG_BYTES)));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(scheduleTag);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (scheduleTag == null)
            return;
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof AdvancedScheduleItem))
            return;

        Schedule schedule;
        CompoundTag sanitized;
        try {
            schedule = Schedule.fromTag(scheduleTag);
            sanitized = schedule.write();
        } catch (Exception e) {
            RealismMod.LOGGER.error("Rejected unreadable schedule from {}", player.getGameProfile().getName(), e);
            return;
        }

        CompoundTag tag = mainHandItem.getOrCreateTag();
        if (schedule.entries.isEmpty()) {
            tag.remove("Schedule");
            if (tag.isEmpty())
                mainHandItem.setTag(null);
        } else
            tag.put("Schedule", sanitized);

        player.getCooldowns()
                .addCooldown(mainHandItem.getItem(), 5);
    }
}
