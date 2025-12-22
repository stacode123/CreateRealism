package net.Realism.foundation.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;
import net.Realism.content.graph.BlockGraph;
import net.Realism.content.graph.GraphTranslator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class RealismCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> realism = Commands.literal("realism")
                .requires(source -> source.hasPermission(2));

        realism.then(Commands.literal("translate")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    GraphTranslator translator = new GraphTranslator();
                    
                    source.sendSuccess(() -> Component.literal("Translating TrackGraphs to BlockGraphs..."), true);
                    
                    Create.RAILWAYS.trackNetworks.values().forEach(graph -> {
                        BlockGraph blockGraph = translator.translate(graph);
                        source.sendSuccess(() -> Component.literal("Translated graph: " + graph.id + " (Nodes: " + blockGraph.nodes.size() + ")"), true);
                    });
                    
                    return 1;
                })
        );

        dispatcher.register(realism);
    }
}
