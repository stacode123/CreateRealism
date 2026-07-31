package net.Realism.content.trains.schedule;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import net.Realism.RNetworking;
import net.Realism.content.gui.SimulationResultsWindow;
import net.Realism.content.gui.SimulationSetupWindow;
import net.Realism.content.simulator.SimulationPayload;
import net.Realism.foundation.network.AdvancedScheduleSavePacket;
import net.Realism.mixin.mixinaccesors.ScheduleScreenAccessor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Create's schedule editor, saving through our own packet. Subclassing the
 * real {@link ScheduleScreen} keeps every Create feature and lets Railways
 * Navigator's {@code instanceof ScheduleScreen}-gated mixins apply here too.
 * Adds the Simulate button opening the phantom setup window (SPEC §4.1).
 */
public class AdvancedScheduleScreen extends ScheduleScreen {

    private Button simulateButton;
    private boolean simulating;

    public AdvancedScheduleScreen(ScheduleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        // Bottom bar, next to Create's "Skip current stop" (icons at +45/+63).
        simulateButton = Button.builder(simulateLabel(),
                        button -> DLWindow.openWindow(manager -> new SimulationSetupWindow(manager, this)))
                .bounds(leftPos + 85, topPos + 196, 60, 18)
                .build();
        simulateButton.active = !simulating;
        addRenderableWidget(simulateButton);
    }

    private Component simulateLabel() {
        return Component.translatable(simulating
                ? "realism.gui.simulate.running"
                : "realism.gui.simulate.button");
    }

    public void simulationRequested() {
        simulating = true;
        if (simulateButton != null) {
            simulateButton.setMessage(simulateLabel());
            simulateButton.active = false;
        }
    }

    public void simulationArrived(SimulationPayload payload) {
        simulating = false;
        if (simulateButton != null) {
            simulateButton.setMessage(simulateLabel());
            simulateButton.active = true;
        }
        DLWindow.openWindow(manager -> new SimulationResultsWindow(manager, payload));
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
