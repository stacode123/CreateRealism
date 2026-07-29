package net.Realism.content.trains.schedule;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import net.Realism.RNetworking;
import net.Realism.foundation.network.AdvancedScheduleSavePacket;
import net.Realism.mixin.mixinaccesors.ScheduleScreenAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Create's schedule editor, saving through our own packet. Subclassing the
 * real {@link ScheduleScreen} keeps every Create feature and lets Railways
 * Navigator's {@code instanceof ScheduleScreen}-gated mixins apply here too.
 */
public class AdvancedScheduleScreen extends ScheduleScreen {

    public AdvancedScheduleScreen(ScheduleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    public void removed() {
        // The vanilla ScheduleEditPacket sent by super is discarded server-side:
        // its handler requires Create's own schedule item in the main hand.
        super.removed();
        Schedule schedule = ((ScheduleScreenAccessor) this).realism$getSchedule();
        if (schedule != null)
            RNetworking.sendToServer(new AdvancedScheduleSavePacket(schedule));
    }
}
