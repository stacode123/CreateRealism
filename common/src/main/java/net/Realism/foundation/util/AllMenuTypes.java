package net.Realism.foundation.util;

import com.tterrag.registrate.builders.MenuBuilder;
import com.tterrag.registrate.util.entry.MenuEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.Realism.RealismExpectPlatform;
import net.Realism.RealismMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class AllMenuTypes {

    public static final MenuEntry<AbstractContainerMenu> ADVANCED_SCHEDULE =
            register("advanced_schedule", RealismExpectPlatform.getAdvancedScheduleMenuFactory(), () -> RealismExpectPlatform.getAdvancedScheduleScreenFactory());


    private static <C extends AbstractContainerMenu, S extends Screen & MenuAccess<C>> MenuEntry<C> register(
            String name, MenuBuilder.ForgeMenuFactory<C> factory, NonNullSupplier<MenuBuilder.ScreenFactory<C, S>> screenFactory) {
        return RealismMod.REGISTRATE
                .menu(name, factory, screenFactory)
                .register();
    }

    public static void register() {
        RealismMod.LOGGER.debug("Registering Menu Types for " + RealismMod.NAME);
    }

}



