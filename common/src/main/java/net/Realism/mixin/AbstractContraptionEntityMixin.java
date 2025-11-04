package net.Realism.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContraptionEntity.class,remap = false)
public class AbstractContraptionEntityMixin {



    @Inject(method = "stopControlling", at = @At("TAIL"))
    private void AfterStopControlling(CallbackInfo ci) {
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
}
