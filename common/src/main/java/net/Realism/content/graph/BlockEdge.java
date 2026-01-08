package net.Realism.content.graph;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class BlockEdge {
    public BlockNode start;
    public BlockNode end;
    public List<BlockStation> stations;
    public int length;

    public enum EdgeType {
        NORMAL, CHAIN
    }

    public EdgeType type;
    public java.util.UUID reservedBy = null;
    public BlockEdge opposite = null;

    public BlockEdge(BlockNode start, BlockNode end, List<BlockStation> stations, int length, EdgeType type){
        this.start = start;
        this.end = end;
        this.length = length;
        this.stations = stations;
        this.type = type;
    }

    public CompoundTag write() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("Start", start.id);
        nbt.putUUID("End", end.id);
        nbt.put("Stations", net.createmod.catnip.nbt.NBTHelper.writeCompoundList(stations, BlockStation::write));
        nbt.putInt("Length", length);
        nbt.putString("Type", type.name());

        return nbt;
    }

    public void read(CompoundTag nbt, java.util.Map<java.util.UUID, BlockNode> nodes, BlockGraph graph) {
        start = nodes.get(nbt.getUUID("Start"));
        end = nodes.get(nbt.getUUID("End"));
        stations = new java.util.ArrayList<>();
        length = nbt.getInt("Length");
        type = EdgeType.valueOf(nbt.getString("Type"));
        net.createmod.catnip.nbt.NBTHelper.iterateCompoundList(nbt.getList("Stations", 10), tag -> {
            BlockStation station = new BlockStation(this, null, null, graph, 0);
            station.read(tag, graph);
            stations.add(station);
        });
    }


}
