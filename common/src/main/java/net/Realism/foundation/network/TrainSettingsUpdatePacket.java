package net.Realism.foundation.network;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.content.trains.TrainSettings;
import net.Realism.foundation.util.S2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class TrainSettingsUpdatePacket implements S2CPacket {

    public final TrainSettings settings;
    public final UUID uuid;

    public TrainSettingsUpdatePacket(TrainSettings settings, UUID UUID) {
        this.settings = settings;
        this.uuid = UUID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(settings.savetoNBT());
        buf.writeUUID(uuid);
    }

    public static TrainSettingsUpdatePacket read(FriendlyByteBuf buf) {
        return new TrainSettingsUpdatePacket(TrainSettings.fromNBT(buf.readNbt()), buf.readUUID());
    }


    @Override
    public void handle(Minecraft mc) {
        mc.execute(() -> {
            if (mc.level == null) return;
            Train tr = Create.RAILWAYS.sided(Minecraft.getInstance().level).trains.get(uuid);
            if(tr instanceof ITrainInterface trainInterface){
                trainInterface.realism$setSettings(settings);
            }
        });
    }
}
