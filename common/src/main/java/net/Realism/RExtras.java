package net.Realism;

import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import net.Realism.trains.schedule.TimeOfDayRealistic;
import net.createmod.catnip.data.Pair;

import java.util.function.Supplier;

import static com.simibubi.create.content.trains.schedule.Schedule.CONDITION_TYPES;
import static com.simibubi.create.content.trains.schedule.Schedule.INSTRUCTION_TYPES;

public class RExtras {
    public static class Schedule {
        private static void registerInstruction(String path, Supplier<? extends ScheduleInstruction> factory) {
            INSTRUCTION_TYPES.add(Pair.of(RealismMod.id(path), factory));
        }
        private static void registerCondition(String path, Supplier<? extends ScheduleWaitCondition> clazz) {
            CONDITION_TYPES.add(Pair.of(RealismMod.id(path), clazz));
        }

        public static void register() {
            registerCondition("time_of_day_realistic", TimeOfDayRealistic::new);
        }
    }
}
