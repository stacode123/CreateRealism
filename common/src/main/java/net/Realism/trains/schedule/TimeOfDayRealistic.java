package net.Realism.trains.schedule;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.condition.TimeOfDayCondition;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import net.Realism.Interfaces.IScheduleRuntimeMixin;
import net.Realism.RealismMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableObject;

public class TimeOfDayRealistic extends TimeOfDayCondition {
    public TimeOfDayRealistic() {
        data.putInt("Hour", 8);
        data.putInt("Minute", 0);
        data.putInt("Rotation", 0);
    }
    @Override
    public ResourceLocation getId() {
        return RealismMod.id("time_of_day_realistic");
    }

    @Override
    public boolean tickCompletion(Level level, Train train, CompoundTag context) {
        int maxTickDiff = 10;
        IScheduleRuntimeMixin runtime = (IScheduleRuntimeMixin) train.runtime;
        int targetHour = intData("Hour");
        int targetMinute = intData("Minute");
        long gtime = level.getGameTime();
        long depDate = runtime.getDepartureDate();
        long wtme = level.getDayTime();
        int wctime = (int) (wtme+6000)%24000;
        int gctime = (int) (gtime+6000)%24000;
        int discrepancy = 6000+ (wctime - gctime);
        int date = (int) Math.floor((double) (gtime+discrepancy)/24000);
        int expectedTargetDate = (int) Math.floor((double) (runtime.getExpectedArrivalDate()+discrepancy) /24000);
        int targetTicks = (int)((expectedTargetDate * 24000) + (targetHour * 1000) +  Math.ceil(targetMinute / 60f * 1000));
        int diff = (int) (targetTicks - (gtime+discrepancy));
        int targetDayTime = (int) ((targetHour * 1000) +  Math.ceil(targetMinute / 60f * 1000));
        int depDayTime = runtime.getLastScheduledDepartureDate();
        //Handle same station, same day rollover
        if (diff < 0  && Math.abs(depDate -gtime) < maxTickDiff && !(runtime.dontCheck())){
            long x = runtime.getExpectedArrivalDate()+ 24000;
            runtime.setExpectedArrivalDate(x);
            targetTicks = (int) (((expectedTargetDate+1) * 24000) + (targetHour * 1000) +  Math.ceil(targetMinute / 60f * 1000));
            diff = (int) (targetTicks - (gtime+discrepancy));
        }
        //Handle same day rollover with travel
        else if (diff < 0 && targetDayTime < depDayTime &&!(runtime.dontCheck())){
            long x = runtime.getExpectedArrivalDate()+24000;
            runtime.setExpectedArrivalDate(x);
            targetTicks = (int) (((expectedTargetDate+1) * 24000) + (targetHour * 1000) +  Math.ceil(targetMinute / 60f * 1000));
            diff = (int) (targetTicks - (gtime+discrepancy));
        }

        runtime.setDontCheck(true);

        if (diff<=0) {
            runtime.setLastScheduledDepartureDate(targetDayTime);
            return diff <= 0;
        }
        return false;
    }

    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        MutableObject<ScrollInput> minuteInput = new MutableObject<>();
        MutableObject<ScrollInput> hourInput = new MutableObject<>();
        MutableObject<Label> timeLabel = new MutableObject<>();

        builder.addScrollInput(0, 16, (i, l) -> {
            i.withRange(0, 24);
            timeLabel.setValue(l);
            hourInput.setValue(i);
        }, "Hour");

        builder.addScrollInput(18, 16, (i, l) -> {
            i.withRange(0, 60);
            minuteInput.setValue(i);
            l.visible = false;
        }, "Minute");

        hourInput.getValue()
                .titled(CreateLang.translateDirect("generic.daytime.hour"))
                .calling(t -> {
                    data.putInt("Hour", t);
                    timeLabel.getValue().text = getDigitalDisplay(t, minuteInput.getValue()
                            .getState(), true);
                })
                .writingTo(null)
                .withShiftStep(6);

        minuteInput.getValue()
                .titled(CreateLang.translateDirect("generic.daytime.minute"))
                .calling(t -> {
                    data.putInt("Minute", t);
                    timeLabel.getValue().text = getDigitalDisplay(hourInput.getValue()
                            .getState(), t, true);
                })
                .writingTo(null)
                .withShiftStep(15);

        minuteInput.getValue().lockedTooltipX = hourInput.getValue().lockedTooltipX = -15;
        minuteInput.getValue().lockedTooltipY = hourInput.getValue().lockedTooltipY = 35;

        hourInput.getValue()
                .setState(intData("Hour"));
        minuteInput.getValue()
                .setState(intData("Minute"))
                .onChanged();

        builder.customArea(0, 52);
    }}
