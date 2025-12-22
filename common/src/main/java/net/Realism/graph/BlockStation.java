package net.Realism.graph;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class BlockStation {
    public BlockEdge edge;
    public UUID id;
    public GlobalStation station;
    public BlockGraph graph;

    public BlockStation(BlockEdge edge,GlobalStation station,UUID id, BlockGraph g){
        this.edge = edge;
        this.station = station;
        this.graph = g;
        this.id = id;
    }

    public CompoundTag write() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("Id", id);
        if (station != null)
            nbt.putUUID("StationId", station.id);
        return nbt;
    }

    public void read(CompoundTag nbt, BlockGraph graph) {
        id = nbt.getUUID("Id");
        this.graph = graph;
        if (nbt.contains("StationId")) {
            this.station = graph.graph.getPoint(EdgePointType.STATION,id);
        }
    }

    public void setEdge(BlockEdge edge){
        this.edge = edge;
    }
}

