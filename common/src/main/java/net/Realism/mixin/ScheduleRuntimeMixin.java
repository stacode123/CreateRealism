package net.Realism.mixin;


import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.Realism.Interfaces.IScheduleRuntimeMixin;
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
 * Timetable bookkeeping for {@link net.Realism.trains.schedule.TimeOfDayRealistic}: the day time
 * the train started waiting at its current stop, and the day time it last departed on. Both are
 * tracked in day time rather than game time, so a mod that changes how fast day time advances (or
 * freezes it) cannot desync the timetable from what the world clock actually shows. The latter has
 * to live here rather than in the condition's context tag because it carries across stops.
 * <p>
 * {@code destinationReached} has no level to read the day time from, hence the flag that the next
 * {@code tickConditions} turns into a timestamp.
 */
@Mixin(value = ScheduleRuntime.class, remap = false)
public abstract class ScheduleRuntimeMixin implements IScheduleRuntimeMixin {

    @Shadow
    public ScheduleRuntime.State state;

    @Unique
    private long realism$arrivalDayTime;
    @Unique
    private boolean realism$arrivalPending;
    @Unique
    private long realism$lastDepartureDayTime;

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
        realism$arrivalDayTime = level.getDayTime();
    }

    @Override
    public long getArrivalDayTime() {
        return realism$arrivalDayTime;
    }

    @Override
    public long getLastDepartureDayTime() {
        return realism$lastDepartureDayTime;
    }

    @Override
    public void setLastDepartureDayTime(long dayTime) {
        realism$lastDepartureDayTime = dayTime;
    }

    /** A fresh schedule starts a fresh timetable rather than chaining onto the old one. */
    @Inject(method = "reset", at = @At("TAIL"))
    private void realism$clearTimetable(CallbackInfo ci) {
        realism$lastDepartureDayTime = 0;
        realism$arrivalDayTime = 0;
        realism$arrivalPending = false;
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void realism$write(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putLong("RealismArrivalDayTime", realism$arrivalDayTime);
        tag.putBoolean("RealismArrivalPending", realism$arrivalPending);
        tag.putLong("RealismLastDepartureDayTime", realism$lastDepartureDayTime);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void realism$read(CompoundTag tag, CallbackInfo ci) {
        realism$arrivalDayTime = tag.getLong("RealismArrivalDayTime");
        realism$arrivalPending = tag.getBoolean("RealismArrivalPending");
        realism$lastDepartureDayTime = tag.getLong("RealismLastDepartureDayTime");
    }
}
