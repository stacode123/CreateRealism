package net.Realism.network;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.trains.TrainSettings;
import net.Realism.util.C2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class TrainSettingsSavePacket implements C2SPacket {
    public final TrainSettings trains;
    public final UUID uuid;

    public TrainSettingsSavePacket(TrainSettings trains, UUID uuid) {
        this.trains = trains;
        this.uuid = uuid;
    }


    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(this.trains.savetoNBT());
        buf.writeUUID(this.uuid);

    }

    public static TrainSettingsSavePacket read(FriendlyByteBuf buf) {
        return new TrainSettingsSavePacket(TrainSettings.fromNBT(buf.readNbt()), buf.readUUID());
    }

    @Override
    public void handle(ServerPlayer player) {
        player.getServer().execute(() -> {
            player.getServer().levelKeys().iterator().forEachRemaining(levelResourceKey -> {
                if (player.getServer().getLevel(levelResourceKey) == null) return;
                Train train = Create.RAILWAYS.sided(player.getServer().getLevel(levelResourceKey)).trains.get(this.uuid);
                if (train != null) {
                    ITrainInterface trainInterface = (ITrainInterface) train;
                    trainInterface.realism$setSettings(TrainSettings.fromNBT(this.trains.savetoNBT()));
                    RNetworking.sendToAll(new TrainSettingsUpdatePacket(trainInterface.realism$getSettings(), train.id));

                }
            });
        });
    }
}
