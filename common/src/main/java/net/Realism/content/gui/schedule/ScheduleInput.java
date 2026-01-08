package net.Realism.content.gui.schedule;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.world.item.Items.AIR;
import static net.minecraft.world.item.Items.STRUCTURE_VOID;

public class ScheduleInput extends DLGuiComponent {
    public Pair<ItemStack, Component> pair;
    int minSize;
    Boolean clean = false;
    ScheduleEntry scheduleEntry;
    ScheduleWaitCondition condition;
    SimulatedScheduleWindow parent;

    /**
     * Construct a new component with the given local position and size.
     *
     * @param x initial x position (local coordinates)
     * @param y initial y position (local coordinates)
     * @param w initial width
     * @param h initial height
     */
    public ScheduleInput(int x, int y, int w, int h, int minSize, Pair<ItemStack, Component> pair, Boolean clean, ScheduleEntry entry,SimulatedScheduleWindow parent) {
        super(x, y, w, h);
        this.pair = pair;
        this.minSize = minSize;
        this.clean = clean;
        this.scheduleEntry = entry;
        this.parent = parent;
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        ItemStack stack = pair.getFirst();
        Component text = pair.getSecond();
        boolean hasItem = !stack.isEmpty();
        int fieldSize = Math.min(getFieldSize(minSize, pair, graphics), 150);

        AllGuiTextures left = AllGuiTextures.SCHEDULE_CONDITION_LEFT;
        AllGuiTextures middle = AllGuiTextures.SCHEDULE_CONDITION_MIDDLE;
        AllGuiTextures item = AllGuiTextures.SCHEDULE_CONDITION_ITEM;
        AllGuiTextures right = AllGuiTextures.SCHEDULE_CONDITION_RIGHT;

        UIRenderHelper.drawStretched(graphics.graphics(), 3, 0, fieldSize, 16, 0, middle);
        if (!(clean)) {
            left.render(graphics.graphics(), 0, 0);
        }
        right.render(graphics.graphics(), fieldSize - 2, 0);
        if (hasItem) {
            item.render(graphics.graphics(), 6, 0);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            if (stack.getItem() ==  STRUCTURE_VOID){
                stack = new ItemStack(AIR);
            }
            graphics.graphics().renderItem(stack, 6, 0);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);

        }


        if (text != null)
            graphics.graphics().drawString(graphics.defaultFont(), text, hasItem ? 24 : 7, 4, 0xFFFFFF);

    }

    private int getFieldSize(int minSize, Pair<ItemStack, Component> pair, DLGuiGraphics graphics) {
        ItemStack stack = pair.getFirst();
        Component text = pair.getSecond();
        boolean hasItem = !stack.isEmpty();
        return Math.max((text == null ? 0 : graphics.defaultFont().width(text)) + (hasItem ? 20 : 0) + 16, minSize);
    }

    @Override
    public void mouseClickDispatcher(double mouseX, double mouseY, int button) {
        getWindowManager().createWindow((manager ->
            new EditWindow(manager, scheduleEntry,condition,null,this,parent)
        ));
    }
}
