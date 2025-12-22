package net.Realism.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.IOrientedContraptionEntity;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.config.RealismConfig;
import net.Realism.foundation.network.RollSyncPacket;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Mixin for Carriage.DimensionalCarriageEntity to calculate banking angles
 */
@Mixin(targets = "com.simibubi.create.content.trains.entity.Carriage$DimensionalCarriageEntity", remap = false)
public class CarriageDimensionalEntityMixin {


    @Shadow
    @Final
    Carriage this$0; // The outer Carriage instance

    @Shadow
    public Couple<Vec3> rotationAnchors;

    /**
     * Inject banking calculation into alignEntity method
     */
    @Inject(method = "alignEntity", at = @At("TAIL"))
    private void calculateAndApplyBanking(CarriageContraptionEntity entity, CallbackInfo ci) {
        // Check if banking is enabled
        if (!RealismConfig.CLIENT.enableBanking.get() || !RealismConfig.COMMON.GlobalBankingEnable.get()) {
            return;
        }

        // Ensure we have valid rotation anchors
        if (rotationAnchors.either(Objects::isNull)) {
            return;
        }

        // Update previous roll
        IOrientedContraptionEntity orientedEntity = (IOrientedContraptionEntity) entity;
        orientedEntity.realism$setPrevRoll(orientedEntity.realism$getRoll());

        // Calculate banking
        if (!(this$0.train instanceof ITrainInterface Rtrain)){
            return;
        }
        float banking = realism$calculateBanking(entity,Rtrain);
        orientedEntity.realism$setRoll(banking);
        if(!entity.level().isClientSide) {
            RNetworking.sendToAll(new RollSyncPacket(orientedEntity.realism$getRoll(), orientedEntity.realism$getPrevRoll(), orientedEntity.getuid()));
        }
        // For first position update, ensure prevRoll matches roll
        if (entity.firstPositionUpdate) {
            orientedEntity.realism$setPrevRoll(banking);
            if(!entity.level().isClientSide){
                RNetworking.sendToAll(new RollSyncPacket(orientedEntity.realism$getRoll(),orientedEntity.realism$getPrevRoll(),orientedEntity.getuid()));
            }
        }

    }

    /**
     * Calculate banking angle based on track curvature and train speed
     */
    @Unique
    private float realism$calculateBanking(CarriageContraptionEntity entity,ITrainInterface Rtrain) {
        // Get the leading bogey
        CarriageBogey leadingBogey = this$0.leadingBogey();
        if (leadingBogey == null) {
            return 0f;
        }

        // Get the leading travelling point
        TravellingPoint leadingPoint = leadingBogey.leading();
        if (leadingPoint == null || leadingPoint.edge == null) {
            return 0f;
        }

        TrackEdge edge = leadingPoint.edge;

        // Only apply banking on curves (BezierConnections)
        if (!edge.isTurn()) {
            return(0f);
        }

        BezierConnection turn = edge.getTurn();
        if (turn == null || turn.normals == null) {
            return 0f;
        }

        // Calculate normalized position along the curve (0.0 to 1.0)
        double edgeLength = edge.getLength();
        double t = edgeLength == 0 ? 0.5 : leadingPoint.position / edgeLength;
        t = Mth.clamp(t, 0.0, 1.0);

        // Get interpolated track normal at current position
        Vec3 trackNormal = realism$getTrackNormalAt(turn, t);
        if (trackNormal == null) {
            return 0f;
        }

        // Get train's forward direction
        Vec3 positionVec = rotationAnchors.getFirst();
        Vec3 coupledVec = rotationAnchors.getSecond();

        double diffX = positionVec.x - coupledVec.x;
        double diffY = positionVec.y - coupledVec.y;
        double diffZ = positionVec.z - coupledVec.z;

        Vec3 forward = new Vec3(diffX, diffY, diffZ).normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = forward.cross(worldUp).normalize();

        // Project track normal onto right vector to get banking
        double bankingDot = trackNormal.dot(right);
        bankingDot = Mth.clamp(bankingDot, -1.0, 1.0);
        float bankingAngle = (float) Math.toDegrees(Math.asin(bankingDot));

        // Apply intensity multiplier
        float intensity = 1;
        bankingAngle *= 0.02f;
        bankingAngle *= intensity;
        if (Rtrain.realism$getSettings().isTiltPassive()){
            bankingAngle *= 2f;
        }
        if (Rtrain.realism$getSettings().isTiltActive()){
            bankingAngle *= 4.5f;
        }
        if (Rtrain.realism$getSettings().isTiltCustom()){
            bankingAngle *= Rtrain.realism$getSettings().customTiltIntensity;
        }

        double speed = this$0.train.speed*20*3.6f;
        float minSpeed = 80f;
        if(Rtrain.realism$getSettings().isTiltCustom()){
            minSpeed = Rtrain.realism$getSettings().customMinSpeed.floatValue();
        }
        if(Rtrain.realism$getSettings().isTiltActive()){
            minSpeed = 1/2 *minSpeed;
        }
        float maxSpeed = AllConfigs.server().trains.trainTopSpeed.getF();
        float speedFactor;
        if (Math.abs(speed) <= minSpeed) {
            speedFactor = 0f;
        } else if (speed >= maxSpeed) {
            speedFactor = 1f;
        } else {
            float range = maxSpeed - minSpeed;
            if (range <= 0f) {
                speedFactor = 1f;
            } else {
                speedFactor =(float) ((Math.abs(speed) - minSpeed) / range);
                speedFactor = Mth.clamp(speedFactor, 0f, 1f);
            }
        }


        float targetBanking = bankingAngle * speedFactor;

        // Clamp to maximum banking angle
        float maxAngle = 15;
        if(Rtrain.realism$getSettings().isTiltCustom()){
            maxAngle = Rtrain.realism$getSettings().customMaxTilt;
        }
        targetBanking = Mth.clamp(targetBanking, -maxAngle, maxAngle);
        if(Rtrain.realism$getSettings().isTiltNone() || !(Rtrain.realism$getSettings().isInside())){
            return targetBanking;
        }
        else{
            return -targetBanking;
        }
    }

    /**
     * Interpolate track normal at position t along the curve
     */
    @Unique
    private Vec3 realism$getTrackNormalAt(BezierConnection turn, double t) {
        if (turn.normals == null) {
            return null;
        }

        Vec3 normal1 = turn.axes.getFirst();
        Vec3 normal2 = turn.axes.getSecond();

        // Linear interpolation between start and end normals
        Vec3 interpolated = VecHelper.lerp((float) t, normal1, normal2);

        // Normalize to ensure it's a unit vector
        double length = interpolated.length();
        if (length < 0.001) {
            return new Vec3(0, 1, 0); // Default to up
        }

        return interpolated.normalize();
    }
}

