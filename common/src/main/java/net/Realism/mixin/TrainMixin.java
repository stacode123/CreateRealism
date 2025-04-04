package net.Realism.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.trains.ETCS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import net.Realism.config.RealismConfig;
import net.Realism.debug.RealismDebuger;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin implements ITrainInterface {
    @Shadow
    public int fuelTicks;

    @Shadow
    public List<Carriage> carriages;

    @Unique
    public ETCS realism$etcs = null;
    /**
     * @author Stacode
     * @reason Change the acceleration to change depending on the amount of carriages and locomotives
     */
    @Overwrite()
    public float acceleration() {
        if (!RealismConfig.COMMON.EnableCustomTrainAcceleration.get()) {
            return (fuelTicks > 0 ? AllConfigs.server().trains.poweredTrainAcceleration.getF()
                    : AllConfigs.server().trains.trainAcceleration.getF()) / 400;
        }
        float ac = (fuelTicks > 0 ? AllConfigs.server().trains.poweredTrainAcceleration.getF() : AllConfigs.server().trains.trainAcceleration.getF()) / 400;
        int locomotives = 0;
        for (Carriage carriage : carriages) {
            if (carriage.presentConductors.either(b -> b)) {
                locomotives++;
            }
        }
        if (locomotives == 0) locomotives = 1;
        float reduce = (float) ((carriages.size() * 0.0002f * RealismConfig.COMMON.CustomTrainAccelerationMultiplyer.get() ) / locomotives);
        ac -= reduce;
        if (ac < 0) ac = 0.0001f;

        if(RealismConfig.CLIENT.debugMode.get()) {
            // Send data to the debug logger instead of printing directly
            RealismDebuger.getInstance().addDebugInfo(ac*400, carriages.size(), locomotives );
        }

        return ac;
    }

    @Unique
    public void realism$setETCS(ETCS etcs) {
        this.realism$etcs = etcs;
    }
    @Unique
    public ETCS realism$getETCS() {
        return this.realism$etcs;
    }
}