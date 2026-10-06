// forge/src/main/java/net/Realism/forge/config/ForgeConfigRegistration.java
package net.Realism.forge.config;

import net.Realism.RealismMod;
import net.Realism.config.RealismConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class ForgeConfigRegistration {
    public static void register() {
        RealismMod.LOGGER.info("Registering configs with Forge Config API");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON, RealismConfig.COMMON_SPEC, RealismMod.MOD_ID + "-common.toml");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT, RealismConfig.CLIENT_SPEC, RealismMod.MOD_ID + "-client.toml");
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ForgeConfigRegistration::registerConfigScreen);
    }

    private static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new RealismConfigScreen(parent)));
    }
}