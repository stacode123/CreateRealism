package net.Realism.forge.config;

import net.Realism.config.RealismConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class RealismConfigScreen extends Screen {
    private static final int ROW_W = 220;
    private static final int ROW_H = 20;

    private final Screen parent;

    private boolean etcsEnable;
    private boolean etcsSounds;
    private boolean etcsMph;
    private double etcsSize;
    private RealismConfig.ETCSAlignment alignment;
    private EditBox xOffsetBox;
    private EditBox yOffsetBox;

    public RealismConfigScreen(Screen parent) {
        super(Component.literal("Create: Realism - ETCS Settings"));
        this.parent = parent;
        this.etcsEnable = RealismConfig.CLIENT.ETCSEnable.get();
        this.etcsSounds = RealismConfig.CLIENT.ETCSSounds.get();
        this.etcsMph = RealismConfig.CLIENT.ETCSMPH.get();
        this.etcsSize = RealismConfig.CLIENT.ETCSSize.get();
        this.alignment = RealismConfig.CLIENT.ETCSAlignment.get();
    }

    @Override
    protected void init() {
        int x = this.width / 2 - ROW_W / 2;
        int y = this.height / 6;

        addRenderableWidget(CycleButton.onOffBuilder(etcsEnable)
                .create(x, y, ROW_W, ROW_H, Component.literal("Show ETCS display"), (b, v) -> etcsEnable = v));
        y += 24;
        addRenderableWidget(CycleButton.onOffBuilder(etcsSounds)
                .create(x, y, ROW_W, ROW_H, Component.literal("ETCS sounds"), (b, v) -> etcsSounds = v));
        y += 24;
        addRenderableWidget(CycleButton.onOffBuilder(etcsMph)
                .create(x, y, ROW_W, ROW_H, Component.literal("Use mph"), (b, v) -> etcsMph = v));
        y += 24;
        addRenderableWidget(CycleButton.<RealismConfig.ETCSAlignment>builder(a -> Component.literal(a.name()))
                .withValues(RealismConfig.ETCSAlignment.values())
                .withInitialValue(alignment)
                .create(x, y, ROW_W, ROW_H, Component.literal("Position"), (b, v) -> alignment = v));
        y += 24;

        addRenderableWidget(new AbstractSliderButton(x, y, ROW_W, ROW_H,
                Component.literal("Size: " + String.format("%.2f", etcsSize)), (etcsSize - 0.1) / 1.9) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Size: " + String.format("%.2f", etcsSize)));
            }

            @Override
            protected void applyValue() {
                etcsSize = Math.round((0.1 + this.value * 1.9) * 100.0) / 100.0;
            }
        });
        y += 28;

        xOffsetBox = numberBox(x + 110, y, RealismConfig.CLIENT.ETCSXOffset.get());
        yOffsetBox = numberBox(x + 110, y + 24, RealismConfig.CLIENT.ETCSYOffset.get());
        addRenderableWidget(xOffsetBox);
        addRenderableWidget(yOffsetBox);

        int by = this.height - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(this.width / 2 - 102, by, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 + 2, by, 100, 20).build());
    }

    private EditBox numberBox(int x, int y, int value) {
        EditBox box = new EditBox(this.font, x, y, 110, ROW_H, Component.empty());
        box.setMaxLength(6);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,5}"));
        box.setValue(String.valueOf(value));
        return box;
    }

    private static int parse(EditBox box, int fallback) {
        try {
            return Math.max(-5000, Math.min(5000, Integer.parseInt(box.getValue())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void saveAndClose() {
        RealismConfig.CLIENT.ETCSEnable.set(etcsEnable);
        RealismConfig.CLIENT.ETCSSounds.set(etcsSounds);
        RealismConfig.CLIENT.ETCSMPH.set(etcsMph);
        RealismConfig.CLIENT.ETCSSize.set(Math.max(0.1, Math.min(2.0, etcsSize)));
        RealismConfig.CLIENT.ETCSAlignment.set(alignment);
        RealismConfig.CLIENT.ETCSXOffset.set(parse(xOffsetBox, 0));
        RealismConfig.CLIENT.ETCSYOffset.set(parse(yOffsetBox, 0));
        RealismConfig.CLIENT_SPEC.save();
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        int x = this.width / 2 - ROW_W / 2;
        int y = this.height / 6 + 24 * 4 + 28;
        g.drawString(this.font, "X offset (px):", x, y + 6, 0xFFFFFF);
        g.drawString(this.font, "Y offset (px):", x, y + 30, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
