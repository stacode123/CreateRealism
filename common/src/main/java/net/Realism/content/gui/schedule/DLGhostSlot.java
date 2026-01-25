package net.Realism.content.gui.schedule;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class DLGhostSlot extends DLGuiComponent {
    private final IScheduleInput input;
    private final int slotIndex;

    public DLGhostSlot(int x, int y, IScheduleInput input, int slotIndex) {
        super(x, y, 18, 18);
        this.input = input;
        this.slotIndex = slotIndex;
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        AllGuiTextures.SCHEDULE_CONDITION_ITEM.render(graphics.graphics(), 0, 0);
        
        ItemStack stack = input.getItem(slotIndex);
        if (!stack.isEmpty()) {
            RenderSystem.enableDepthTest();
            graphics.graphics().renderItem(stack, 1, 1);
            graphics.graphics().renderItemDecorations(graphics.defaultFont(), stack, 1, 1);
        }
    }

    @Override
    public void renderFrontLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        if (isMouseOver(mouseX, mouseY)) {
            List<Component> tooltip = input.getSecondLineTooltip(slotIndex);
            if (tooltip != null && !tooltip.isEmpty()) {
                GuiUtils.drawTooltip(graphics, graphics.defaultFont(), (int) mouseX, (int) mouseY, 
                    tooltip.stream().map(c -> TextUtils.text(c.getString())).toList(), 
                    (int) getWindowManager().getScreenWidth());
            } else {
                ItemStack stack = input.getItem(slotIndex);
                if (!stack.isEmpty()) {
                    graphics.graphics().renderTooltip(graphics.defaultFont(), stack, (int) mouseX, (int) mouseY);
                }
            }
        }
    }

    @Override
    public void mouseClickDispatcher(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            ItemStack held = Minecraft.getInstance().player.containerMenu.getCarried();
            input.setItem(slotIndex, held.copy());
            // Most Create conditions serialize their item into their data NBT in setItem
            // but we might need to trigger a UI refresh if DragonLib doesn't do it automatically
        }
    }
}
