package net.Realism.foundation.network;

import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLScreen;
import net.Realism.content.gui.schedule.SimulatedScheduleWindow;
import net.Realism.foundation.util.S2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record OpenSimulatedSchedulePacket(ItemStack stack) implements S2CPacket {

    public static OpenSimulatedSchedulePacket read(FriendlyByteBuf buf) {
        return new OpenSimulatedSchedulePacket(buf.readItem());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeItem(stack);
    }

    @Override
    public void handle(Minecraft mc) {
        //Inventory playerInv = Minecraft.getInstance().player.getInventory();
        //PlayerInventoryContainerMenu.Base menu = new PlayerInventoryContainerMenu.Base(0, playerInv);
        //Minecraft.getInstance().setScreen(new DLScreen<>(menu, (manager) -> new SimulatedScheduleWindow(manager, stack)));
        //var player = Minecraft.getInstance().player;
        //PlayerInventoryContainerMenu menu = new PlayerInventoryContainerMenu.Base(0, player.getInventory());

        DLScreen<?> screen = new DLScreen<>(null, (manager) -> new SimulatedScheduleWindow(manager, stack));
        Minecraft.getInstance().setScreen(screen);
        //DLWindow.openWindow(manager -> new SimulatedScheduleWindow(manager, stack));
    }
}
