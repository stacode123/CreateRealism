package net.Realism.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class RealismConfig {
    // Common Config
    public static class Common {
        public final  ForgeConfigSpec.BooleanValue GlobalETCSEnable;
        public final  ForgeConfigSpec.BooleanValue GlobalBankingEnable;
        public final ForgeConfigSpec.BooleanValue EnableCustomTrainAcceleration;
        public final ForgeConfigSpec.DoubleValue CustomTrainAccelerationMultiplyer;
        public final ForgeConfigSpec.BooleanValue AllowBiggerValuesTrains;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            builder.push("Custom train acceleration");
            EnableCustomTrainAcceleration = builder.comment("Enable custom train acceleration(Custom and Standard)")
                    .define("Enable Custom Train Acceleration", true);
            CustomTrainAccelerationMultiplyer = builder.comment("Multiplier for custom train acceleration(Higher = slower acceleration per Carriage)(For trains set on Standard)")
                    .defineInRange("Custom Train Acceleration Multiplayer", 1.0, 0.1, 5.0);
            AllowBiggerValuesTrains = builder.comment("Allow players to set custom acceleration values larger than default(For trains set on Custom)")
                    .define("Allow Large Acceleration", true);
            builder.pop();
            builder.push("Banking");
            GlobalBankingEnable = builder.comment("Global enable of ALL train banking")
                    .define("Global Banking Enable", true);
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
        public final ForgeConfigSpec.BooleanValue ETCSMPH;

        public final ForgeConfigSpec.BooleanValue OverlayEnable;
        public final ForgeConfigSpec.BooleanValue OverlayMPH;

        // Banking Configuration
        public final ForgeConfigSpec.BooleanValue enableBanking;
        public final ForgeConfigSpec.BooleanValue enablePlayerTilt;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            builder.push("Overlay");
            OverlayEnable = builder.comment("Enable Track Placing Overlay").define("Enable Overlay", true);
            OverlayMPH = builder.comment("Use MPH For The Overlay").define("Use mph", false);
            builder.pop();
            builder.push("ETCS");
            ETCSEnable = builder.comment("Enable ETCS for trains")
                    .define("ETCS Enable", true);
            ETCSSize = builder.comment("Size of the ETCS display")
                    .defineInRange("ETCS Size", 0.25, 0.1, 2);
            ETCSMPH = builder.comment("Use Mph in ETCS").define("ETCS MPH", false);
            ETCSSounds = builder.comment("Enable ETCS sounds")
                    .define("ETCS Sounds", true);
            builder.pop();

            builder.push("Banking");
            enableBanking = builder.comment("Enable banking rendering (roll rotation) on curved tracks")
                    .define("Enable Banking", true);
            enablePlayerTilt = builder.comment("Rotate player camera with the train(Forge only)")
                    .define("Enable Player Tilt", true);
            builder.pop();
            debugMode = builder.comment("Enable debug mode")
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