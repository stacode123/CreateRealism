// forge/src/main/java/net/Realism/forge/config/ForgeConfigRegistration.java
package net.Realism.forge.config;

import net.Realism.RealismMod;
import net.Realism.config.RealismConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class ForgeConfigRegistration {
    public static void register() {
        RealismMod.LOGGER.info("Registering configs with Forge Config API");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON, RealismConfig.COMMON_SPEC, RealismMod.MOD_ID + "-common.toml");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT, RealismConfig.CLIENT_SPEC, RealismMod.MOD_ID + "-client.toml");
    }
}