package net.Realism.content.graph.v2;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The collapsed, self-contained snapshot of one Create {@code TrackGraph}:
 * junction/signal/portal nodes joined by directed edges carrying lengths,
 * speed caps, stations, crossings and render shapes. Fully serializable, no
 * references back into Create's live graph.
 */
public class RailGraph {
    public final UUID trackGraphId;
    /** Create's {@code TrackGraph.getChecksum()} at translation time. */
    public final int sourceChecksum;
    /** Dimension ids (e.g. "minecraft:overworld") indexed by nodes/edges. */
    public final List<String> dimensions = new ArrayList<>();
    public final List<RailNode> nodes = new ArrayList<>();
    public final List<RailEdge> edges = new ArrayList<>();

    public RailGraph(UUID trackGraphId, int sourceChecksum) {
        this.trackGraphId = trackGraphId;
        this.sourceChecksum = sourceChecksum;
    }

    public RailNode node(int id) {
        return nodes.get(id);
    }

    /** Rebuilds per-node outgoing edge lists; call after populating edges. */
    public void linkNodes() {
        for (RailNode node : nodes)
            node.outgoing.clear();
        for (RailEdge edge : edges)
            nodes.get(edge.from).outgoing.add(edge.id);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(trackGraphId);
        buf.writeInt(sourceChecksum);
        buf.writeVarInt(dimensions.size());
        for (String dimension : dimensions)
            buf.writeUtf(dimension);
        buf.writeVarInt(nodes.size());
        for (RailNode node : nodes)
            node.write(buf);
        buf.writeVarInt(edges.size());
        for (RailEdge edge : edges)
            edge.write(buf);
    }

    public static RailGraph read(FriendlyByteBuf buf) {
        RailGraph graph = new RailGraph(buf.readUUID(), buf.readInt());
        int dimensionCount = buf.readVarInt();
        for (int i = 0; i < dimensionCount; i++)
            graph.dimensions.add(buf.readUtf());
        int nodeCount = buf.readVarInt();
        for (int i = 0; i < nodeCount; i++)
            graph.nodes.add(RailNode.read(buf, i));
        int edgeCount = buf.readVarInt();
        for (int i = 0; i < edgeCount; i++)
            graph.edges.add(RailEdge.read(buf, i));
        graph.linkNodes();
        return graph;
    }
}
