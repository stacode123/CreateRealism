package net.Realism.content.graph;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.Realism.content.simulator.SimulatedTrain;
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

    public double getDistanceToStation(SimulatedTrain train) {
        if (train.edge == edge) {
            return location - train.edgeLocation;
        }

        if (train.path.isEmpty()) {
            train.path = train.findPath(train.edge, edge);
        }

        if (train.path.isEmpty()) return Double.MAX_VALUE;

        int currentIndex = train.path.indexOf(train.edge);
        if (currentIndex == -1) {
            // Re-calculate if we somehow got off path
            train.path = train.findPath(train.edge, edge);
            currentIndex = train.path.indexOf(train.edge);
            if (currentIndex == -1) return Double.MAX_VALUE;
        }

        double distance = (train.edge.length - train.edgeLocation);
        for (int i = currentIndex + 1; i < train.path.size(); i++) {
            BlockEdge e = train.path.get(i);
            if (e == edge) {
                distance += location;
                return distance;
            }
            distance += e.length;
        }

        return distance;
    }
}

