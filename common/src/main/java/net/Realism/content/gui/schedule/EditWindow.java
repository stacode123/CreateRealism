package net.Realism.content.gui.schedule;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.*;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.TextStyle;
import de.mrjulsen.mcdragonlib.client.render.DefaultGuiTextures;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.createmod.catnip.data.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.function.Supplier;

public class EditWindow extends DLWindow {
    public ScheduleWaitCondition condition;
    public ScheduleEntry entry;
    private ScheduleWaitCondition originalCondition;
    private ScheduleEntry originalEntry;
    boolean editingEntry = false;
    DLPanel configWidgets;
    Pair<Integer, Integer> loc;
    SimulatedScheduleWindow parent;

    public EditWindow(DLWindowManager manager, ScheduleEntry scheduleEntry, ScheduleWaitCondition condition, Pair<Integer, Integer> loc, ScheduleInput sourceInput, SimulatedScheduleWindow parent) {
        super(manager);
        this.parent = parent;
        setSize(300, 100);
        movable.set(true);
        topLevel.set(true);
        setPosition(manager.getScreenWidth() / 2 - 150, manager.getScreenHeight() / 2 - 100);

        this.originalCondition = condition;
        this.originalEntry = scheduleEntry;
        this.loc = loc;

        if (condition == null) {
            editingEntry = true;
            this.entry = new ScheduleEntry();
            for (Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>> type : Schedule.INSTRUCTION_TYPES) {
                if (type.getFirst().equals(scheduleEntry.instruction.getId())) {
                    this.entry.instruction = type.getSecond().get();
                    this.entry.instruction.setData(scheduleEntry.instruction.getData());
                    break;
                }
            }
        } else {
            for (Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>> type : Schedule.CONDITION_TYPES) {
                if (type.getFirst().equals(condition.getId())) {
                    this.condition = type.getSecond().get();
                    this.condition.setData(condition.getData());
                    break;
                }
            }
            this.entry = scheduleEntry;
        }

        DLRichTextLabel label = new DLRichTextLabel(width()/2-25, 5, 50, 20);
        if (editingEntry) {
            label.text.get().set("Entry", new TextStyle.Builder().color(DLColor.BLACK.getAsInt()).build());
            configWidgets = DragonGuiConverter.fromInstruction(entry.instruction, entry.instruction.getData());
        } else {
            label.text.get().set("Condition", new TextStyle.Builder().color(DLColor.BLACK.getAsInt()).build());
            configWidgets = DragonGuiConverter.fromCondition(this.condition, this.condition.getData());
        }
        configWidgets.setPosition(10, 40);
        this.addComponent(label);
        this.addComponent(configWidgets);

        DLComboBox comboBox = new DLComboBox(50, 20, 200, 20);
        if (editingEntry) {
            for (Component c : Schedule.getTypeOptions(Schedule.INSTRUCTION_TYPES)) {
                comboBox.items.add(c.getString());
            }
            for (int i = 0; i < Schedule.INSTRUCTION_TYPES.size(); i++)
                if (Schedule.INSTRUCTION_TYPES.get(i)
                        .getFirst()
                        .equals(entry.instruction.getId()))
                    comboBox.selectedIndex.set(i);
        } else {
            for (Component c : Schedule.getTypeOptions(Schedule.CONDITION_TYPES)) {
                comboBox.items.add(c.getString());
            }
            for (int i = 0; i < Schedule.CONDITION_TYPES.size(); i++)
                if (Schedule.CONDITION_TYPES.get(i)
                        .getFirst()
                        .equals(this.condition.getId()))
                    comboBox.selectedIndex.set(i);
        }
        comboBox.addEventListener(DLCycleButton.SelectedItemChanged.class, ((source, event) -> {
            int index = (int) comboBox.selectedIndex.get();
            if (editingEntry) {
                Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>> dataEntry = Schedule.INSTRUCTION_TYPES.get(index);
                this.entry.instruction = dataEntry.getSecond().get();
                DLPanel newConfig = DragonGuiConverter.fromInstruction(entry.instruction, entry.instruction.getData());
                newConfig.setPosition(10, 40);
                this.removeComponent(configWidgets);
                configWidgets = newConfig;
                this.addComponent(configWidgets);
            } else {
                Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>> dataEntry = Schedule.CONDITION_TYPES.get(index);
                this.condition = dataEntry.getSecond().get();
                DLPanel newConfig = DragonGuiConverter.fromCondition(this.condition, this.condition.getData());
                newConfig.setPosition(10, 40);
                this.removeComponent(configWidgets);
                configWidgets = newConfig;
                this.addComponent(configWidgets);
            }
            return false;
        }));
        this.addComponent(comboBox);

        this.addEventListener(DLGuiStandardEvents.CloseEvent.class, (src, event) -> {
            return false;
        });


        DLButton confirmButton = new DLButton((width() / 2) - 25, height() - 20, 50, 15);
        confirmButton.text.set(Component.literal("Confirm"));
        confirmButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (src, event) -> {
            if (editingEntry) {
                originalEntry.instruction = this.entry.instruction;
            } else {
                originalEntry.conditions.get(loc.getFirst()).set(loc.getSecond(), this.condition);
            }


            parent.save();
            parent.resetCards();


            getWindowManager().closeWindow(this);
            return false;
        });
        this.addComponent(confirmButton);

        DLButton deleteButton = new DLButton((width()) - 25, height() - 25, 20, 20);
        deleteButton.text.set(Component.literal("\uD83D\uDDD1"));
        deleteButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (src, event) -> {
            if (editingEntry) {
                originalEntry.instruction = null;
                Arrays.stream(getWindowManager().getWindows(getAssignedModal().get())).forEach(window -> {
                    if (window instanceof SimulatedScheduleWindow) {
                        ((SimulatedScheduleWindow) window).save();
                        ((SimulatedScheduleWindow) window).resetCards();
                    }
                });
                getWindowManager().closeWindow(this);
                return false;
            }
            originalEntry.conditions.get(loc.getFirst()).remove(originalCondition);
            originalEntry.conditions.removeIf(list -> list.isEmpty());
            parent.save();
            parent.resetCards();
            getWindowManager().closeWindow(this);
            return false;
        });
        this.addComponent(deleteButton);

    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds){
        DefaultGuiTextures.DRAGONLIB_UI.getSprite("window_rounded")
                .render(graphics, 0, 0, width(), height());
    }

    private void copyContent(ScheduleWaitCondition original, ScheduleWaitCondition copy){
    }


}
