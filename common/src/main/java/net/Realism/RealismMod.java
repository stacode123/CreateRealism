package net.Realism;

import com.mojang.datafixers.TypeRewriteRule;
import com.simibubi.create.AllPackets;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.Realism.network.RealismPackets;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.Realism.config.RealismConfig;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static com.simibubi.create.foundation.networking.SimplePacketBase.NetworkDirection.PLAY_TO_CLIENT;

public class RealismMod {
    public static final String MOD_ID = "realism";
    public static final String NAME = "Create Realism";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public static void init() {
        LOGGER.info("{} initializing! Create version: {} on platform: {}", NAME, Create.VERSION, RealismExpectPlatform.platformName());
        RealismBlocks.init();
        RealismPackets.registerPackets();
    }



    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
