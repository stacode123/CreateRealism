package net.Realism.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.Realism.trains.ETCS;
import net.Realism.Interfaces.ITrainInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CarriageContraptionEntity.class,remap = false)
public class CarriageContraptionEntityMixin {

    @Shadow private Carriage carriage;

    @Inject(method = "startControlling", at = @At("RETURN"), cancellable = true)
    private void afterStartControlling(BlockPos controlsLocalPos, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            if (carriage.train instanceof ITrainInterface RTrain) {
                if (RTrain.realism$getETCS() == null) {
                    RTrain.realism$setETCS(new ETCS(carriage.train));
                }
                RTrain.realism$getETCS().previousSignalScanResult = null;
            }
        }
    }
}
