package net.Realism.mixin.mixinaccesors;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ScheduleScreen.class, remap = false)
public interface ScheduleScreenAccessor {
    @Accessor("schedule")
    Schedule realism$getSchedule();

    @Accessor("scroll")
    LerpedFloat realism$getScroll();

    @Accessor("editingCondition")
    ScheduleWaitCondition realism$getEditingCondition();

    @Accessor("editingDestination")
    ScheduleInstruction realism$getEditingDestination();
}
