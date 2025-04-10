package net.Realism.trains.etcs;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.Realism.RNetworking;
import net.Realism.compat.TramwaysCompat;
import net.Realism.config.RealismConfig;
import net.Realism.network.ETCSSyncPacket;
import net.Realism.network.SteerDirectionPacket;
import net.Realism.trains.SignalFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.Realism.trains.SignalFinder.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Stream;

import static net.Realism.compat.isModLoaded.isTramwaysLoaded;
import static net.Realism.network.SteerDirectionPacket.KeyPressType.*;
import static net.Realism.trains.etcs.ETCStools.optimizedRenderSpeedCurve;
import static net.Realism.trains.etcs.ETCStools.renderElement;


public class ETCS {
    boolean backward = false;
    public Train train;
    public SignalScanResult previousSignalScanResult;
    boolean previousBackward;

    private double speedLimit = -1; // -1 means no limit
    private double distanceToSignal = 0;
    private float needleRotationDegrees = 0;
    
    // Last update timestamp to handle client/server synchronization
    private long lastUpdateTime = 0;

    private boolean needsSync = false;
    private long lastSyncTime = 0;
    private static final int SYNC_INTERVAL_MS = 200; // Sync every 200ms

    private int curvedropping;
    private int diffrenceCounter=0;
    private int zoom = 1;

    // Braking distances
    private double cachedEmergencyBrakingDist;
    private double cachedServiceBrakingDist;
    private double cachedWarningBrakingDist;
    private boolean cachedCurveIsDropping = false;
    private double cachedAllowedSpeed = -1;
    private List<SpeedLimit> cachedSpeedLimits = new ArrayList<>();
    private final Font font = Minecraft.getInstance().font;

    private boolean plusKeyWasDown = false;
    private boolean minusKeyWasDown = false;
    private long lastKeyPressTime = 0;
    private static final long KEY_COOLDOWN_MS = 300;
    
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
        ReciveKeys();



        SignalScanResult s = SignalFinder.scanAheadForSignals(train, (double) 4000 /zoom, backward);
        
        if (s != null) {
            // Logic to decide whether to use previous scan result
            if (previousSignalScanResult != null) {
                if (diffrenceCounter<10){
                if (((Math.abs(s.getDistanceToClosestOccupiedSignal() - previousSignalScanResult.getDistanceToClosestOccupiedSignal()) > 100)
                        && backward == previousBackward)) {
                    diffrenceCounter+=1;
                    s = previousSignalScanResult;
                }}
                else {
                    diffrenceCounter = 0;
                }
            }
            this.previousSignalScanResult = s;
            // New variables to store calculation results
            SignalScanResult currentSignalScanResult = s;
            this.distanceToSignal = s.getDistanceToClosestOccupiedSignal();

            // Process tram signs for speed limits
            if (isTramwaysLoaded()) {
                if (trackspeedlimit==0){
                    trackspeedlimit = 20;
                }
                cachedSpeedLimits = TramwaysCompat.processTramSigns(s,train.maxSpeed()*20*3.6f);
            } else {
                // Reset speed limits when Tramways isn't loaded
                cachedSpeedLimits = new ArrayList<>();
                trackspeedlimit = 300;
            }

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
     * Render method to be called during render cycle.
     * Only contains rendering code, using data calculated in update().
     */
    public void render(GuiGraphics graphics) {
        // Load the most current data before rendering
        PoseStack posestack = graphics.pose();
        double ScaleFactor = RealismConfig.CLIENT.ETCSSize.get();
        posestack.pushPose();
        posestack.scale((float) ScaleFactor, (float) ScaleFactor, (float) ScaleFactor);
        sendKeysToServer();
        
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        Minecraft.getInstance().getWindow();
        int xPos = (int) ((screenWidth/ScaleFactor) - 536);  // 10 scaled pixels from right edge
        int yPos = 0;

        if (zoom == 0){
            zoom = 1;
        }
        // Render the ETCS panel background
        String zoomTexture = String.format("realism:textures/etcszoom%d.png", zoom);
        RenderSystem.setShaderTexture(0, new ResourceLocation(zoomTexture));
        graphics.blit(new ResourceLocation(zoomTexture),
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
                0, 0,   // screen position
                0, 0,         // texture position (top left of texture)
                49, 112,   // width and height to render
                49, 112);
        posestack.popPose();

        //graphics.drawString(mc.font,String.valueOf((int)train.speed*20*3.6),xPos + 156,yPos+124,0x294A6DFF);

        // Render ETCS limits display
        renderETCSlimits(graphics, posestack, xPos + 10, yPos + 10);
        renderOverviewItems(graphics,xPos, yPos, zoom);
        renderBrakingCurve(graphics, posestack, xPos+10, yPos+10);
        
        posestack.popPose();
    }

    public void renderETCSlimits(GuiGraphics graphics, PoseStack posestack, int Xpos, int Ypos) {
        posestack.pushPose();
        posestack.translate(Xpos + 361, Ypos + 227, 0);
        int CurrentY = 0;

        ResourceLocation startTex = new ResourceLocation("realism:textures/etcsplusstart.png");
        ResourceLocation midTex = new ResourceLocation("realism:textures/etcsplusmid.png");
        ResourceLocation endTex = new ResourceLocation("realism:textures/etcsplusend.png");
        ResourceLocation flagTex = new ResourceLocation("realism:textures/flag.png");

        // Scale boundaries based on zoom
        double scaledMax = 4000.0 / zoom;
        double scaledBoundary1 = 500.0 / zoom;
        double scaledBoundary2 = 1000.0 / zoom;
        double scaledBoundary3 = 2000.0 / zoom;

        // Handle case where distance is beyond max range
        if (distanceToSignal > scaledMax) {
            CurrentY -= 9;
            renderElement(graphics, startTex, 0, CurrentY, 15, 9);

            for (int i = 0; i < 198; i++) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }

            posestack.popPose();
            return;
        }

        // Handle very short distances
        if (distanceToSignal < 60) {
            double pixelLength = distanceToSignal * 0.304 * zoom;
            for (int i = 0; i < (int)pixelLength; i++) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // Start rendering the normal case
        CurrentY -= 9;
        renderElement(graphics, startTex, 0, CurrentY, 15, 9);

        // Calculate pixel length for first segment
        double pixelLength;
        if (distanceToSignal <= scaledBoundary1) {
            pixelLength = distanceToSignal * 0.21 * zoom - 18;
        } else {
            pixelLength = Math.min(96.0, distanceToSignal * 0.21 * zoom - 9);
        }

        // Render first segment
        for (int i = 0; i < (int)pixelLength; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within first boundary
        if (distanceToSignal <= scaledBoundary1) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // Calculate pixel length for second segment
        if (distanceToSignal < scaledBoundary2) {
            pixelLength = (distanceToSignal - scaledBoundary1) * 0.068 * zoom;
        } else {
            pixelLength = Math.min(34.0, (distanceToSignal - scaledBoundary1) * 0.068 * zoom);
        }

        // Render second segment
        for (int i = 0; i < (int)pixelLength; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within second boundary
        if (distanceToSignal <= scaledBoundary2) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // Calculate pixel length for third segment
        if (distanceToSignal <= scaledBoundary3) {
            pixelLength = (distanceToSignal - scaledBoundary2) * 0.034 * zoom;
        } else {
            pixelLength = Math.min(34.0, (distanceToSignal - scaledBoundary2) * 0.034 * zoom);
        }

        // Render third segment
        for (int i = 0; i < (int)pixelLength; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within third boundary
        if (distanceToSignal <= scaledBoundary3) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // Calculate and render fourth segment (beyond 2000)
        pixelLength = (distanceToSignal - scaledBoundary3) * 0.017 * zoom;
        for (int i = 0; i < (int)pixelLength; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // End rendering
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

    public void renderOverviewItems(GuiGraphics graphics, int xPos, int yPos, int zoom) {
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


    public int calculateDistancePixelPosition(double distance, int zoom) {
        int pixelPos = 0;

        // Scale distance boundaries by zoom factor
        double scaledMax = 4000.0 / zoom;
        double scaledBoundary1 = 500.0 / zoom;
        double scaledBoundary2 = 1000.0 / zoom;
        double scaledBoundary3 = 2000.0 / zoom;

        if (distance > scaledMax) {
            return 198; // Maximum offset
        }

        // First range: 0-500/zoom units
        double range1 = Math.min(scaledBoundary1, distance);
        pixelPos += (int)(range1 * 0.21 * zoom);

        if (distance <= scaledBoundary1) {
            return pixelPos;
        }

        // Second range: 501-1000/zoom units
        double range2 = Math.min(scaledBoundary2, distance) - scaledBoundary1;
        pixelPos += (int)(range2 * 0.068 * zoom);

        if (distance <= scaledBoundary2) {
            return pixelPos;
        }

        // Third range: 1001-2000/zoom units
        double range3 = Math.min(scaledBoundary3, distance) - scaledBoundary2;
        pixelPos += (int)(range3 * 0.034 * zoom);

        if (distance <= scaledBoundary3) {
            return pixelPos;
        }

        // Fourth range: 2001+ units
        double range4 = distance - scaledBoundary3;
        pixelPos += (int)(range4 * 0.017 * zoom);

        return pixelPos;
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
                cachedSpeedLimits,
                zoom
        );

        // Send to all players
        RNetworking.sendToAll(packet);
    }

    public void updateFromNetwork(double distanceToSignal, double speedLimit, float needleRotation, boolean backward,
                                  double emergencyBrakingDist, double serviceBrakingDist, double warningBrakingDist,
                                  boolean curveIsDropping, List<SpeedLimit> speedLimits,int zoom) {
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
        this.zoom = zoom;
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
        etcsData.putInt("zoom", zoom);
        etcsData.putInt("trackspeedlimit", trackspeedlimit);
        
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
        this.zoom = etcsData.getInt("zoom");
        this.trackspeedlimit = etcsData.getInt("trackspeedlimit");
        
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

    private void sendKeysToServer() {
        boolean plusKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_EQUALS);
        boolean minusKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_MINUS);
        long currentTime = System.currentTimeMillis();

        // Detect key press events (transition from not pressed to pressed)
        if (plusKeyDown && !plusKeyWasDown && currentTime - lastKeyPressTime > KEY_COOLDOWN_MS) {
            RNetworking.sendToServer(new SteerDirectionPacket(PLUS));
            lastKeyPressTime = currentTime;
        } else if (minusKeyDown && !minusKeyWasDown && currentTime - lastKeyPressTime > KEY_COOLDOWN_MS) {
            RNetworking.sendToServer(new SteerDirectionPacket(MINUS));
            lastKeyPressTime = currentTime;
        } else if (!plusKeyDown && !minusKeyDown) {
            // Only send NONE when both keys are released
            RNetworking.sendToServer(new SteerDirectionPacket(NONE));
        }

        // Update previous key states
        plusKeyWasDown = plusKeyDown;
        minusKeyWasDown = minusKeyDown;
    }

    private void ReciveKeys() {
        Optional<UUID> controllingPlayerUuid = train.carriages.stream()
                .flatMap(carriage -> {
                    CarriageContraptionEntity entity = carriage.anyAvailableEntity();
                    return entity != null ? Stream.of(entity.getControllingPlayer()) : Stream.empty();
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (!controllingPlayerUuid.isPresent()) return;

        SteerDirectionPacket.KeyPressType currentKeyPress = SteerDirectionPacket.getPlayerKeyPress(controllingPlayerUuid.get());
        if (zoom == 0){
            zoom = 1;
        }
        // Only process key press once
        switch (currentKeyPress) {
            case PLUS:
                if (zoom < 4) {
                    zoom *= 2;
                    // Reset key state after processing
                    SteerDirectionPacket.setPlayerKeyPresses(controllingPlayerUuid.get(), NONE);
                }
                break;
            case MINUS:
                if (zoom > 1) {
                    zoom /= 2;
                    // Reset key state after processing
                    SteerDirectionPacket.setPlayerKeyPresses(controllingPlayerUuid.get(), NONE);
                }
                break;
        }
    }
}
