package net.Realism.fabric;

import io.github.fabricators_of_create.porting_lib.util.EnvExecutor;
import net.Realism.RealismBlocks;
import net.Realism.RealismMod;
import net.Realism.fabric.config.FabricConfigRegistration;
import net.fabricmc.api.ModInitializer;

public class RealismFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RealismMod.init();
        RealismMod.LOGGER.info(EnvExecutor.unsafeRunForDist(
                () -> () -> "{} is accessing Porting Lib on a Fabric client!",
                () -> () -> "{} is accessing Porting Lib on a Fabric server!"
        ), RealismMod.NAME);
        // on fabric, Registrates must be explicitly finalized and registered.
        RealismBlocks.REGISTRATE.register();
        FabricConfigRegistration.register();
    }
}
