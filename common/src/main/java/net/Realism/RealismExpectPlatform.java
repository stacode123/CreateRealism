package net.Realism;


import com.tterrag.registrate.builders.MenuBuilder;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RealismExpectPlatform {
    @ExpectPlatform
    public static String platformName() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isForge() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isModLoaded(String id) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Object getNetworkHook() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void openAdvancedScheduleScreen(ServerPlayer player, ItemStack heldItem) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static AbstractContainerMenu createAdvancedScheduleMenu(int id, Inventory inv, ItemStack heldItem) {
        throw new AssertionError();
    }
    @ExpectPlatform
    public static <C extends AbstractContainerMenu, S extends Screen & MenuAccess<C>> MenuBuilder.ScreenFactory<C, S> getAdvancedScheduleScreenFactory() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static <C extends AbstractContainerMenu> MenuBuilder.ForgeMenuFactory<C> getAdvancedScheduleMenuFactory() {
        throw new AssertionError();
    }
}
