package net.Realism.content.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlockNode {
    public enum NodeType {
        JUNCTION, SIGNAL
    }

    public UUID id;
    public List<BlockEdge> connections;
    public Vec3 location;
    public NodeType type;

    public BlockNode(UUID id, Vec3 location, NodeType type){
        this.id = id;
        this.location = location;
        this.type = type;
        this.connections = new ArrayList<>();
    }

    public CompoundTag write() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("Id", id);
        nbt.putString("Type", type.name());
        if (location != null) {
            nbt.putDouble("X", location.x);
            nbt.putDouble("Y", location.y);
            nbt.putDouble("Z", location.z);
        }
        return nbt;
    }

    public static BlockNode read(CompoundTag nbt) {
        Vec3 location = null;
        if (nbt.contains("X")) {
            location = new Vec3(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z"));
        }
        NodeType type = NodeType.JUNCTION;
        if (nbt.contains("Type")) {
            type = NodeType.valueOf(nbt.getString("Type"));
        }
        return new BlockNode(nbt.getUUID("Id"), location, type);
    }
}
