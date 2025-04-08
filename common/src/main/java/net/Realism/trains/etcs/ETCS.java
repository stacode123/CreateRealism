package net.Realism.trains.etcs;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.foundation.utility.Couple;
import net.Realism.Interfaces.ITramSignPoint;
import net.Realism.RealismMod;
import net.Realism.mixinaccesors.TramSignDataAccessor;
import net.Realism.network.ETCSSyncPacket;
import net.Realism.network.RealismPackets;
import net.Realism.trains.SignalFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.Realism.trains.SignalFinder.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import purplecreate.tramways.content.signs.TramSignPoint;
import purplecreate.tramways.content.signs.demands.SignDemand;
import purplecreate.tramways.content.signs.demands.TemporaryEndSignDemand;
import purplecreate.tramways.content.signs.demands.TemporarySpeedSignDemand;

import java.util.*;

import static net.Realism.trains.etcs.ETCStools.optimizedRenderSpeedCurve;
import static net.Realism.trains.etcs.ETCStools.renderElement;


public class ETCS {
    boolean backward = false;
    public Train train;
    public SignalScanResult previousSignalScanResult;
    boolean previousBackward;
    
    // New variables to store calculation results
    private SignalScanResult currentSignalScanResult;
    private double speedLimit = -1; // -1 means no limit
    private double distanceToSignal = 0;
    private float needleRotationDegrees = 0;
    
    // Last update timestamp to handle client/server synchronization
    private long lastUpdateTime = 0;

    private boolean needsSync = false;
    private long lastSyncTime = 0;
    private static final int SYNC_INTERVAL_MS = 200; // Sync every 200ms

    private int curvedropping;

    // Braking distances
    private double cachedEmergencyBrakingDist;
    private double cachedServiceBrakingDist;
    private double cachedWarningBrakingDist;
    private boolean cachedCurveIsDropping = false;
    private double cachedAllowedSpeed = -1;
    private List<SpeedLimit> cachedSpeedLimits = new ArrayList<>();
    private Font font = Minecraft.getInstance().font;

    
    private int trackspeedlimit = 300;
    public ETCS(Train train) {
        this.train = train;
    }
    
    /**
     * Update method to be called during game logic/tick updates.
     * This handles all calculations independent of rendering.
     */
    public void update() {
        // Record update time
        this.lastUpdateTime = System.currentTimeMillis();
        
        // Determine train direction
        if (train.speed < 0) {
            previousBackward = backward;
            backward = true;
        } else if (train.speed > 0) {
            previousBackward = backward;
            backward = false;
        }
        
        // Scan for signals
        SignalScanResult s = SignalFinder.scanAheadForSignals(train, 4000, backward);
        
        if (s != null) {
            // Logic to decide whether to use previous scan result
            if (previousSignalScanResult != null) {
                if ((Math.abs(s.getDistanceToClosestOccupiedSignal() - previousSignalScanResult.getDistanceToClosestOccupiedSignal()) > 100)
                        && backward == previousBackward) {
                    s = previousSignalScanResult;
                }
            }
            this.previousSignalScanResult = s;
            this.currentSignalScanResult = s;
            this.distanceToSignal = s.getDistanceToClosestOccupiedSignal();

            // Process tram signs for speed limits
            processTramSigns(s);

            float distance = (float) s.getDistanceToClosestOccupiedSignal();
            float maxDeceleration = train.acceleration() * 2f * 400;
            speedLimit = Math.min(calculateAllowedSpeed(distance, maxDeceleration),trackspeedlimit);
            if (cachedAllowedSpeed > speedLimit){
                cachedCurveIsDropping = true;
                curvedropping =0;
            }
            else {
                curvedropping+=1;
                if (curvedropping >10){
                cachedCurveIsDropping = false;}
            }
            cachedAllowedSpeed = speedLimit;

        }
        // Calculate needle rotation based on train speed
        this.needleRotationDegrees = ETCStools.calculateNeedleRotation(train.speed);
        
        // Update braking distances
        updateBrakingDistances();
        
        // After calculations are done
        markDirty();

        // Call sync at the end of update
        // Save state to train's NBT
        
        // Send data to clients if needed
        syncToClients();
    }
    /**
     * Process tram signs to extract speed limits
     */
    private void processTramSigns(SignalScanResult s) {
        try {
            cachedSpeedLimits = new ArrayList<>();
            for (TramSignInfo sign : s.getTramSigns()) {
                if (sign == null || sign.getSign() == null) continue;
                Couple<Set<TramSignPoint.SignData>> sides = null;
                if (sign.getSign() instanceof ITramSignPoint) {
                    sides = ((ITramSignPoint)sign.getSign()).getSides();
                } else {
                    // Use reflection as a fallback
                    try {
                        java.lang.reflect.Field sidesField = TramSignPoint.class.getDeclaredField("sides");
                        sidesField.setAccessible(true);
                        sides = (Couple<Set<TramSignPoint.SignData>>) sidesField.get(sign.getSign());
                    } catch (Exception e) {
                        RealismMod.LOGGER.error("Error accessing sides field: " + e.getMessage());
                    }
                }

                if (sides == null) continue;
                CompoundTag tag = null;
                SignDemand demand = null;
                int LastLimit = 300;
                if (sides.get(sign.getPrimary()) == null) continue;
                    for (TramSignPoint.SignData signD : new HashSet<>(sides.get(sign.getPrimary()))) {
                        if (signD == null) continue;
                        if (!(signD instanceof TramSignDataAccessor)) {
                            try {
                                java.lang.reflect.Field demandFieldExtra = TramSignPoint.SignData.class.getDeclaredField("demandExtra");
                                java.lang.reflect.Field demandField = TramSignPoint.SignData.class.getDeclaredField("demand");
                                demandFieldExtra.setAccessible(true);
                                demandField.setAccessible(true);
                                tag = (CompoundTag) demandFieldExtra.get(signD);
                                demand = (SignDemand) demandField.get(signD);

                            } catch (Exception e) {
                                RealismMod.LOGGER.error("Error accessing DemandExtra field: " + e.getMessage());
                            }
                        } else {


                            TramSignDataAccessor accessor = (TramSignDataAccessor) signD;

                            demand = accessor.getDemand();
                            if (demand == null) continue;

                            tag = accessor.getDemandExtra();
                        }
                        if (tag != null && tag.contains("Throttle")) {
                            double signSpeedLimit = tag.getInt("Throttle");
                            signSpeedLimit *= train.maxSpeed() / 100 * 20 * 3.6f;
                            cachedSpeedLimits.add(new SpeedLimit(sign.getDistance(), signSpeedLimit));
                            if (demand instanceof TemporarySpeedSignDemand) {
                                LastLimit = (int) cachedSpeedLimits.get(cachedSpeedLimits.size()-1).speedLimit;
                            }}
                        if (demand instanceof TemporaryEndSignDemand) {
                            cachedSpeedLimits.add(new SpeedLimit(sign.getDistance(), LastLimit));}

                    }
            }
        } catch (Exception e) {
            RealismMod.LOGGER.error("Error processing tram signs: " + e.getMessage(), e);
        }
    }


    /**
     * Render method to be called during render cycle.
     * Only contains rendering code, using data calculated in update().
     */
    public void render(GuiGraphics graphics) {
        // Load the most current data before rendering
        Minecraft mc = Minecraft.getInstance();
        PoseStack posestack = graphics.pose();
        posestack.pushPose();
        posestack.scale(0.25f, 0.25f, 0.25f);
        
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getHeight();
        
        int xPos = (screenWidth * 4) - 536;  // 10 scaled pixels from right edge
        int yPos = 0;
        
        // Render the ETCS panel background
        RenderSystem.setShaderTexture(0, new ResourceLocation("realism:textures/etcs.png"));
        graphics.blit(new ResourceLocation("realism:textures/etcs.png"),
                xPos, yPos,   // screen position
                0, 0,         // texture position (top left of texture)
                536, 401,   // width and height to render
                536, 401);  // texture width and height
                
        // Render the speedometer needle
        posestack.pushPose();
        posestack.translate(xPos + 167, yPos + 130, 0);
        
        float rotationRadians = needleRotationDegrees * (float)(Math.PI / 180);
        posestack.mulPose(Axis.ZP.rotation(rotationRadians));
        posestack.translate(-24, -89, 0);

        // Render the image
        RenderSystem.setShaderTexture(0, new ResourceLocation("realism:textures/etcshand.png"));
        graphics.blit(new ResourceLocation("realism:textures/etcshand.png"),
                0,
                0,   // screen position (now relative to transformed coordinates)
                0, 0,   // texture position
                49, 112,   // width and height to render
                49, 112);  // texture width and height
        posestack.popPose();

        //graphics.drawString(mc.font,String.valueOf((int)train.speed*20*3.6),xPos + 156,yPos+124,0x294A6DFF);

        // Render ETCS limits display
        renderETCSlimits(graphics, posestack, xPos + 10, yPos + 10);
        renderOverviewItems(graphics,xPos, yPos,(int)distanceToSignal,1);
        renderBrakingCurve(graphics, posestack, xPos+10, yPos+10);
        
        posestack.popPose();
    }
    
    public void renderETCSlimits(GuiGraphics graphics, PoseStack posestack, int Xpos, int Ypos) {
        
        posestack.pushPose();
        posestack.translate(Xpos + 361, Ypos + 227, 0);
        int CurrentX = 0;
        int CurrentY = 0;
        double px = 0;

        ResourceLocation startTex = new ResourceLocation("realism:textures/etcsplusstart.png");
        ResourceLocation midTex = new ResourceLocation("realism:textures/etcsplusmid.png");
        ResourceLocation endTex = new ResourceLocation("realism:textures/etcsplusend.png");
        ResourceLocation flagTex = new ResourceLocation("realism:textures/flag.png");

        if (distanceToSignal > 4000) {
            CurrentY -= 9;
            renderElement(graphics, startTex, 0, CurrentY, 15, 9);

            for (int i = 0; i < 198; i += 1) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }

            posestack.popPose();
            return;
        }

        if (distanceToSignal < 60) {
            double x = (distanceToSignal * 0.304);
            for (int i = 0; i < (int) x; i += 1) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        CurrentY -= 9;
        renderElement(graphics, startTex, 0, CurrentY, 15, 9);
        if (distanceToSignal <= 500){
            px = (distanceToSignal * 0.21) - 18;}
        else {
            px = (distanceToSignal * 0.21) - 9;
        }
        if (px > 96.0) {
            px = 96.0;
        }
        for (int i = 0; i < (int) px; i += 1) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }
        if (distanceToSignal <= 500) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }
        if (distanceToSignal < 1000){
        px = ((distanceToSignal-500) * 0.068) ;}
        else {px = ((distanceToSignal-500) * 0.068);}
         if (px > 34.0) {
            px = 34.0;
        }
        for (int i = 0; i < (int) px; i += 1) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }
        if (distanceToSignal <= 1000) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }
        if (distanceToSignal <= 2000){
            px = ((distanceToSignal-1000) * 0.034);}
        else {px = ((distanceToSignal-1000) * 0.034);}
        if (px > 34.0) {
            px = 34.0;
        }
        for (int i = 0; i < (int) px; i += 1) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }
        if (distanceToSignal <= 2000) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }
        px = ((distanceToSignal-2000) * 0.017);
        for (int i = 0; i < (int) px; i += 1) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }
        CurrentY -= 9;
        renderElement(graphics, endTex, 0, CurrentY, 15, 9);
        renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
        posestack.popPose();
    }

    public void renderBrakingCurve(GuiGraphics graphics, PoseStack poseStack, int xPos, int yPos) {
        float currentSpeed = (float) Math.abs(train.speed * 20f * 3.6f);
        float distance = (float) distanceToSignal;

        // Recalculate these values client-side
        double allowedSpeed = speedLimit;

        boolean approachingBrakingZone = distance <= (cachedWarningBrakingDist * 1.5) + 100;

        // Determine curve color
        int curveColor;
        if (currentSpeed > speedLimit) {
            curveColor = 0xFFFF0000;  // Red
        } else if (cachedCurveIsDropping || approachingBrakingZone) {
            curveColor = 0xFFFFFF00;  // Yellow

            if (distance <= cachedServiceBrakingDist * 1.3) {
                //braking = true;
            } else if (distance > cachedWarningBrakingDist * 1.5) {
                //braking = false;
            }
        } else {
            curveColor = 0xFF888888;  // Gray
            //braking = false;
        }

        // Optimize render speed curve using calculated values
        optimizedRenderSpeedCurve(graphics, poseStack, xPos + 155, yPos + 119, allowedSpeed, curveColor);
    }

    public void renderOverviewItems(GuiGraphics graphics,int xPos, int yPos,int distance,int zoom) {
        ResourceLocation flag = new ResourceLocation("realism:textures/flag.png");
            for (SpeedLimit s : cachedSpeedLimits){
                if (s.getDistance() > distanceToSignal){continue;}
                int pixelPos = calculateDistancePixelPosition(s.getDistance(),zoom);
                //int pixelPos = 1;
                // Render the item at the calculated position
                renderElement(graphics, flag, xPos+386 , yPos +235 - pixelPos, 19, 11);
                graphics.drawString(font, String.valueOf((int)s.getSpeedLimit()), xPos+406, yPos+235 - pixelPos, 0xFFFFFFFF);

            }






    }


    public int calculateDistancePixelPosition(double distance,int zoom) {
        if (zoom == 1) {
            int pixelPos = 0;

            if (distance > 4000) {
                return 198; // Maximum offset without the additional 9px
            }

            // First range: 0-500 units
            double range1 = Math.min(500, distance);
            if (distance <= 500) {
                pixelPos += (int) ((range1 * 0.21));
                return pixelPos;
            }

            pixelPos += (int) ((range1 * 0.21));

            // Second range: 501-1000 units
            double range2 = Math.min(1000, distance) - 500;
            if (range2 > 0) {
                pixelPos += (int) (range2 * 0.068);
            }

            if (distance <= 1000) {
                return pixelPos;
            }

            // Third range: 1001-2000 units
            double range3 = Math.min(2000, distance) - 1000;
            if (range3 > 0) {
                pixelPos += (int) (range3 * 0.034);
            }

            if (distance <= 2000) {
                return pixelPos;
            }

            // Fourth range: 2001+ units
            double range4 = distance - 2000;
            pixelPos += (int) (range4 * 0.017);

            return pixelPos;
        }
        else{return 0;}
    }

    /**
     * Update data from network packet
     */
     private void markDirty() {
        this.needsSync = true;
     }



    private double calculateStoppingDistance(float speedKmh, float deceleration) {
        // Convert km/h to m/s
        float speedMs = speedKmh / 3.6f;
        // Basic physics: d = v²/(2a)
        return (speedMs * speedMs) / (2 * deceleration);
    }

    private int calculateAllowedSpeed(float distance, float maxDeceleration) {
        float safetyFactor = 1.2f;

        // Calculate safe speed based on distance to signal
        if (distance < 50) {
            return 20;
        }

        float safeSpeed = (float) (Math.sqrt(2 * (maxDeceleration * 0.7) * distance) * 3.6);

        if (distance < 100) {
            safeSpeed *= (distance / 100f) * 0.8f;
        } else if (distance < 300) {
            safeSpeed *= 0.9f;
        } else if (distance < 500) {
            safeSpeed *= 0.95f;
        }

        int signalBasedSpeed = (int)(safeSpeed / safetyFactor);
        
        // Check for speed limits from tram signs
        int limitBasedSpeed = Integer.MAX_VALUE;
        for (SpeedLimit speedLimit : cachedSpeedLimits) {
            // Only consider speed limits that are ahead of us but within our calculation distance
            if (speedLimit.getDistance() > 0 && speedLimit.getDistance() <= distance * 1.5) {
                // Calculate safe speed based on distance to limit using physics formula
                float distToLimit = (float) speedLimit.getDistance();
                float targetSpeed = (float) speedLimit.getSpeedLimit() / 3.6f; // Convert km/h to m/s

// Physics formula: v₁ = √(v₂² + 2ad) where v₂ is target speed, not zero
                float targetSpeedSq = targetSpeed * targetSpeed;
                safeSpeed = (float) (Math.sqrt(targetSpeedSq + 2 * (maxDeceleration * 0.7) * distToLimit) * 3.6);

// Apply safety adjustments based on distance
//                if (distToLimit < 100) {
//                    safeSpeed = (float) Math.min(safeSpeed, speedLimit.getSpeedLimit() + (distToLimit / 100f) * 20f);
//                } else if (distToLimit < 300) {
//                    safeSpeed = (float) safeSpeed * 0.9f;
//                }else if (distToLimit < 400) {
//                      safeSpeed = (float) safeSpeed * 0.925f;
//                }
//                else if (distToLimit < 500) {
//                    safeSpeed = (float) safeSpeed * 0.95f;
//                }

                int adjustedLimit = (int)(safeSpeed);

                if (adjustedLimit < limitBasedSpeed) {
                    limitBasedSpeed = adjustedLimit;
                }
            }
            if(speedLimit.getDistance()<5){
                trackspeedlimit = (int)speedLimit.getSpeedLimit();
            }
        }
        
        // Return the most restrictive of signal-based and limit-based speeds
        return limitBasedSpeed < Integer.MAX_VALUE ? 
               Math.min(signalBasedSpeed, limitBasedSpeed) : 
               signalBasedSpeed;
    }

    private void updateBrakingDistances() {

        float maxDeceleration = train.acceleration() * 2f * 400;
        float currentSpeed = (float) Math.abs(train.speed * 20f * 3.6f);

        cachedEmergencyBrakingDist = calculateStoppingDistance(currentSpeed, (float) (maxDeceleration * 1.0));
        cachedServiceBrakingDist = calculateStoppingDistance(currentSpeed, (float) (maxDeceleration * 0.7));
        cachedWarningBrakingDist = calculateStoppingDistance(currentSpeed, (float) (maxDeceleration * 0.5));
    }
    public static class SpeedLimit {
        private final double distance;
        private final double speedLimit;

        public SpeedLimit(double distance, double speedLimit) {
            this.distance = distance;
            this.speedLimit = speedLimit;
        }

        public double getDistance() {
            return distance;
        }

        public double getSpeedLimit() {
            return speedLimit;
        }
    }


    /**
     * Synchronize ETCS data to clients
     */
    private void syncToClients() {
        // Only run on server side and when sync is needed
        if (train == null || !needsSync) return;

        // Check if it's time to sync
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSyncTime < SYNC_INTERVAL_MS) return;

        lastSyncTime = currentTime;
        needsSync = false;
        if(train.carriages.get(0).anyAvailableEntity() == null){return;}
        Level level = train.carriages.get(0).anyAvailableEntity().level();
        if (level == null || level.isClientSide()) return;

        MinecraftServer server = level.getServer();
        if (server == null) return;

        // Create and send the sync packet
        ETCSSyncPacket packet = new ETCSSyncPacket(
                train.id,
                distanceToSignal,
                speedLimit,
                needleRotationDegrees,
                backward,
                cachedEmergencyBrakingDist,
                cachedServiceBrakingDist,
                cachedWarningBrakingDist,
                cachedCurveIsDropping,
                cachedSpeedLimits
        );

        // Send to all players
        RealismPackets.sendToAllClients(packet, server);
    }

    public void updateFromNetwork(double distanceToSignal, double speedLimit, float needleRotation, boolean backward,
                                  double emergencyBrakingDist, double serviceBrakingDist, double warningBrakingDist,
                                  boolean curveIsDropping, List<SpeedLimit> speedLimits) {
        this.distanceToSignal = distanceToSignal;
        this.speedLimit = speedLimit;
        this.needleRotationDegrees = needleRotation;
        this.backward = backward;
        
        // Update speed limits from network
        this.cachedSpeedLimits = new ArrayList<>(speedLimits);

        // Update the braking distances
        this.cachedEmergencyBrakingDist = emergencyBrakingDist;
        this.cachedServiceBrakingDist = serviceBrakingDist;
        this.cachedWarningBrakingDist = warningBrakingDist;
        this.cachedCurveIsDropping = curveIsDropping;

        this.lastUpdateTime = System.currentTimeMillis();
        // Clear the sync flag since we just received fresh data
        this.needsSync = false;
    }

    /**
     * Save ETCS state to the train's NBT data
     */
    public CompoundTag saveToNBT() {
        if (train == null) return null;

        CompoundTag etcsData = new CompoundTag();
        etcsData.putDouble("distanceToSignal", distanceToSignal);
        etcsData.putDouble("speedLimit", speedLimit);
        etcsData.putFloat("needleRotation", needleRotationDegrees);
        etcsData.putBoolean("backward", backward);
        etcsData.putBoolean("previousBackward", previousBackward);
        etcsData.putLong("lastUpdateTime", lastUpdateTime);

        // Add braking distances and curve dropping flag
        etcsData.putDouble("emergencyBrakingDist", cachedEmergencyBrakingDist);
        etcsData.putDouble("serviceBrakingDist", cachedServiceBrakingDist);
        etcsData.putDouble("warningBrakingDist", cachedWarningBrakingDist);
        etcsData.putBoolean("curveIsDropping", cachedCurveIsDropping);
        etcsData.putDouble("allowedSpeed", cachedAllowedSpeed);
        
        // Save speed limits
        CompoundTag speedLimitsTag = new CompoundTag();
        speedLimitsTag.putInt("size", cachedSpeedLimits.size());
        for (int i = 0; i < cachedSpeedLimits.size(); i++) {
            SpeedLimit limit = cachedSpeedLimits.get(i);
            CompoundTag limitTag = new CompoundTag();
            limitTag.putDouble("distance", limit.getDistance());
            limitTag.putDouble("speed", limit.getSpeedLimit());
            speedLimitsTag.put("limit" + i, limitTag);
        }
        etcsData.put("speedLimits", speedLimitsTag);

        return etcsData;
    }

    /**
     * Load ETCS state from the train's NBT data
     */
    public void loadFromNBT(CompoundTag etcsData) {
        if (train == null) return;

        this.distanceToSignal = etcsData.getDouble("distanceToSignal");
        this.speedLimit = etcsData.getDouble("speedLimit");
        this.needleRotationDegrees = etcsData.getFloat("needleRotation");
        this.backward = etcsData.getBoolean("backward");
        this.previousBackward = etcsData.getBoolean("previousBackward");
        this.lastUpdateTime = etcsData.getLong("lastUpdateTime");

        // Load braking distances and curve dropping flag
        this.cachedEmergencyBrakingDist = etcsData.getDouble("emergencyBrakingDist");
        this.cachedServiceBrakingDist = etcsData.getDouble("serviceBrakingDist");
        this.cachedWarningBrakingDist = etcsData.getDouble("warningBrakingDist");
        this.cachedCurveIsDropping = etcsData.getBoolean("curveIsDropping");
        this.cachedAllowedSpeed = etcsData.getDouble("allowedSpeed");
        
        // Load speed limits
        if (etcsData.contains("speedLimits")) {
            CompoundTag speedLimitsTag = etcsData.getCompound("speedLimits");
            int size = speedLimitsTag.getInt("size");
            this.cachedSpeedLimits = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                CompoundTag limitTag = speedLimitsTag.getCompound("limit" + i);
                double distance = limitTag.getDouble("distance");
                double speed = limitTag.getDouble("speed");
                this.cachedSpeedLimits.add(new SpeedLimit(distance, speed));
            }
        }
    }

}
