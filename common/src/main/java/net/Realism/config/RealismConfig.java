package net.Realism.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class RealismConfig {
    // Common Config
    public static class Common {
        public final  ForgeConfigSpec.BooleanValue GlobalETCSEnable;
        public final ForgeConfigSpec.BooleanValue EnableCustomTrainAcceleration;
        public final  ForgeConfigSpec.DoubleValue CustomTrainAccelerationMultiplyer;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            builder.push("Custom train acceleration");
            EnableCustomTrainAcceleration = builder.comment("Enable custom train acceleration based on the number of carriages")
                    .define("Enable Custom Train Acceleration", true);
            CustomTrainAccelerationMultiplyer = builder.comment("Multiplier for custom train acceleration(Higher = slower acceleration per Carrige)")
                    .defineInRange("Custom Train Acceleration Multiplyer", 1.0, 0.1, 5.0);
            builder.pop();
            builder.push("ETCS");
            GlobalETCSEnable = builder.comment("Enable ETCS for all trains")
                    .define("Global ETCS Enable", true);
            builder.pop();
            builder.pop();
        }
    }

    // Client Config
    public static class Client {
        public final ForgeConfigSpec.BooleanValue debugMode;
        public final ForgeConfigSpec.BooleanValue ETCSEnable;
        public final ForgeConfigSpec.DoubleValue ETCSSize;


        Client(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            builder.push("ETCS");
            ETCSEnable = builder.comment("Enable ETCS for trains")
                    .define("ETCS Enable", true);
            ETCSSize = builder.comment("Size of the ETCS display")
                    .defineInRange("ETCS Size", 0.25, 0.1, 2);
            builder.pop();
            debugMode = builder.comment("Enable debug mode to see the modified acceleration")
                    .define("debugMode", false);
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