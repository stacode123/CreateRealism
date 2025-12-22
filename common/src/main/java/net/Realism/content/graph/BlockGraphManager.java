package net.Realism.content.graph;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockGraphManager extends SavedData {
    private Map<UUID,BlockGraph> Graphs;

    BlockGraph getGraph(UUID id){
        return Graphs.get(id);
    }
    void UpdateGraph(UUID id, BlockGraph graph){
        Graphs.put(id, graph);
    }

    List<UUID> getGraphIds(){
        return List.copyOf(Graphs.keySet());
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.put("Graphs", NBTHelper.writeCompoundList(Graphs.values(), BlockGraph::write));
        return nbt;
    }

    public static BlockGraphManager load(CompoundTag nbt) {
        BlockGraphManager manager = new BlockGraphManager();
        manager.Graphs = new java.util.HashMap<>();
        NBTHelper.iterateCompoundList(nbt.getList("Graphs", 10), tag -> {
            BlockGraph graph = new BlockGraph(null);
            graph.read(tag);
            manager.Graphs.put(graph.id, graph);
        });
        return manager;
    }
}
