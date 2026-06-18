package net.Realism.neoforge;

import net.Realism.RealismMod;
import net.Realism.neoforge.config.ForgeConfigRegistration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(RealismMod.MOD_ID)
public class RealismModForge {
    public RealismModForge(IEventBus modEventBus, ModContainer container) {
        RealismMod.init();
        modEventBus.addListener(this::commonSetup);
        ForgeConfigRegistration.register();
    }
    public void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RNetworkingImpl.init();
            RealismMod.commonSetup();
        });
    }
}