package net.Realism.fabric;

import io.github.fabricators_of_create.porting_lib.util.EnvExecutor;
import net.Realism.RealismMod;
import net.Realism.fabric.config.FabricConfigRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;

import static net.Realism.RealismMod.commonSetup;
import static net.Realism.fabric.RNetworkingImpl.clientInit;
import static net.Realism.fabric.RNetworkingImpl.serverInit;

public class RealismFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RealismMod.init();
        RealismMod.LOGGER.info(EnvExecutor.unsafeRunForDist(
                () -> () -> "{} is accessing Porting Lib on a Fabric client!",
                () -> () -> "{} is accessing Porting Lib on a Fabric server!"
        ), RealismMod.NAME);
        RealismMod.REGISTRATE.register();
        FabricConfigRegistration.register();
        commonSetup();
        serverInit();
        CommonEventsImpl.register();
        com.tterrag.registrate.fabric.EnvExecutor.runWhenOn(EnvType.CLIENT, () -> () -> {
            clientInit();
        });
    }
}
