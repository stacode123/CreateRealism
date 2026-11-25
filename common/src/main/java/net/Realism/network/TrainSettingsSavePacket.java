package net.Realism.network;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.trains.RouteGuidanceManager;
import net.Realism.trains.TrainSettings;
import net.Realism.trains.TrainSettingsLimits;
import net.Realism.util.C2SPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class TrainSettingsSavePacket implements C2SPacket {
    private static final double STATION_EDIT_RANGE = 16;

    public final TrainSettings trains;
    public final UUID uuid;
    @Nullable
    public final BlockPos stationPos;

    public TrainSettingsSavePacket(TrainSettings trains, UUID uuid) {
        this(trains, uuid, null);
    }

    public TrainSettingsSavePacket(TrainSettings trains, UUID uuid, @Nullable BlockPos stationPos) {
        this.trains = trains;
        this.uuid = uuid;
        this.stationPos = stationPos;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(this.trains.savetoNBT());
        buf.writeUUID(this.uuid);
        buf.writeBoolean(this.stationPos != null);
        if (this.stationPos != null) buf.writeBlockPos(this.stationPos);
    }

    public static TrainSettingsSavePacket read(FriendlyByteBuf buf) {
        TrainSettings settings = TrainSettings.fromNBT(buf.readNbt());
        UUID uuid = buf.readUUID();
        BlockPos stationPos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new TrainSettingsSavePacket(settings, uuid, stationPos);
    }

    @Override
    public void handle(ServerPlayer player) {
        player.getServer().execute(() -> {
            if (!isAuthorized(player)) return;

            TrainSettings sanitized = TrainSettingsLimits.sanitize(this.trains);
            player.getServer().levelKeys().iterator().forEachRemaining(levelResourceKey -> {
                if (player.getServer().getLevel(levelResourceKey) == null) return;
                Train train = Create.RAILWAYS.sided(player.getServer().getLevel(levelResourceKey)).trains.get(this.uuid);
                if (train != null) {
                    ITrainInterface trainInterface = (ITrainInterface) train;
                    trainInterface.realism$setSettings(TrainSettings.fromNBT(sanitized.savetoNBT()));
                    RNetworking.sendToAll(new TrainSettingsUpdatePacket(trainInterface.realism$getSettings(), train.id));

                }
            });
        });
    }

    private boolean isAuthorized(ServerPlayer player) {
        if (stationPos == null) {
            Train controlledTrain = RouteGuidanceManager.controllingTrain(player);
            return controlledTrain != null && controlledTrain.id.equals(this.uuid);
        }

        if (player.distanceToSqr(Vec3.atCenterOf(stationPos)) > STATION_EDIT_RANGE * STATION_EDIT_RANGE)
            return false;
        if (!(player.level().getBlockEntity(stationPos) instanceof StationBlockEntity stationBlockEntity))
            return false;
        GlobalStation station = stationBlockEntity.getStation();
        if (station == null || station.getImminentTrain() == null) return false;
        return station.getImminentTrain().id.equals(this.uuid);
    }
}
