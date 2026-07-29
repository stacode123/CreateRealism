package net.Realism.mixin.mixinaccesors;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ScheduleScreen.class, remap = false)
public interface ScheduleScreenAccessor {
    @Accessor("schedule")
    Schedule realism$getSchedule();
}
