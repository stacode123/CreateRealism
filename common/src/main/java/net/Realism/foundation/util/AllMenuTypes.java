package net.Realism.foundation.util;

import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.tterrag.registrate.builders.MenuBuilder;
import com.tterrag.registrate.util.entry.MenuEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.Realism.RealismMod;
import net.Realism.content.trains.schedule.AdvancedScheduleMenu;
import net.Realism.content.trains.schedule.AdvancedScheduleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class AllMenuTypes {

    public static final MenuEntry<ScheduleMenu> ADVANCED_SCHEDULE =
            register("advanced_schedule", AdvancedScheduleMenu::new, () -> AdvancedScheduleScreen::new);


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
