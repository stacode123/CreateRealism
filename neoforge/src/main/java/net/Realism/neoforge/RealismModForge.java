package net.Realism.neoforge;

import net.Realism.RealismMod;
import net.Realism.neoforge.config.ForgeConfigRegistration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(RealismMod.MOD_ID)
public class RealismModForge {
    public RealismModForge(IEventBus modEventBus, ModContainer container) {
        RealismMod.init();
        if (FMLEnvironment.dist == Dist.CLIENT)
            RealismMod.clientSetup();
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