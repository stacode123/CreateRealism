package net.Realism.foundation.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;
import net.Realism.RealismMod;
import net.Realism.content.graph.BlockGraph;
import net.Realism.content.graph.GraphTranslator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class RealismCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> realism = Commands.literal("realism")
                .requires(source -> source.hasPermission(2));

        realism.then(Commands.literal("translate")
                .then(Commands.argument("minNodes", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int minNodes = IntegerArgumentType.getInteger(context, "minNodes");
                            return translateGraphs(context.getSource(), minNodes);
                        }))
                .executes(context -> {
                    return translateGraphs(context.getSource(), 0);
                })
        );

        realism.then(Commands.literal("simulate")
                .then(Commands.literal("tick")
                        .executes(context -> {
                            RealismMod.SIMULATION_MANAGER.tick();
                            context.getSource().sendSuccess(() -> Component.literal("Simulation ticked."), true);
                            return 1;
                        }))
                .then(Commands.literal("results")
                        .executes(context -> {
                            RealismMod.SIMULATION_MANAGER.getTrains().forEach(train -> {
                                context.getSource().sendSuccess(() -> Component.literal("Train at " + train.edge.start.id + " -> " + train.edge.end.id), true);
                                context.getSource().sendSuccess(() -> Component.literal("Speed: " + String.format("%.2f", train.currentSpeed) + " (Target: " + String.format("%.2f", train.targetSpeed) + ")"), true);
                                context.getSource().sendSuccess(() -> Component.literal("Times between stops: " + train.timeBetweenStops), true);
                            });
                            return 1;
                        }))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("stationName", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String stationName = StringArgumentType.getString(context, "stationName");
                                    if (RealismMod.SIMULATION_MANAGER.spawnTrainAtStation(stationName)) {
                                        context.getSource().sendSuccess(() -> Component.literal("Spawned train at station " + stationName), true);
                                    } else {
                                        context.getSource().sendFailure(Component.literal("Station not found: " + stationName));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("export")
                        .executes(context -> {
                            RealismMod.SIMULATION_MANAGER.exportState("simulation_state.json");
                            context.getSource().sendSuccess(() -> Component.literal("Simulation state exported to simulation_state.json"), true);
                            return 1;
                        }))
                .then(Commands.literal("autoExport")
                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
                                    RealismMod.SIMULATION_MANAGER.setAutoExport(enabled);
                                    context.getSource().sendSuccess(() -> Component.literal("Auto-export " + (enabled ? "enabled" : "disabled")), true);
                                    return 1;
                                }))
                        .then(Commands.argument("interval", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int interval = IntegerArgumentType.getInteger(context, "interval");
                                    RealismMod.SIMULATION_MANAGER.setAutoExport(true);
                                    RealismMod.SIMULATION_MANAGER.setExportInterval(interval);
                                    context.getSource().sendSuccess(() -> Component.literal("Auto-export enabled with interval " + interval + " ticks"), true);
                                    return 1;
                                })))
                .then(Commands.literal("listStations")
                        .executes(context -> {
                            RealismMod.GRAPH_MANAGER.getGraphIds().forEach(graphId -> {
                                BlockGraph graph = RealismMod.GRAPH_MANAGER.getGraph(graphId);
                                if (graph != null) {
                                    graph.edges.forEach(edge -> {
                                        edge.stations.forEach(station -> {
                                            String name = station.station != null ? station.station.name : "Unknown";
                                            context.getSource().sendSuccess(() -> Component.literal("[" + graph.id.toString().substring(0, 8) + "] Station: " + name), true);
                                        });
                                    });
                                }
                            });
                            return 1;
                        })
                        .then(Commands.argument("graphId", StringArgumentType.string())
                                .executes(context -> {
                                    try {
                                        UUID graphId = UUID.fromString(StringArgumentType.getString(context, "graphId"));
                                        BlockGraph graph = RealismMod.GRAPH_MANAGER.getGraph(graphId);
                                        if (graph == null) {
                                            context.getSource().sendFailure(Component.literal("Graph not found."));
                                            return 0;
                                        }
                                        graph.edges.forEach(edge -> {
                                            edge.stations.forEach(station -> {
                                                String name = station.station != null ? station.station.name : "Unknown";
                                                context.getSource().sendSuccess(() -> Component.literal("Station: " + name + " ID: " + station.id), true);
                                            });
                                        });
                                    } catch (IllegalArgumentException e) {
                                        context.getSource().sendFailure(Component.literal("Invalid UUID format."));
                                    }
                                    return 1;
                                })))
        );

        dispatcher.register(realism);
    }

    private static int translateGraphs(CommandSourceStack source, int minNodes) {
        GraphTranslator translator = new GraphTranslator();

        source.sendSuccess(() -> Component.literal("Translating TrackGraphs to BlockGraphs (min nodes: " + minNodes + ")..."), true);

        Create.RAILWAYS.trackNetworks.values().forEach(graph -> {
            if (graph.getNodes().size() >= minNodes) {
                BlockGraph blockGraph = translator.translate(graph);
                RealismMod.GRAPH_MANAGER.UpdateGraph(graph.id, blockGraph);
                source.sendSuccess(() -> Component.literal("Translated graph: " + graph.id + " (Nodes: " + blockGraph.nodes.size() + ")"), true);
            }
        });

        return 1;
    }
}
