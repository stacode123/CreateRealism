package net.Realism.mixin;


import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.Schedule;
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

import java.util.List;

@Mixin(value = ScheduleRuntime.class, remap = false)
public abstract class ScheduleRuntimeMixin implements IScheduleRuntimeMixin {
    @Shadow
    private static final int TBD = -1;
    @Shadow
    Schedule schedule;
    @Shadow
    public boolean paused;
    @Shadow
    public Train train;
    @Shadow
    public int ticksInTransit;
    @Shadow
    public List<Integer> predictionTicks;
    @Shadow
    public int currentEntry;


    @Shadow
    protected abstract boolean checkEndOfScheduleReached();

    @Unique
    public long departureDate;
    @Unique
    public boolean shouldUpdateDepartureDate;
    @Unique
    public long expectedArrivalDate;
    @Unique
    public int lastScheduledDepartureDate = 0;
    @Unique
    public boolean dontCheck = false;


    @Inject(method = "tick",at=@At("HEAD"))
    public void tickDate(Level level, CallbackInfo ci) {
        if (schedule == null)
            return;
        if (paused)
            return;
        if (train.derailed)
            return;
        if (train.navigation.destination != null) {
            ticksInTransit++;
            if(shouldUpdateDepartureDate){
                long x = level.getGameTime();
                departureDate = x;
                shouldUpdateDepartureDate = false;
            }
            return;
        }

        if(checkEndOfScheduleReached() || shouldUpdateDepartureDate){
            long x = level.getGameTime();
            departureDate = x;
            shouldUpdateDepartureDate = false;
        }

    }

    @Inject(method = "setSchedule", at=@At("TAIL"))
    public void setSchedule(Schedule schedule, boolean auto, CallbackInfo ci) {
         shouldUpdateDepartureDate = true;
         setDontCheck(false);
    }

    @Inject(method = "reset", at=@At("TAIL"))
    public void reset(CallbackInfo ci) {
        shouldUpdateDepartureDate = true;
        dontCheck = false;
    }

    @Override
    public long getDepartureDate() {
        return departureDate;
    }

    @Override
    public long getExpectedArrivalDate() {
        return expectedArrivalDate;
    }

    @Override
    public int getLastScheduledDepartureDate() {
        return lastScheduledDepartureDate;
    }

    @Override
    public void setLastScheduledDepartureDate(int date) {
        lastScheduledDepartureDate = date;
    }

    @Override
    public void setExpectedArrivalDate(long date) {
        expectedArrivalDate = date;
    }

    @Override
    public boolean dontCheck() {
        return dontCheck;
    }
    @Override
    public void setDontCheck(boolean dontCheck) {
        this.dontCheck = dontCheck;
    }


    @Inject(method = "startCurrentInstruction", at=@At("RETURN"))
    public void startCurrentInstruction(Level level, CallbackInfoReturnable<DiscoveredPath> cir) {
         DiscoveredPath nextPath = cir.getReturnValue();
         setDontCheck(false);
        shouldUpdateDepartureDate = true;
        int etaTicksToDestination;
        int learned = (predictionTicks != null && predictionTicks.size() > currentEntry)
                ? predictionTicks.get(currentEntry)
                : TBD; // TBD == -1

        if (learned >= 0) {
            // Use learned transit time for this entry
            etaTicksToDestination = learned;
        } else if (nextPath != null) {
            // Fallback: estimate from path distance and speed heuristic (same as submitPredictions)
            double speed = Math.min(train.throttle * train.maxSpeed(), (train.maxSpeed() + train.maxTurnSpeed()) / 2.0);
            // Protect against divide-by-zero; if the speed is ~0, report TBD
            if (speed > 1e-6) {
                etaTicksToDestination = (int) (Math.abs(nextPath.distance) / speed) * 2; // matches submitPredictions()
            } else {
                etaTicksToDestination = TBD; // can’t estimate right now
            }
        } else {
            etaTicksToDestination = TBD; // no path available
        }
        expectedArrivalDate  = (etaTicksToDestination + level.getGameTime());
    }

    @Inject(method = "write", at=@At("RETURN"), cancellable = true)
    public void write(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putLong("departureDate", departureDate);
        tag.putLong("expectedArrivalDate", expectedArrivalDate);
        tag.putInt("lastScheduledDepartureDate", lastScheduledDepartureDate);
        tag.putBoolean("dontCheck", dontCheck);
        cir.setReturnValue(tag);

    }

    @Inject(method = "read", at=@At("TAIL"))
    public void read(HolderLookup.Provider registries, CompoundTag tag, CallbackInfo ci) {
        departureDate = tag.getLong("departureDate");
        expectedArrivalDate = tag.getLong("expectedArrivalDate");
        lastScheduledDepartureDate = tag.getInt("lastScheduledDepartureDate");
        dontCheck = tag.getBoolean("dontCheck");
    }
}
