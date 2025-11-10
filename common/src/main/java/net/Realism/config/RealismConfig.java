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
        public final ForgeConfigSpec.BooleanValue ETCSSounds;

        // Banking Configuration
        public final ForgeConfigSpec.BooleanValue enableBanking;
        public final ForgeConfigSpec.BooleanValue enablePlayerTilt;
        public final ForgeConfigSpec.DoubleValue maxBankingAngle;
        public final ForgeConfigSpec.DoubleValue bankingMinSpeed;
        public final ForgeConfigSpec.DoubleValue bankingIntensity;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            builder.push("ETCS");
            ETCSEnable = builder.comment("Enable ETCS for trains")
                    .define("ETCS Enable", true);
            ETCSSize = builder.comment("Size of the ETCS display")
                    .defineInRange("ETCS Size", 0.25, 0.1, 2);
            ETCSSounds = builder.comment("Enable ETCS sounds")
                    .define("ETCS Sounds", true);
            builder.pop();

            builder.push("Banking");
            enableBanking = builder.comment("Enable banking (roll rotation) on curved tracks")
                    .define("Enable Banking", true);
            enablePlayerTilt = builder.comment("Enable player camera and model tilting when riding on banked trains")
                    .define("Enable Player Tilt", true);
            maxBankingAngle = builder.comment("Maximum banking angle in degrees")
                    .defineInRange("Max Banking Angle", 15.0, 0.0, 30.0);
            bankingMinSpeed = builder.comment("Minimum speed (kmn/h) for full banking effect")
                    .defineInRange("Banking Min Speed", 80.0, 0, 300);
            bankingIntensity = builder.comment("Banking intensity multiplier")
                    .defineInRange("Banking Intensity", 1.0, 0.0, 2.0);
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