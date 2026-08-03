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
        public final ForgeConfigSpec.IntValue GraphNodeCap;
        public final ForgeConfigSpec.IntValue SimMaxHorizonHours;
        public final ForgeConfigSpec.IntValue SimCooldownSeconds;
        public final ForgeConfigSpec.IntValue SimMaxConcurrent;
        public final ForgeConfigSpec.IntValue SimMaxWallSeconds;
        public final ForgeConfigSpec.IntValue SimHeadwaySeconds;
        public final ForgeConfigSpec.IntValue SimWaitConflictSeconds;
        public final ForgeConfigSpec.BooleanValue SimDebugExport;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> SimDiagramHiddenCategories;

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
            builder.push("Advanced Schedule");
            GraphNodeCap = builder.comment("Maximum track node count a rail network may have to be translated for the map viewer/simulator")
                    .defineInRange("Graph Node Cap", 4000, 100, 100000);
            SimMaxHorizonHours = builder.comment("Longest timetable simulation a player may request, in in-game hours (1000 ticks each)")
                    .defineInRange("Sim Max Horizon Hours", 48, 1, 336);
            SimCooldownSeconds = builder.comment("Seconds a player must wait between simulation requests")
                    .defineInRange("Sim Cooldown Seconds", 10, 0, 3600);
            SimMaxConcurrent = builder.comment("Maximum simulations running at the same time across all players")
                    .defineInRange("Sim Max Concurrent", 2, 1, 8);
            SimMaxWallSeconds = builder.comment("Real-time seconds a single simulation may compute before its results are cut off")
                    .defineInRange("Sim Max Wall Seconds", 10, 1, 120);
            SimHeadwaySeconds = builder.comment("Default minimum gap in seconds between consecutive trains through a track section before a headway conflict is reported; players can override per run. 0 disables the flat threshold (CRN separation conditions still apply)")
                    .defineInRange("Sim Headway Seconds", 10, 0, 600);
            SimWaitConflictSeconds = builder.comment("Seconds a simulated train may wait at a red signal before a section conflict is reported; 0 disables wait conflicts")
                    .defineInRange("Sim Wait Conflict Seconds", 30, 0, 600);
            SimDebugExport = builder.comment("Write a self-contained HTML playback viewer (realism-sim-debug.html in the server/save directory) after every simulation — a debugging tool")
                    .define("Sim Debug Export", false);
            SimDiagramHiddenCategories = builder.comment("Trains whose CRN train category name contains any of these words (case-insensitive) are hidden from the time-distance diagram, e.g. [\"bus\"]")
                    .defineListAllowEmpty(java.util.List.of("Sim Diagram Hidden Categories"),
                            java.util.List::of, element -> element instanceof String);
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