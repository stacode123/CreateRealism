package net.Realism.mixin;

import net.Realism.mixinaccesors.TramSignDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import purplecreate.tramways.content.signs.TramSignPoint;
import purplecreate.tramways.content.signs.demands.SignDemand;

@Mixin(value = TramSignPoint.SignData.class, remap = false)
public class TramSignDataMixin implements TramSignDataAccessor {
    @Shadow
    BlockPos pos;
    @Shadow
    SignDemand demand;
    @Shadow
    CompoundTag demandExtra;

    @Override
    public BlockPos getPos(){
        return pos;
    }
    @Override
    public SignDemand getDemand(){
        return demand;
    }
    @Override
    public CompoundTag getDemandExtra(){
        return demandExtra;
    }

}
