package net.Realism.content.gui.schedule;

import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;

import java.util.ArrayList;
import java.util.List;

public class ConditionList extends DLGuiComponent {

    /**
     * Construct a new component with the given local position and size.
     *
     * @param x initial x position (local coordinates)
     * @param y initial y position (local coordinates)
     * @param w initial width
     * @param h initial height
     */
    SimulatedScheduleWindow parent;
    public ConditionList(int x, int y, int w, int h, SimulatedScheduleWindow parent) {
        super(x, y, w, h);
        this.parent = parent;
       // TableLayout layout = new TableLayout();
        //this.layout.set(layout);
    }

    public void AddCondition(ScheduleEntry entry, List<List<ScheduleWaitCondition>> conditions){
        int row = 0;
        int coloumn = 0;
        //TableLayout layout = (TableLayout) this.layout.get();
        for (List<ScheduleWaitCondition> condition : conditions) {
            //layout.addColumn(String.valueOf(coloumn),70, TableLayout.ColumnSizeMode.FIXED);
            for (ScheduleWaitCondition scheduleWaitCondition : condition) {
                ScheduleConditionInput conditionInput = new ScheduleConditionInput(coloumn*70, row*18, 70, 16, 50, scheduleWaitCondition.getSummary(), true,row,coloumn, scheduleWaitCondition,entry,parent);
                conditionInput.layoutContraint.set(String.valueOf(coloumn));
                this.addComponent(conditionInput);
                row++;
            }
            DLButton addConditionButton = new DLButton(coloumn*70+30, row*18, 10, 10){
                @Override
                public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
                    AllGuiTextures.SCHEDULE_CONDITION_APPEND.render(graphics.graphics(),0,0);
                }
            };
            int finalColoumn = coloumn;
            addConditionButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (src, event) -> {
                conditions.get(finalColoumn).add(new ScheduledDelay());
                parent.save();
                parent.resetCards();
                return false;

            });
            this.addComponent(addConditionButton);
            coloumn++;
            row=0;
        }
        DLButton addConditionButton2 = new DLButton(coloumn*70, row*18+9, 19, 16){
            @Override
            public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
                AllGuiTextures.SCHEDULE_CONDITION_NEW.render(graphics.graphics(),0,0);
            }
        };
        addConditionButton2.addEventListener(DLGuiStandardEvents.ClickEvent.class, (src,event) -> {
            conditions.add(new ArrayList<>());
            conditions.get(conditions.size()-1).add(new ScheduledDelay());
            parent.save();
            parent.resetCards();
            return false;

        });
        this.addComponent(addConditionButton2);
    }
    public void updateCondition(int coloumn,int row, ScheduleWaitCondition condition) {
        for (DLGuiComponent component : this.getComponents()) {
            if (component instanceof ScheduleConditionInput) {
                ScheduleConditionInput conditionInput = (ScheduleConditionInput) component;
                if (conditionInput.coloumn == coloumn && conditionInput.row == row) {
                    conditionInput.condition = condition;
                    conditionInput.pair = (condition.getSummary());
                }
            }
        }
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        super.renderMainLayer(graphics, mouseX, mouseY, renderBounds);
    }





}
