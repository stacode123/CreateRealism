package net.Realism.content.trains.schedule;

import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Create's schedule menu under our own {@link MenuType}, so the client opens
 * {@link AdvancedScheduleScreen} instead of Create's editor. All slot and
 * ghost-inventory behavior is inherited.
 */
public class AdvancedScheduleMenu extends ScheduleMenu {

    public AdvancedScheduleMenu(MenuType<?> type, int id, Inventory inv, FriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public AdvancedScheduleMenu(MenuType<?> type, int id, Inventory inv, ItemStack contentHolder) {
        super(type, id, inv, contentHolder);
    }
}
