package net.Realism.foundation.network;

import net.Realism.foundation.util.C2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class SaveAdvancedSchedule implements C2SPacket {
    ItemStack stack;
    public SaveAdvancedSchedule(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeItem(stack);
    }

    @Override
    public void handle(ServerPlayer player) {
        player.getItemInHand(player.getUsedItemHand()).setTag(stack.getTag());
    }

    public static SaveAdvancedSchedule read(FriendlyByteBuf friendlyByteBuf) {
        return new SaveAdvancedSchedule(friendlyByteBuf.readItem());
    }
}
