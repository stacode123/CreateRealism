package net.Realism.neoforge.config;

import net.Realism.RealismMod;
import net.Realism.config.RealismConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;

public class ForgeConfigRegistration {
    public static void register() {
        RealismMod.LOGGER.info("Registering configs with NeoForge Config API");
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        container.registerConfig(ModConfig.Type.COMMON, RealismConfig.COMMON_SPEC, RealismMod.MOD_ID + "-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, RealismConfig.CLIENT_SPEC, RealismMod.MOD_ID + "-client.toml");
    }
}

