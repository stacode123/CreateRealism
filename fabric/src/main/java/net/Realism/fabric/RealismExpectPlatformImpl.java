package net.Realism.fabric;

import com.tterrag.registrate.builders.MenuBuilder;
import net.Realism.content.trains.schedule.AdvancedScheduleMenu;
import net.Realism.content.trains.schedule.AdvancedScheduleScreen;
import net.Realism.foundation.util.AllMenuTypes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RealismExpectPlatformImpl {
	public static String platformName() {
		return "Fabric";
	}

	public static boolean isForge() {
		return false;
	}

    public static boolean isModLoaded(String id) {
        return  FabricLoader.getInstance().isModLoaded(id);
    }

    public static void openAdvancedScheduleScreen(ServerPlayer player, ItemStack heldItem) {
        net.Realism.RNetworking.sendToPlayer(new net.Realism.foundation.network.OpenSimulatedSchedulePacket(heldItem), player);
    }

    public static AdvancedScheduleMenu createAdvancedScheduleMenu(int id, Inventory inv, ItemStack heldItem) {
        return new AdvancedScheduleMenu(AllMenuTypes.ADVANCED_SCHEDULE.get(), id, inv, heldItem);
    }

	@SuppressWarnings("unchecked")
    public static <C extends AbstractContainerMenu, S extends Screen & MenuAccess<C>> MenuBuilder.ScreenFactory<C, S> getAdvancedScheduleScreenFactory() {
		return (MenuBuilder.ScreenFactory<C, S>) (Object) (MenuBuilder.ScreenFactory<AdvancedScheduleMenu, AdvancedScheduleScreen>) AdvancedScheduleScreen::new;
    }

	@SuppressWarnings("unchecked")
    public static <C extends AbstractContainerMenu> MenuBuilder.ForgeMenuFactory<C> getAdvancedScheduleMenuFactory() {
		return (MenuBuilder.ForgeMenuFactory<C>) (Object) (MenuBuilder.ForgeMenuFactory<AdvancedScheduleMenu>) AdvancedScheduleMenu::new;
    }
}