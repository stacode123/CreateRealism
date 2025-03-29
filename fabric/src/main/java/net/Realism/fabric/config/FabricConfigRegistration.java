// fabric/src/main/java/net/Realism/fabric/config/FabricConfigRegistration.java
package net.Realism.fabric.config;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.Realism.RealismMod;
import net.Realism.config.RealismConfig;
import net.minecraftforge.fml.config.ModConfig;

public class FabricConfigRegistration {
    public static void register() {
        RealismMod.LOGGER.info("Registering configs with ForgeConfigAPIPort");
        ForgeConfigRegistry.INSTANCE.register(
                RealismMod.MOD_ID, ModConfig.Type.COMMON, RealismConfig.COMMON_SPEC);
        ForgeConfigRegistry.INSTANCE.register(
                RealismMod.MOD_ID, ModConfig.Type.CLIENT, RealismConfig.CLIENT_SPEC);
    }
}