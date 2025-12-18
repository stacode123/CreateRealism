package net.Realism.NBT;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

public class RNBTHelper {
    public static CompoundTag writeUUIDList(List<UUID> list, String key) {
        int iter = 0;
        CompoundTag tag = new CompoundTag();
        for (UUID id : list) {
            tag.putUUID(String.join("_", key, String.valueOf(iter++)), id);
        }
        return tag;
    }

    public static List<UUID> readUUIDList(CompoundTag tag, String key) {
        List<UUID> list = new java.util.ArrayList<>(List.of());
        for (int i = 0; i < tag.size(); i++) {
            list.add(tag.getUUID(String.join("_", key, String.valueOf(i))));
        }
        return list;
    }




}
