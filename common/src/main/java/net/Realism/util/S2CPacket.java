package net.Realism.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public interface S2CPacket {
  void write(FriendlyByteBuf buf);
  void handle(Minecraft mc);
}
