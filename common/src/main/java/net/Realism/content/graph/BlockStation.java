package net.Realism.content.graph;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class BlockStation {
    public BlockEdge edge;
    public UUID id;
    public GlobalStation station;
    public BlockGraph graph;
    public double location;

    public BlockStation(BlockEdge edge,GlobalStation station,UUID id, BlockGraph g, double location){
        this.edge = edge;
        this.station = station;
        this.graph = g;
        this.id = id;
        this.location = location;
    }

    public CompoundTag write() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("Id", id);
        nbt.putDouble("Location", location);
        if (station != null)
            nbt.putUUID("StationId", station.id);
        return nbt;
    }

    public void read(CompoundTag nbt, BlockGraph graph) {
        id = nbt.getUUID("Id");
        this.graph = graph;
        this.location = nbt.getDouble("Location");
        if (nbt.contains("StationId")) {
            this.station = graph.graph.getPoint(EdgePointType.STATION,id);
        }
    }

    public void setEdge(BlockEdge edge){
        this.edge = edge;
    }
    public void setLocation(Double location){
        this.location = location;
    }
}

