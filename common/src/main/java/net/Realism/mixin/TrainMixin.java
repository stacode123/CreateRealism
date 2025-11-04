package net.Realism.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainStatus;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.config.RealismConfig;
import net.Realism.debug.RealismDebuger;
import net.Realism.trains.etcs.ETCS;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(value = Train.class,remap = false)
public abstract class TrainMixin implements ITrainInterface {

    @Shadow
    public int fuelTicks;

    @Shadow
    public List<Carriage> carriages;

    @Shadow public ScheduleRuntime runtime;
    @Shadow public TrainStatus status;
    @Shadow public boolean manualTick;
    @Unique
    public ETCS realism$etcs = null;

    @Unique
    public void realism$setETCS(ETCS etcs) {
        this.realism$etcs = etcs;
    }
    @Unique
    public ETCS realism$getETCS() {
        return this.realism$etcs;
    }

    @Inject(method = "tick", at = @At(value = "TAIL"))
    private void tick(CallbackInfo ci) {
        if (this.realism$etcs == null) {
            this.realism$setETCS(new ETCS((Train)(Object)this));
        }
        if(this.realism$etcs.toUpdate){
        this.realism$etcs.update();
        }
    }
    @Inject(method = "write", at = @At(value = "RETURN"), cancellable = true)
    private void write(DimensionPalette dimensions, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.realism$etcs != null) {
            CompoundTag tag =  cir.getReturnValue();
            tag.put("ETCS", this.realism$etcs.saveToNBT());
            cir.setReturnValue(tag);
        }

    }
    @Inject(method = "read", at = @At("RETURN"), cancellable = true)
    private static void onTrainRead(CompoundTag tag, Map<UUID, TrackGraph> trackNetworks,
                                    DimensionPalette dimensions, CallbackInfoReturnable<Train> cir) {
        Train original = cir.getReturnValue();
        if (tag.contains("ETCS")) {
            CompoundTag customDataTag = tag.getCompound("ETCS");
            ((ITrainInterface)original).realism$setETCS(new ETCS(original));
            ((ITrainInterface)original).realism$getETCS().loadFromNBT(customDataTag);
        }
    }

    /**
     * @author Stacode
     * @reason Change the acceleration to change depending on the number of carriages and locomotives
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


}