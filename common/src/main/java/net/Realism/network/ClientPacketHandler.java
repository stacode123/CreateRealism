package net.Realism.network;

import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Train;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RealismMod;
import net.Realism.trains.etcs.ETCS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

/**
 * Handles processing of packets on the client side
 */
public class ClientPacketHandler {
    
    /**
     * Process an ETCS data sync packet on the client
     */
    public static void handleETCSSync(UUID trainId, double distanceToSignal, double speedLimit, float needleRotation,
                                      boolean backward, double emergencyBrakingDist, double serviceBrakingDist,
                                      double warningBrakingDist, boolean cachedCurveIsDropping, List<ETCS.SpeedLimit> speedLimits) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        
        if (level == null) return;
        MinecraftServer server = level.getServer();
        
        // Schedule task to run on the main client thread
        minecraft.execute(() -> {
            try {
                // Get the train from the client-side train registry
                RailwaySavedData savedData = RailwaySavedData.load(server);
                Train train = savedData.getTrains().get(trainId);
                // Update the train's ETCS data
                if (train != null && train instanceof ITrainInterface) {
                    ITrainInterface trainInterface = (ITrainInterface) train;
                    if (trainInterface.realism$getETCS() == null) {
                        trainInterface.realism$setETCS(new ETCS(train));
                    }
                    
                    trainInterface.realism$getETCS().updateFromNetwork(
                        distanceToSignal, speedLimit, needleRotation, backward,
                        emergencyBrakingDist, serviceBrakingDist, warningBrakingDist, cachedCurveIsDropping,speedLimits
                    );
                }
            } catch (Exception e) {
                RealismMod.LOGGER.error("Error handling ETCS sync packet", e);
            }
        });
    }
}
