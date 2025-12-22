package net.Realism.content.graph;

import com.simibubi.create.content.trains.graph.TrackGraph;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.nbt.CompoundTag;

import java.util.*;


public final class BlockGraph {
    public TrackGraph graph;
    public  UUID id;
    public List<BlockNode> nodes;
    public List<BlockEdge> edges;

    public BlockGraph(TrackGraph graph){
        this.id = graph == null ? new java.util.UUID(0L, 0L) : graph.id;
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }


    public CompoundTag write() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("Id", id);
        nbt.put("Nodes", NBTHelper.writeCompoundList(nodes, BlockNode::write));
        nbt.put("Edges", NBTHelper.writeCompoundList(edges, BlockEdge::write));
        return nbt;
    }

    public void read(CompoundTag nbt) {
        id = nbt.getUUID("Id");
        nodes = new ArrayList<>();
        Map<UUID, BlockNode> nodeMap = new HashMap<>();
        NBTHelper.iterateCompoundList(nbt.getList("Nodes", 10), tag -> {
            BlockNode node = BlockNode.read(tag);
            nodes.add(node);
            nodeMap.put(node.id, node);
        });

        edges = new ArrayList<>();
        NBTHelper.iterateCompoundList(nbt.getList("Edges", 10), tag -> {
            BlockEdge edge = new BlockEdge(null, null, new ArrayList<>(), 0);
            edge.read(tag, nodeMap, this);
            edges.add(edge);
        });
    }
}
