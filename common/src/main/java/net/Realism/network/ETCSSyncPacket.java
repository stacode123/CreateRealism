package net.Realism.network;

import com.simibubi.create.CreateClient;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RealismMod;
import net.Realism.trains.etcs.ETCS;
import net.Realism.trains.etcs.ETCS.SpeedLimit;
import net.Realism.util.S2CPacket;
import net.minecraft.client.Minecraft;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ETCSSyncPacket implements S2CPacket {
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
    private final int zoom;
    private final boolean newRoute;
    private final double distanceToBrakingPoint;

    public ETCSSyncPacket(UUID trainId, double distanceToSignal, double speedLimit, float needleRotation,
                         boolean backward, double emergencyBrakingDist, double serviceBrakingDist,
                         double warningBrakingDist, boolean cachedCurveIsDropping, List<SpeedLimit> speedLimits,
                         int zoom, boolean newRoute, double distanceToBrakingPoint) {
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
        this.zoom = zoom;
        this.newRoute = newRoute;
        this.distanceToBrakingPoint = distanceToBrakingPoint;
    }

    public static ETCSSyncPacket read(FriendlyByteBuf buffer) {
        UUID trainId = buffer.readUUID();
        double distanceToSignal = buffer.readDouble();
        double speedLimit = buffer.readDouble();
        float needleRotation = buffer.readFloat();
        boolean backward = buffer.readBoolean();
        double emergencyBrakingDist = buffer.readDouble();
        double serviceBrakingDist = buffer.readDouble();
        double warningBrakingDist = buffer.readDouble();
        boolean cachedCurveIsDropping = buffer.readBoolean();
        int zoom = buffer.readInt();
        // Read new route flag
        boolean newRoute = buffer.readBoolean();
        double distanceToBrakingPoint = buffer.readDouble();
        int speedLimitCount = buffer.readInt();
        List<SpeedLimit> speedLimits = new ArrayList<>();
        for (int i = 0; i < speedLimitCount; i++) {
            double distance = buffer.readDouble();
            double limit = buffer.readDouble();
            speedLimits.add(new SpeedLimit(distance, limit));
        }

        return new ETCSSyncPacket(trainId, distanceToSignal, speedLimit, needleRotation, backward,
                emergencyBrakingDist, serviceBrakingDist, warningBrakingDist, cachedCurveIsDropping,
                speedLimits, zoom, newRoute, distanceToBrakingPoint);
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
        buffer.writeInt(zoom);
        // Write new route flag
        buffer.writeBoolean(newRoute);
        // Write distance to braking point
        buffer.writeDouble(distanceToBrakingPoint);
        // Write speed limits
        buffer.writeInt(speedLimits.size());
        for (SpeedLimit limit : speedLimits) {
            buffer.writeDouble(limit.getDistance());
            buffer.writeDouble(limit.getSpeedLimit());
        }
    }

    @Override
    public void handle(Minecraft mc) {
        // Schedule task to run on the main client thread
        mc.execute(() -> {
            try {
                // Get the train from the client-side train registry
                if (CreateClient.RAILWAYS.trains.get(trainId) != null && CreateClient.RAILWAYS.trains.get(trainId) instanceof ITrainInterface) {
                    ITrainInterface trainInterface = (ITrainInterface) CreateClient.RAILWAYS.trains.get(trainId);
                    if (trainInterface.realism$getETCS() == null) {
                        trainInterface.realism$setETCS(new ETCS(CreateClient.RAILWAYS.trains.get(trainId)));
                    }

                    trainInterface.realism$getETCS().updateFromNetwork(
                            distanceToSignal, speedLimit, needleRotation, backward,
                            emergencyBrakingDist, serviceBrakingDist, warningBrakingDist,
                            cachedCurveIsDropping, speedLimits, zoom, newRoute, distanceToBrakingPoint
                    );
                }
            } catch (Exception e) {
                RealismMod.LOGGER.error("Error handling ETCS sync packet", e);
            }
        });
    }
}
