package net.Realism.foundation.util;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class AllRealismIcons implements ScreenElement {
    public static final ResourceLocation icons = new ResourceLocation("realism:textures/gui/settings.png");
    private static int x = 0, y = -1;
    private int iconX;
    private int iconY;

    public static final AllRealismIcons
        SETTINGS_ICON = new AllRealismIcons(0,0);


    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(icons, x, y, 0, iconX, iconY, 16, 16, 16, 16);
    }

    public static AllRealismIcons next() {
        x += 16;
        y += 16;
        return new AllRealismIcons(x, y);
    }

    public AllRealismIcons(int x, int y) {
        iconX = x * 16;
        iconY = y * 16;
    }
}
