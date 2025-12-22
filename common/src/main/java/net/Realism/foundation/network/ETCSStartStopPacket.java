package net.Realism.foundation.network;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.foundation.util.C2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public class ETCSStartStopPacket implements C2SPacket{
    private final boolean toUpdate;
    private final UUID trainId;
    public ETCSStartStopPacket(boolean toUpdate, UUID trainId) {
        this.toUpdate = toUpdate;
        this.trainId = trainId;
    }
    public static ETCSStartStopPacket read(FriendlyByteBuf buf) {
        return new ETCSStartStopPacket(buf.readBoolean(),buf.readUUID());
    }
    

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(toUpdate);
        buf.writeUUID(trainId);

    }

    @Override
    public void handle(ServerPlayer player) {
        Objects.requireNonNull(player.getServer()).execute(() -> {
            Train train = Create.RAILWAYS.sided(player.level()).trains.get(trainId);
            if(train instanceof ITrainInterface RTrain){
                RTrain.realism$getETCS().toUpdate = toUpdate;
            }
        });
    }
}
