package net.Realism.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainStatus;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.config.RealismConfig;
import net.Realism.network.TrainSettingsUpdatePacket;
import net.Realism.trains.TrainSettings;
import net.Realism.trains.etcs.ETCS;
import net.minecraft.core.HolderLookup;
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
    TrainSettings realism$Settings = new TrainSettings();
    @Unique
    public void realism$setSettings(TrainSettings tiltSetting) {this.realism$Settings = tiltSetting;}
    @Unique
    public TrainSettings realism$getSettings() {return this.realism$Settings;}

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
    private void write(DimensionPalette dimensions, HolderLookup.Provider registries,CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag =  cir.getReturnValue();
        if (this.realism$etcs != null) {
            tag.put("ETCS", this.realism$etcs.saveToNBT());

        }
        if (this.realism$Settings != null) {
            cir.getReturnValue().put("trainSettings", this.realism$Settings.savetoNBT());
        }
        cir.setReturnValue(tag);

    }

    @Inject(method = "read", at = @At("RETURN"), cancellable = true)
    private static void onTrainRead(CompoundTag tag, HolderLookup.Provider registries, Map<UUID, TrackGraph> trackNetworks,
                                    DimensionPalette dimensions, CallbackInfoReturnable<Train> cir) {
        Train original = cir.getReturnValue();
        if (tag.contains("ETCS")) {
            CompoundTag customDataTag = tag.getCompound("ETCS");
            ((ITrainInterface)original).realism$setETCS(new ETCS(original));
            ((ITrainInterface)original).realism$getETCS().loadFromNBT(customDataTag);
        }
        if (tag.contains("trainSettings")) {
            CompoundTag tiltTag = tag.getCompound("trainSettings");
            ((ITrainInterface)original).realism$setSettings(TrainSettings.fromNBT(tiltTag));
            RNetworking.sendToAll(new TrainSettingsUpdatePacket(((ITrainInterface)original).realism$getSettings(), original.id));
        }

    }

    /**
     * @author Stacode
     * @reason Change the acceleration to change depending on the number of carriages and locomotives
     */

    @Overwrite()
    public float acceleration() {
        if (!RealismConfig.COMMON.EnableCustomTrainAcceleration.get() || this.realism$Settings.isAccelerationNone()) {
            return (fuelTicks > 0 ? AllConfigs.server().trains.poweredTrainAcceleration.getF()
                    : AllConfigs.server().trains.trainAcceleration.getF()) / 400;
        }

        if (this.realism$Settings.isAccelerationRealistic()) {
            float ac = (fuelTicks > 0 ? AllConfigs.server().trains.poweredTrainAcceleration.getF() : AllConfigs.server().trains.trainAcceleration.getF()) / 400;
            int locomotives = 0;
            for (Carriage carriage : carriages) {
                if (carriage.presentConductors.either(b -> b)) {
                    locomotives++;
                }
            }
            if (locomotives == 0) locomotives = 1;
            float reduce = (float) ((carriages.size() * 0.0002f * RealismConfig.COMMON.CustomTrainAccelerationMultiplyer.get()) / locomotives);
            ac -= reduce;
            if (ac < 0) ac = 0.0001f;



            return ac;
        }
        else{
            return (float) this.realism$Settings.customAcceleration/400;
        }


    }


}