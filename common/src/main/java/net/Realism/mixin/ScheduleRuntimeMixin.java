package net.Realism.mixin;


import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.Realism.Interfaces.IScheduleRuntimeMixin;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Timetable bookkeeping for {@link net.Realism.trains.schedule.TimeOfDayRealistic}: when the train
 * started waiting at its current stop, and which departure slot it last left on. The latter has to
 * live here rather than in the condition's context tag because it carries across stops.
 * <p>
 * {@code destinationReached} has no level to read the game time from, hence the flag that the next
 * {@code tickConditions} turns into a timestamp.
 */
@Mixin(value = ScheduleRuntime.class, remap = false)
public abstract class ScheduleRuntimeMixin implements IScheduleRuntimeMixin {

    @Shadow
    public ScheduleRuntime.State state;

    @Unique
    private long realism$arrivalGameTime;
    @Unique
    private boolean realism$arrivalPending;
    @Unique
    private long realism$lastDepartureSlot;

    @Inject(method = "destinationReached", at = @At("HEAD"))
    private void realism$markArrival(CallbackInfo ci) {
        if (state == ScheduleRuntime.State.IN_TRANSIT)
            realism$arrivalPending = true;
    }

    @Inject(method = "tickConditions", at = @At("HEAD"))
    private void realism$stampArrival(Level level, CallbackInfo ci) {
        if (!realism$arrivalPending)
            return;
        realism$arrivalPending = false;
        realism$arrivalGameTime = level.getGameTime();
    }

    @Override
    public long getArrivalGameTime() {
        return realism$arrivalGameTime;
    }

    @Override
    public long getLastDepartureSlot() {
        return realism$lastDepartureSlot;
    }

    @Override
    public void setLastDepartureSlot(long slot) {
        realism$lastDepartureSlot = slot;
    }

    /** A fresh schedule starts a fresh timetable rather than chaining onto the old one. */
    @Inject(method = "reset", at = @At("TAIL"))
    private void realism$clearTimetable(CallbackInfo ci) {
        realism$lastDepartureSlot = 0;
        realism$arrivalGameTime = 0;
        realism$arrivalPending = false;
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void realism$write(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putLong("RealismArrivalGameTime", realism$arrivalGameTime);
        tag.putBoolean("RealismArrivalPending", realism$arrivalPending);
        tag.putLong("RealismLastDepartureSlot", realism$lastDepartureSlot);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void realism$read(HolderLookup.Provider registries, CompoundTag tag, CallbackInfo ci) {
        realism$arrivalGameTime = tag.getLong("RealismArrivalGameTime");
        realism$arrivalPending = tag.getBoolean("RealismArrivalPending");
        realism$lastDepartureSlot = tag.getLong("RealismLastDepartureSlot");
    }
}
