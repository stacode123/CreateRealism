package net.Realism;


import com.simibubi.create.foundation.data.CreateRegistrate;
import net.Realism.foundation.util.AllMenuTypes;
import net.Realism.foundation.util.AllRealismItems;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealismMod {
    public static final String MOD_ID = "realism";
    public static final String NAME = "Create Realism";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public static void init() {
        RealismSounds.SOUND_EVENTS.register();
    }

    public static void commonSetup() {
        RNetworking.register();
        RExtras.Schedule.register();
        AllRealismItems.register();
        AllMenuTypes.register();
    }




    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}


