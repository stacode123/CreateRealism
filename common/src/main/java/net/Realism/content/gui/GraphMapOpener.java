package net.Realism.content.gui;

import net.Realism.content.graph.v2.RailGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client-only holder for opening the map. Packet classes must not construct
 * the screen themselves: verifying such a method loads the client-only
 * {@code Screen} hierarchy, which crashes dedicated servers the moment the
 * packet class is registered. An invokestatic into this class resolves only
 * when actually executed, which never happens server-side.
 */
public class GraphMapOpener {

    public static void open(RailGraph graph, BlockPos focus, String dimension,
                            @Nullable Vec3 clickedAxis, List<RailGraph> context) {
        Minecraft.getInstance().setScreen(new GraphMapScreen(graph, focus, dimension, clickedAxis, context));
    }
}
