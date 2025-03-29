package net.Realism.forge;

import net.Realism.RealismBlocks;
import net.Realism.RealismMod;
import net.Realism.forge.config.ForgeConfigRegistration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(RealismMod.MOD_ID)
public class RealismModForge {
    public RealismModForge() {
        // registrate must be given the mod event bus on forge before registration
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        RealismBlocks.REGISTRATE.registerEventListeners(eventBus);
        RealismMod.init();
        ForgeConfigRegistration.register();
    }
}
