package net.Realism.trains.schedule;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.condition.TimeOfDayCondition;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import net.Realism.Interfaces.IScheduleRuntimeMixin;
import net.Realism.RealismMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.List;

/**
 * Timetable departure. Each stop books an absolute departure tick - its slot - and the train
 * leaves the moment it is both ready and past that slot. A train that pulls in behind schedule
 * finds its slot already in the past and leaves at once, exactly like a delayed service, and it
 * keeps leaving at once at every following stop until the timetable catches up with it again.
 * <p>
 * What keeps that from turning into a train that never waits at all is that slots are strictly
 * increasing: the next one is always the first matching time of day <em>after</em> the slot just
 * used. So a loop calling at the same station twice books tomorrow rather than departing on the
 * spot, while a genuinely late train still gets to run.
 * <p>
 * Unlike Create's own {@link TimeOfDayCondition}, which departs only if it happens to tick inside
 * a 40 tick window around the target, a booked slot can never be missed.
 */
public class TimeOfDayRealistic extends TimeOfDayCondition {

    /** Length of a Minecraft day in ticks. */
    private static final long DAY = 24000L;
    /** Day time 0 is 06:00, so this shifts it to "ticks since midnight". */
    private static final long MIDNIGHT_OFFSET = 6000L;
    /**
     * How far behind the timetable a train may be and still be treated as merely late. Past this
     * it is re-timetabled onto the next slot after its arrival, so a train that was derailed or
     * parked for weeks does not sprint through whole cycles of backlog to catch up.
     */
    private static final long MAX_RECOVERY = 7L * DAY;
    /** Absolute game time this train may depart at, kept in the per-condition context tag. */
    private static final String DEPART_AT = "RealismDepartAt";

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
        long now = level.getGameTime();
        long slot = bookedSlot(level, train, context, now);
        if (now < slot)
            return false;
        if (train.runtime instanceof IScheduleRuntimeMixin runtime)
            runtime.setLastDepartureSlot(slot);
        return true;
    }

    /**
     * Absolute game time of this stop's departure. Resolved once per stop and cached in the
     * context tag, which Create clears whenever the train arrives somewhere or this condition
     * is satisfied - so every stop books its own slot.
     */
    private long bookedSlot(Level level, Train train, CompoundTag context, long now) {
        if (context.contains(DEPART_AT, Tag.TAG_LONG)) {
            long stored = context.getLong(DEPART_AT);
            // A slot is never more than a day ahead; anything else is a leftover from an edited
            // schedule or a world time change, so book a new one.
            if (stored - now <= DAY)
                return stored;
        }

        long slot = bookSlot(level, train, now);
        context.putLong(DEPART_AT, slot);
        requestStatusToUpdate(context);
        return slot;
    }

    private long bookSlot(Level level, Train train, long now) {
        long arrival = arrival(train, now);
        if (train.runtime instanceof IScheduleRuntimeMixin runtime) {
            long previous = runtime.getLastDepartureSlot();
            // Chain onto the previous slot rather than onto the arrival, so that being late only
            // moves the train, never the timetable.
            if (previous > 0 && previous <= arrival && arrival - previous <= MAX_RECOVERY)
                return occurrenceFrom(level, previous, now, false);
        }
        return occurrenceFrom(level, arrival, now, true);
    }

    /** The moment the train started waiting here, falling back to now if that is unknown. */
    private long arrival(Train train, long now) {
        if (!(train.runtime instanceof IScheduleRuntimeMixin runtime))
            return now;
        long arrival = runtime.getArrivalGameTime();
        if (arrival <= 0 || arrival > now || now - arrival > MAX_RECOVERY)
            return now;
        return arrival;
    }

    /**
     * First game time matching the configured time of day, counted from {@code from} - inclusive
     * of {@code from} itself, or a full day later when {@code inclusive} is false.
     */
    private long occurrenceFrom(Level level, long from, long now, boolean inclusive) {
        // Day time and game time advance in lockstep, so rewinding by the elapsed ticks gives the
        // time of day back at that moment.
        long timeOfDay = Math.floorMod(level.getDayTime() - (now - from) + MIDNIGHT_OFFSET, DAY);
        long untilTarget = Math.floorMod(targetTimeOfDay() - timeOfDay, DAY);
        if (untilTarget == 0 && !inclusive)
            untilTarget = DAY;
        return from + untilTarget;
    }

    /** Configured departure as ticks since midnight, always within a single day. */
    private long targetTimeOfDay() {
        long hour = Mth.clamp(intData("Hour"), 0, 23);
        long minute = Mth.clamp(intData("Minute"), 0, 59);
        return hour * 1000L + Math.round(minute * 1000d / 60d);
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
        return CreateLang.translateDirect("schedule.condition.time_of_day.status")
                .append(getDigitalDisplay(intData("Hour"), intData("Minute"), false));
    }

    @Override
    public List<Component> getTitleAs(String type) {
        ResourceLocation id = getId();
        return ImmutableList.of(
                Component.translatable(id.getNamespace() + ".schedule." + type + "." + id.getPath()),
                getDigitalDisplay(intData("Hour"), intData("Minute"), false).withStyle(ChatFormatting.DARK_AQUA));
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
                .setState(Mth.clamp(intData("Hour"), 0, 23));
        minuteInput.getValue()
                .setState(Mth.clamp(intData("Minute"), 0, 59))
                .onChanged();

        builder.customArea(0, 52);
    }
}
