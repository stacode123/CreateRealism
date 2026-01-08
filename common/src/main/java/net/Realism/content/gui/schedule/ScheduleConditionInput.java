package net.Realism.content.gui.schedule;

import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.createmod.catnip.data.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ScheduleConditionInput extends ScheduleInput {
    int coloumn;
    int row;

    /**
     * Construct a new component with the given local position and size.
     *
     * @param x       initial x position (local coordinates)
     * @param y       initial y position (local coordinates)
     * @param w       initial width
     * @param h       initial height
     * @param minSize
     * @param pair
     * @param clean
     *
     */
    public ScheduleConditionInput(int x, int y, int w, int h, int minSize, Pair<ItemStack, Component> pair, Boolean clean, int row, int coloumn, ScheduleWaitCondition condition, ScheduleEntry entry, SimulatedScheduleWindow parent) {
        super(x, y, w, h, minSize, pair, clean,entry,parent);
        this.row = row;
        this.coloumn = coloumn;
        this.condition = condition;
        this.parent = parent;
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {;
        int x = (coloumn - 1) * 150;
        int y = row * 22;
        Rectangle newBounds = Rectangle.offset(renderBounds, x, y);
        super.renderMainLayer(graphics, mouseX, mouseY, newBounds);
        condition.renderSpecialIcon(graphics.graphics(), 6, 0);
    }
    @Override
    public void mouseClickDispatcher(double mouseX, double mouseY, int button) {
        //super.mouseClickDispatcher(mouseX, mouseY, button);
        getWindowManager().createWindow(manager ->
                new EditWindow(manager, scheduleEntry,condition, Pair.of(coloumn,row),this,parent));
    }
}

