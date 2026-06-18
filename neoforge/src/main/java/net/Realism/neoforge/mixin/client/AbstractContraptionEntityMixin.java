package net.Realism.neoforge.mixin.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.Realism.Interfaces.IOrientedContraptionEntity;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.UUID;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
public class AbstractContraptionEntityMixin {

    @Shadow
    Contraption contraption;

    @Shadow
    public Vec3 toGlobalVector(Vec3 localVec, float partialTicks) {return  null;  }

    @Shadow
    public Vec3 toGlobalVector(Vec3 localVec, float partialTicks, boolean prevAnchor) {return  null;  }


    @Inject(method = "stopControlling", at = @At("TAIL"))
    //@OnlyIn(Dist.CLIENT)
    private void AfterStopControlling(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        //noinspection ConstantValue
        if(this.getClass().equals(CarriageContraptionEntity.class)){
            CarriageContraptionEntity carriageContraptionEntity = (CarriageContraptionEntity)(Object)this;
            Carriage carriage = carriageContraptionEntity.getCarriage();
            if(carriage != null && carriage.train instanceof net.Realism.Interfaces.ITrainInterface RTrain){
                if(RTrain.realism$getETCS() != null){
                    RTrain.realism$getETCS().stop();
                }
            }
        }
    }

    /**
     * @author stacode
     * @reason Inject doesnt work for this usecase
     */
    @Overwrite()
    public Vec3 getPassengerPosition(Entity passenger, float partialTicks) {
        if (contraption == null)
            return null;

        UUID id = passenger.getUUID();
        if (passenger instanceof OrientedContraptionEntity) {
            BlockPos localPos = contraption.getBearingPosOf(id);

            if (localPos != null)

                return toGlobalVector(VecHelper.getCenterOf(localPos), partialTicks)
                        .add(VecHelper.getCenterOf(BlockPos.ZERO))
                        .subtract(.5f, 1, .5f)
                        ;
        }

        AABB bb = passenger.getBoundingBox();
        double ySize = bb.getYsize();
        BlockPos seat = contraption.getSeatOf(id);
        if (seat == null)
            return null;

        if(contraption.entity instanceof IOrientedContraptionEntity orientedEntity){
            double seatOffset = -passenger.getVehicleAttachmentPoint(contraption.entity).y + ySize + .125
                    - SeatEntity.getCustomEntitySeatOffset(passenger);
            return toGlobalVector(Vec3.atLowerCornerOf(seat)
                    .xRot((float) Math.toRadians(-orientedEntity.realism$getViewRoll(partialTicks)))
                    .add(.5, seatOffset, .5), partialTicks)
                    .add(VecHelper.getCenterOf(BlockPos.ZERO))
                    .subtract(0.5, ySize, 0.5);
        }

        double seatOffset = -passenger.getVehicleAttachmentPoint(contraption.entity).y + ySize + .125
                - SeatEntity.getCustomEntitySeatOffset(passenger);
        Vec3 transformedVector = toGlobalVector(Vec3.atLowerCornerOf(seat)
                .add(.5, seatOffset, .5), partialTicks)
                .add(VecHelper.getCenterOf(BlockPos.ZERO))
                .subtract(0.5, ySize, 0.5);
        return transformedVector;
    }

}