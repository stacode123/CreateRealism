package net.Realism.mixin.mixinaccesors;


import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import purplecreate.tramways.content.signs.TramSignPoint;
import purplecreate.tramways.content.signs.demands.SignDemand;

@Mixin(value = TramSignPoint.SignData.class, remap = false)
public interface TramSignDataAccessor {
    @Accessor("pos")
    BlockPos getPos();
    @Accessor("demand")
    SignDemand getDemand();
    @Accessor("demandExtra")
    CompoundTag getDemandExtra();

}
