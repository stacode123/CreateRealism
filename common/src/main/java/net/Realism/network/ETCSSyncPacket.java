package net.Realism.network;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RealismMod;
import net.Realism.trains.etcs.ETCS;
import net.Realism.trains.etcs.ETCS.SpeedLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ETCSSyncPacket extends SimplePacketBase {
    private final UUID trainId;
    private final double distanceToSignal;
    private final double speedLimit;
    private final float needleRotation;
    private final boolean backward;
    private final double emergencyBrakingDist;
    private final double serviceBrakingDist;
    private final double warningBrakingDist;
    private final boolean cachedCurveIsDropping;
    private final List<SpeedLimit> speedLimits;

    public ETCSSyncPacket(UUID trainId, double distanceToSignal, double speedLimit, float needleRotation, 
                         boolean backward, double emergencyBrakingDist, double serviceBrakingDist, 
                         double warningBrakingDist, boolean cachedCurveIsDropping, List<SpeedLimit> speedLimits) {
        this.trainId = trainId;
        this.distanceToSignal = distanceToSignal;
        this.speedLimit = speedLimit;
        this.needleRotation = needleRotation;
        this.backward = backward;
        this.emergencyBrakingDist = emergencyBrakingDist;
        this.serviceBrakingDist = serviceBrakingDist;
        this.warningBrakingDist = warningBrakingDist;
        this.cachedCurveIsDropping = cachedCurveIsDropping;
        this.speedLimits = speedLimits;
    }

    public ETCSSyncPacket(FriendlyByteBuf buffer) {
        trainId = buffer.readUUID();
        distanceToSignal = buffer.readDouble();
        speedLimit = buffer.readDouble();
        needleRotation = buffer.readFloat();
        backward = buffer.readBoolean();
        emergencyBrakingDist = buffer.readDouble();
        serviceBrakingDist = buffer.readDouble();
        warningBrakingDist = buffer.readDouble();
        cachedCurveIsDropping = buffer.readBoolean();
        
        // Read speed limits
        int size = buffer.readInt();
        List<SpeedLimit> limits = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double distance = buffer.readDouble();
            double limitSpeed = buffer.readDouble();
            limits.add(new SpeedLimit(distance, limitSpeed));
        }
        this.speedLimits = limits;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(trainId);
        buffer.writeDouble(distanceToSignal);
        buffer.writeDouble(speedLimit);
        buffer.writeFloat(needleRotation);
        buffer.writeBoolean(backward);
        buffer.writeDouble(emergencyBrakingDist);
        buffer.writeDouble(serviceBrakingDist);
        buffer.writeDouble(warningBrakingDist);
        buffer.writeBoolean(cachedCurveIsDropping);
        
        // Write speed limits
        buffer.writeInt(speedLimits.size());
        for (SpeedLimit limit : speedLimits) {
            buffer.writeDouble(limit.getDistance());
            buffer.writeDouble(limit.getSpeedLimit());
        }
    }

    @Override
    public boolean handle(Context context) {
        // This method must be implemented, but we'll use handleClient instead
        // to avoid issues with the final Context class
        return true;
    }

    // New method to handle client-side packet processing without using Context
    public void handleClient(Minecraft minecraft) {
        Level level = minecraft.getInstance().level;
        
        if (level != null) {
            try {
                // On client side, we need to find the train in the client-side railway data
                GlobalRailwayManager m =  CreateClient.RAILWAYS;
                if (m != null) {
                    Train train = m.trains.get(trainId);
                    if (train != null && train instanceof ITrainInterface) {
                        ITrainInterface trainInterface = (ITrainInterface) train;
                        if (trainInterface.realism$getETCS() == null) {
                            trainInterface.realism$setETCS(new ETCS(train));
                        }
                        trainInterface.realism$getETCS().updateFromNetwork(
                            distanceToSignal, speedLimit, needleRotation, backward,
                            emergencyBrakingDist, serviceBrakingDist, warningBrakingDist, 
                            cachedCurveIsDropping, speedLimits
                        );
                    }
                }
            } catch (Exception e) {
                RealismMod.LOGGER.error("Error processing ETCS sync packet", e);
            }
        }
    }
}
