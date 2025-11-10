package net.Realism.mixin;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.Realism.Interfaces.IOrientedContraptionEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin to add roll (banking) functionality to OrientedContraptionEntity
 */
@Mixin(value = OrientedContraptionEntity.class, remap = false)
public abstract class OrientedContraptionEntityMixin implements IOrientedContraptionEntity {
    
    @Unique
    private float realism$roll = 0;
    
    @Unique
    private float realism$prevRoll = 0;

    @Unique
    public UUID getuid(){
        return ((net.minecraft.world.entity.Entity)(Object)this).getUUID();
    }

    @Override
    @Unique
    public float realism$getRoll() {
        return realism$roll;
    }

    @Override
    @Unique
    public void realism$setRoll(float roll) {
        this.realism$roll = roll;
    }

    @Override
    @Unique
    public float realism$getPrevRoll() {
        return realism$prevRoll;
    }

    @Override
    @Unique
    public void realism$setPrevRoll(float prevRoll) {
        this.realism$prevRoll = prevRoll;
    }

    @Override
    @Unique
    public float realism$getViewRoll(float partialTicks) {
        return Mth.lerp(partialTicks, realism$prevRoll, realism$roll);
    }

    /**
     * Save roll to NBT
     */
    @Inject(method = "writeAdditional", at = @At("TAIL"))
    protected void writeRollToNBT(CompoundTag compound, boolean spawnPacket, CallbackInfo ci) {
        compound.putFloat("Roll", realism$roll);
    }

    /**
     * Load roll from NBT
     */
    @Inject(method = "readAdditional", at = @At("TAIL"))
    protected void readRollFromNBT(CompoundTag compound, boolean spawnPacket, CallbackInfo ci) {
        realism$roll = compound.getFloat("Roll");
        realism$prevRoll = realism$roll;
    }

    /**
     * Inject roll into ContraptionRotationState
     */
//    @Inject(method = "getRotationState", at = @At("RETURN"), cancellable = true)
//    public void injectRollIntoRotationState(@NotNull CallbackInfoReturnable<AbstractContraptionEntity.ContraptionRotationState> cir) {
//        AbstractContraptionEntity.ContraptionRotationState crs = cir.getReturnValue();
//
//        // Set xRotation to our roll value
//        ((ContraptionRotationStateAccessor) crs).setXRotation(realism$roll);
//
//        cir.setReturnValue(crs);
//    }
}

