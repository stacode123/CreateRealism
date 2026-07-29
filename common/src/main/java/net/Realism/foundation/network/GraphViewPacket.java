package net.Realism.foundation.network;

import net.Realism.content.graph.v2.RailGraph;
import net.Realism.content.gui.GraphMapOpener;
import net.Realism.foundation.util.S2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Delivers a translated rail graph and opens the map focused on a position.
 * {@code clickedAxis} is the clicked track block's horizontal direction (or
 * null) so the map highlights the line that was clicked, not one crossing it.
 * {@code context} holds nearby separate networks, rendered dimmed.
 */
public record GraphViewPacket(RailGraph graph, BlockPos focus, String dimension,
                              @Nullable Vec3 clickedAxis, List<RailGraph> context) implements S2CPacket {

    public static GraphViewPacket read(FriendlyByteBuf buf) {
        RailGraph graph = RailGraph.read(buf);
        BlockPos focus = buf.readBlockPos();
        String dimension = buf.readUtf();
        Vec3 clickedAxis = null;
        if (buf.readBoolean())
            clickedAxis = new Vec3(buf.readFloat(), 0, buf.readFloat());
        int contextCount = buf.readVarInt();
        List<RailGraph> context = new ArrayList<>(contextCount);
        for (int i = 0; i < contextCount; i++)
            context.add(RailGraph.read(buf));
        return new GraphViewPacket(graph, focus, dimension, clickedAxis, context);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        graph.write(buf);
        buf.writeBlockPos(focus);
        buf.writeUtf(dimension);
        buf.writeBoolean(clickedAxis != null);
        if (clickedAxis != null) {
            buf.writeFloat((float) clickedAxis.x);
            buf.writeFloat((float) clickedAxis.z);
        }
        buf.writeVarInt(context.size());
        for (RailGraph contextGraph : context)
            contextGraph.write(buf);
    }

    @Override
    public void handle(Minecraft mc) {
        // Indirection keeps client-only Screen classes out of this class's
        // verification; constructing the screen here crashes dedicated servers.
        GraphMapOpener.open(graph, focus, dimension, clickedAxis, context);
    }
}
