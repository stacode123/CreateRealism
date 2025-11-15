package net.Realism.gui;


import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLScreenWrapper;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.WindowBuilder;
import net.Realism.Interfaces.IRealismScreens;
import net.Realism.Interfaces.ITrainInterface;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RealismScreens extends Screen implements IRealismScreens {
    private DLScreenWrapper wrapper;

    public RealismScreens() {
        super(Component.literal("My Screen"));
    }

    @Override
    public void init(ITrainInterface Rtrain) {
        // Create a WindowBuilder that constructs your main window
        WindowBuilder<TrainSettingsGui> builder = (manager) -> new TrainSettingsGui(manager, Rtrain);

        // DLScreenWrapper creates and manages the DLWindowManager internally
        wrapper = new DLScreenWrapper(
                null,
                builder
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        wrapper.render(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return wrapper.mouseClicked(mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return wrapper.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Access the manager directly if needed:
    public DLWindowManager getManager() {
        return wrapper.getWindowManager();
    }
}