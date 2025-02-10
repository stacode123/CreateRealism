package net.Realism.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.infrastructure.config.AllConfigs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin {
    @Shadow
    public int fuelTicks;
    public List<Carriage> carriages;

    /**
     * @author Stacode
     * @reason Change the acceleration to change depending on the amount of carriages and locomotives
     */
    @Overwrite()
    public float acceleration() {
        float ac = (fuelTicks > 0 ? AllConfigs.server().trains.poweredTrainAcceleration.getF() : AllConfigs.server().trains.trainAcceleration.getF()) / 400;
        int locomotives = 0;
        for (Carriage carriage : carriages) {
            if (carriage.presentConductors.either(b -> b)) {
                locomotives++;
            }
        }
        if (locomotives == 0) locomotives = 1;
        float reduce = (carriages.size() * 0.0002f) / locomotives;
        ac -= reduce;
        if (ac < 0) ac = 0.0001f;
        return ac;
    }


}