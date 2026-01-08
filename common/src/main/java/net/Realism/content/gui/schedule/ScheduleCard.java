package net.Realism.content.gui.schedule;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLScrollBar;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.Realism.content.trains.schedule.TimeOfDayRealistic;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ScheduleCard extends DLGuiComponent {
    /**
     * Construct a new Card with the given local position and size.
     *
     * @param x initial x position (local coordinates)
     * @param y initial y position (local coordinates)
     * @param w initial width
     * @param h initial height
     */
    SimulatedScheduleWindow parent;
    public ScheduleEntry scheduleEntry;
    Schedule schedule;
    ScheduleInput input;
    int cardWidth = width()-16;
    public ScheduleCard(int x, int y, int w, int h, ScheduleEntry scheduleEntry, Schedule schedule, SimulatedScheduleWindow parent) {
        super(x, y, w, h);
        this.scheduleEntry = scheduleEntry;
        this.schedule = schedule;
        input = new ScheduleInput(x, y, 100, 16, 100, scheduleEntry.instruction.getSummary(),false,scheduleEntry,parent);
        if (scheduleEntry.instruction.supportsConditions()) {

            ConditionList conditionList = new ConditionList(x + 14, y + 24, cardWidth - 20, h-39,parent);
            conditionList.AddCondition(scheduleEntry, scheduleEntry.conditions);
            this.addComponent(conditionList);

            int maxRows = 0;
            for (List<ScheduleWaitCondition> list : scheduleEntry.conditions)
                maxRows = Math.max(maxRows, list.size());

            DLScrollBar verticalBar = new DLScrollBar(cardWidth-5, y + 24, 3, DLScrollBar.Orientation.VERTICAL);
            DLScrollBar horizontalBar = new DLScrollBar(x + 13, y + h - 17, 3, DLScrollBar.Orientation.HORIZONTAL);
            verticalBar.setSize(3, h - 48);
            horizontalBar.setSize(cardWidth - 15, 3);
            horizontalBar.max.set((scheduleEntry.conditions.size() * 70) + 20);
            horizontalBar.screenSize.set(conditionList.width());
            verticalBar.screenSize.set(conditionList.height());
            verticalBar.max.set((maxRows * 18) + 20);

            horizontalBar.addEventListener(DLScrollBar.ValueChangedEvent.class, (src, event) -> {
                // Update the container's scroll offset whenever the scrollbar moves
                conditionList.setScrollOffsetX(event.value());
                return false;
            });

            verticalBar.addEventListener(DLScrollBar.ValueChangedEvent.class, (src, event) -> {
                // Update the container's scroll offset whenever the scrollbar moves
                conditionList.setScrollOffsetY(event.value());
                return false;
            });

            this.addComponent(verticalBar);
            this.addComponent(horizontalBar);
        }

        DLButton removeButton = new DLButton(cardWidth-14,2){
            @Override
            public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
                //super.renderMainLayer(graphics, mouseX, mouseY, renderBounds);
                AllGuiTextures.SCHEDULE_CARD_REMOVE.render(graphics.graphics(), 0, 0);
            }
        };
        DLButton duplicateButton = new DLButton(cardWidth-14,height()-14){
            @Override
            public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
                //super.renderMainLayer(graphics, mouseX, mouseY, renderBounds);
                AllGuiTextures.SCHEDULE_CARD_DUPLICATE.render(graphics.graphics(), 0, 0);
            }
        };

        removeButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (src,event) -> {
            schedule.entries.remove(scheduleEntry);
            parent.save();
            parent.resetCards();
            return false;
        });

        duplicateButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (src,event) -> {
            ScheduleEntry entry2 = scheduleEntry.clone();
            int entryIndex = schedule.entries.indexOf(scheduleEntry);
            schedule.entries.add(entryIndex+1,entry2);
            parent.save();
            parent.resetCards();
            return false;
        });

        this.addComponent(removeButton);
        this.addComponent(duplicateButton);
        this.addComponent(input);

    }



    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        renderScheduleEntry(graphics, mouseX, mouseY, renderBounds);

    }
    private void renderScheduleEntry(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        AllGuiTextures light = AllGuiTextures.SCHEDULE_CARD_LIGHT;
        AllGuiTextures medium = AllGuiTextures.SCHEDULE_CARD_MEDIUM;
        AllGuiTextures dark = AllGuiTextures.SCHEDULE_CARD_DARK;

        ScheduleEntry entry = scheduleEntry;
        int xOffset = 0;
        int yOffset = 0;
        int cardWidth = width()-16;
        int cardHeader = 22;
        int maxRows = 0;
        for (List<ScheduleWaitCondition> list : entry.conditions)
            maxRows = Math.max(maxRows, list.size());
        boolean supportsConditions = entry.instruction.supportsConditions();
        int cardHeight = height();

        int i  = schedule.entries.indexOf(entry);

        //DefaultGuiTextures.DRAGONLIB_UI.getSprite("window_rectangular").render(graphics, 0, 0, width(), height());
        UIRenderHelper.drawStretched(graphics.graphics(), 0+xOffset, 1+yOffset, cardWidth, cardHeight - 2, 0, light);
        UIRenderHelper.drawStretched(graphics.graphics(), 1+xOffset, 0+yOffset, cardWidth - 2, cardHeight - 2, 0, light);
        UIRenderHelper.drawStretched(graphics.graphics(), 1+xOffset, 1+yOffset, cardWidth - 2, cardHeight - 2, 0, dark);
        UIRenderHelper.drawStretched(graphics.graphics(), 2+xOffset, 2+yOffset, cardWidth - 4, cardHeight - 4, 0, medium);
        UIRenderHelper.drawStretched(graphics.graphics(), 2+xOffset, 2+yOffset, cardWidth - 4, cardHeader, 0,
                supportsConditions ? light : medium);


       // AllGuiTextures.SCHEDULE_CARD_REMOVE.render(graphics.graphics(), cardWidth - 14+xOffset, 2+yOffset);
        //AllGuiTextures.SCHEDULE_CARD_DUPLICATE.render(graphics.graphics(), cardWidth - 14+xOffset, cardHeight - 14+yOffset);

        if (i > 0)
            AllGuiTextures.SCHEDULE_CARD_MOVE_UP.render(graphics.graphics(), cardWidth+xOffset, (height()/2)-14);
        if (i < schedule.entries.size() - 1)
            AllGuiTextures.SCHEDULE_CARD_MOVE_DOWN.render(graphics.graphics(), cardWidth+xOffset, (height()/2));

        UIRenderHelper.drawStretched(graphics.graphics(), 5+xOffset, 0, 3, cardHeight + 25, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT);
        (supportsConditions ? AllGuiTextures.SCHEDULE_STRIP_TRAVEL : AllGuiTextures.SCHEDULE_STRIP_ACTION)
                .render(graphics.graphics(), 1+xOffset, 6+yOffset);

        if (supportsConditions)
            AllGuiTextures.SCHEDULE_STRIP_WAIT.render(graphics.graphics(), 1+xOffset, 28+yOffset);

        Pair<ItemStack, Component> destination = entry.instruction.getSummary();
        entry.instruction.renderSpecialIcon(graphics.graphics(), 30, 5);
        updateInput(xOffset+9, 5+yOffset,destination);
    }

    public void UpdateScheduleEntry(ScheduleEntry entry, Schedule schedule){
        this.scheduleEntry = entry;
        this.schedule = schedule;
    }

    public void updateInput(int x,int y,Pair<ItemStack, Component> pair1){
        input.setPosition(x, y);
        input.pair = pair1;


    }
    public static void addConditions(ScheduleEntry entry) {
        if (entry.instruction.supportsConditions()) {
            entry.conditions.add(new java.util.ArrayList<>());
            entry.conditions.add(new java.util.ArrayList<>());
            entry.conditions.add(new java.util.ArrayList<>());
            entry.conditions.get(0).add(new ScheduledDelay());
            entry.conditions.get(0).add(new TimeOfDayRealistic());
            entry.conditions.get(1).add(new TimeOfDayRealistic());
            entry.conditions.get(1).add(new ScheduledDelay());
            entry.conditions.get(2).add(new ScheduledDelay());
            entry.conditions.get(2).add(new TimeOfDayRealistic());
        }
    }



}
