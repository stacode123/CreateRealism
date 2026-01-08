package net.Realism.content.gui.schedule;

import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding;
import net.minecraft.nbt.CompoundTag;

public class DragonGuiConverter {

    /**
     * Converts a Create ScheduleInstruction's configuration widgets into a DragonLib DLGuiComponent.
     * 
     * @param instruction The instruction to convert.
     * @param data The NBT data to bind the widgets to.
     * @return A new DLGuiComponent (a DLPanel) containing the equivalent DragonLib widgets.
     */
    public static DLPanel fromInstruction(ScheduleInstruction instruction, CompoundTag data) {
        return fromInput(instruction, data);
    }

    /**
     * Converts a Create ScheduleWaitCondition's configuration widgets into a DragonLib DLGuiComponent.
     *
     * @param condition The condition to convert.
     * @param data The NBT data to bind the widgets to.
     * @return A new DLGuiComponent (a DLPanel) containing the equivalent DragonLib widgets.
     */
    public static DLPanel fromCondition(ScheduleWaitCondition condition, CompoundTag data) {
        return fromInput(condition, data);
    }

    private static DLPanel fromInput(IScheduleInput input, CompoundTag data) {
        // Create a panel to hold the widgets
        DLPanel panel = new DLPanel(0, 0, 280, 25);
        
        // Use a FlowLayout for horizontal arrangement, similar to a ModularGuiLine

        FlowLayout layout = new FlowLayout();
        layout.flowDirection.set(FlowLayout.Direction.HORIZONTAL);
        layout.padding.set(new Padding(2, 2, 2, 2));
        layout.horizontalGap.set(1);
        panel.layout.set(layout);
        
        // Create the builder and let the input populate the panel
        ModularDragonGuiBuilder builder = new ModularDragonGuiBuilder(panel, data);
        input.initConfigurationWidgets(builder);

        // Add ghost slots if targeted
        int slots = input.slotsTargeted();
        if (slots > 0) {
            for (int i = 0; i < slots; i++) {
                panel.addComponent(new DLGhostSlot(0, 0, input, i));
            }
        }
        
        return panel;
    }
}
