package net.Realism.config;

import net.Realism.RealismMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public class RealismConfig {
    // Common Config
    public static class Common {
        //public final ForgeConfigSpec.BooleanValue enableFeature;
        //public final ForgeConfigSpec.IntValue maxProcessingAmount;
        //public final ForgeConfigSpec.DoubleValue difficultyMultiplier;
        public final ForgeConfigSpec.BooleanValue EnableCustomTrainAcceleration;
        public final  ForgeConfigSpec.DoubleValue CustomTrainAccelerationMultiplyer;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("general");

            //enableFeature = builder.comment("Enable the main feature of the mod").define("enableFeature", true);
            //maxProcessingAmount = builder.comment("Maximum amount of items that can be processed at once")
            //        .defineInRange("maxProcessingAmount", 64, 1, 512);
            //difficultyMultiplier = builder.comment("Multiplier that affects processing difficulty")
            //        .defineInRange("difficultyMultiplier", 1.0, 0.1, 5.0);
            EnableCustomTrainAcceleration = builder.comment("Enable custom train acceleration based on the number of carriages")
                    .define("Enable Custom Train Acceleration", true);
            CustomTrainAccelerationMultiplyer = builder.comment("Multiplier for custom train acceleration(Higher = slower acceleration per Carrige)")
                    .defineInRange("Custom Train Acceleration Multiplyer", 1.0, 0.1, 5.0);
            builder.pop();
        }
    }

    // Client Config
    public static class Client {
        //public final ForgeConfigSpec.BooleanValue showParticles;
        //public final ForgeConfigSpec.IntValue particleDensity;
        //public final ForgeConfigSpec.DoubleValue renderDistance;
        public final ForgeConfigSpec.BooleanValue debugMode;
        public final ForgeConfigSpec.IntValue Xpos;
        public final ForgeConfigSpec.IntValue Ypos;
        public final ForgeConfigSpec.DoubleValue rotate;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("visual");
            //showParticles = builder.comment("Enable particle effects").define("showParticles", true);
            //particleDensity = builder.comment("Density of particles (higher = more particles)")
            //        .defineInRange("particleDensity", 2, 0, 5);
            //renderDistance = builder.comment("Maximum distance to render special effects")
            //        .defineInRange("renderDistance", 32.0, 8.0, 64.0);
            debugMode = builder.comment("Enable debug mode to see the modified acceleration")
                    .define("debugMode", false);
            Xpos = builder.comment("X position of the debug overlay")
                    .defineInRange("Xpos", 10, -1000, 1000);
            Ypos = builder.comment("Y position of the debug overlay")
                    .defineInRange("Ypos", 10, -1000, 1000);
            rotate = builder.comment("Rotation of the debug overlay")
                    .defineInRange("rotate", 0.0, -360.0, 360.0);
            builder.pop();
        }
    }

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new Common(commonBuilder);
        COMMON_SPEC = commonBuilder.build();

        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }
}