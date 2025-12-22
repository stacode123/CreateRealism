package net.Realism.forge;

import dev.architectury.platform.forge.EventBuses;
import net.Realism.RealismMod;
import net.Realism.forge.config.ForgeConfigRegistration;
import net.Realism.foundation.commands.RealismCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(RealismMod.MOD_ID)
public class RealismModForge {
    public RealismModForge() {
        // registrate must be given the mod event bus on forge before registration
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(RealismMod.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        RealismMod.init();
        eventBus.addListener(this::commonSetup);
        ForgeConfigRegistration.register();
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RealismCommands.register(event.getDispatcher());
    }
    public void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RNetworkingImpl.init();
            RealismMod.commonSetup();
        });

    }


}
