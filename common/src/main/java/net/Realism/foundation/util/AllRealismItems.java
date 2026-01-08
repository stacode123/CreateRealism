package net.Realism.foundation.util;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.Realism.RealismMod;
import net.Realism.content.trains.schedule.AdvancedScheduleItem;

public class AllRealismItems {

    public static final ItemEntry<AdvancedScheduleItem> ADVANCED_SCHEDULE = RealismMod.REGISTRATE.item("advanced_schedule", AdvancedScheduleItem::new).register();

    public static void register() {
        RealismMod.LOGGER.debug("Registering Items for " + RealismMod.NAME);
    }
}
