package net.Realism.content.gui;

import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLNumberPicker;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.INumberFormatAdapter;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.ITextFormatter;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.Realism.RNetworking;
import net.Realism.content.simulator.SimulationService;
import net.Realism.content.trains.schedule.AdvancedScheduleScreen;
import net.Realism.foundation.network.RequestSimulationPacket;
import net.minecraft.network.chat.Component;

/**
 * The phantom-train setup window (SPEC §4.4): acceleration mode + carriage
 * count, sim start time and horizon. One control row per setting (24px
 * rhythm, no reserved gaps) with mode-dependent fields appearing inline.
 * Values persist across openings within the session; Run sends the request —
 * the schedule itself is read server-side from the held item.
 */
public class SimulationSetupWindow extends DLWindow {

    private static final int LABEL_X = 15;
    private static final int CONTROL_X = 88;
    private static final int ROW_HEIGHT = 24;
    private static final int FIRST_ROW_Y = 30;

    private static int carriages = 3;
    private static int locomotives = 1;
    private static int accelerationMode = 1;
    private static double customAcceleration = 1.5;
    private static boolean startNow = true;
    private static int startHour = 8;
    private static int startMinute = 0;
    private static int horizonHours = 48;

    public SimulationSetupWindow(DLWindowManager manager, AdvancedScheduleScreen host) {
        super(manager);
        setSize(300, 212);
        var mcWindow = net.minecraft.client.Minecraft.getInstance().getWindow();
        setPosition(Math.max(0, (mcWindow.getGuiScaledWidth() - 300) / 2),
                Math.max(0, (mcWindow.getGuiScaledHeight() - 212) / 2));

        DLRichTextLabel title = addComponent(new DLRichTextLabel(95, 8, 200, 20));
        title.text.get().set(Component.translatable("realism.gui.sim.title").getString());

        int row = 0;

        label(row, "realism.gui.sim.carriages");
        DLNumberPicker carriagePicker = picker(row, CONTROL_X, 56);
        carriagePicker.min.set(1.0);
        carriagePicker.max.set(100.0);
        carriagePicker.value.set((double) carriages);
        row++;

        label(row, "realism.gui.sim.locomotives");
        DLNumberPicker locomotivePicker = picker(row, CONTROL_X, 56);
        locomotivePicker.min.set(1.0);
        locomotivePicker.max.set(100.0);
        locomotivePicker.value.set((double) locomotives);
        row++;

        label(row, "realism.gui.sim.accel");
        DLCycleButton<String> accelButton = cycle(row, 84);
        accelButton.items.add(Component.translatable("realism.gui.accel.setting.none").getString());
        accelButton.items.add(Component.translatable("realism.gui.accel.setting.standard").getString());
        accelButton.items.add(Component.translatable("realism.gui.accel.setting.custom").getString());
        accelButton.selectedIndex.set(accelerationMode);

        DLNumberPicker customPicker = picker(row, 180, 60);
        customPicker.min.set(0.1);
        customPicker.max.set(50.0);
        customPicker.step.set(0.1);
        customPicker.format.set(new INumberFormatAdapter.DecimalNumberFormat(1));
        customPicker.value.set(customAcceleration);
        customPicker.visible.set(accelerationMode == 2);
        accelButton.addEventListener(DLCycleButton.SelectedItemChanged.class, (src, event) -> {
            customPicker.visible.set(((DLCycleButton.SelectedItemChanged) event).index() == 2);
            return false;
        });
        row++;

        label(row, "realism.gui.sim.start");
        DLCycleButton<String> startButton = cycle(row, 84);
        startButton.items.add(Component.translatable("realism.gui.sim.start.now").getString());
        startButton.items.add(Component.translatable("realism.gui.sim.start.at").getString());
        startButton.selectedIndex.set(startNow ? 0 : 1);

        DLNumberPicker hourPicker = picker(row, 180, 46);
        hourPicker.min.set(0.0);
        hourPicker.max.set(23.0);
        hourPicker.value.set((double) startHour);
        DLRichTextLabel colon = addComponent(new DLRichTextLabel(229, rowY(row) + 3, 8, 14));
        colon.text.get().set(":");
        DLNumberPicker minutePicker = picker(row, 236, 46);
        minutePicker.min.set(0.0);
        minutePicker.max.set(59.0);
        minutePicker.value.set((double) startMinute);
        hourPicker.visible.set(!startNow);
        colon.visible.set(!startNow);
        minutePicker.visible.set(!startNow);
        startButton.addEventListener(DLCycleButton.SelectedItemChanged.class, (src, event) -> {
            boolean atTime = ((DLCycleButton.SelectedItemChanged) event).index() == 1;
            hourPicker.visible.set(atTime);
            colon.visible.set(atTime);
            minutePicker.visible.set(atTime);
            return false;
        });
        row++;

        label(row, "realism.gui.sim.horizon");
        DLNumberPicker horizonPicker = picker(row, CONTROL_X, 56);
        horizonPicker.min.set(1.0);
        horizonPicker.max.set(336.0);
        horizonPicker.format.set(new INumberFormatAdapter.UnitNumberFormat(0, "h"));
        horizonPicker.value.set((double) horizonHours);
        row++;

        DLButton runButton = addComponent(new DLButton(110, rowY(row) + 6, 80, 20));
        runButton.text.set(Component.translatable("realism.gui.sim.run"));
        runButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
            carriages = carriagePicker.value.get().intValue();
            locomotives = locomotivePicker.value.get().intValue();
            accelerationMode = (int) accelButton.selectedIndex.get();
            customAcceleration = customPicker.value.get().doubleValue();
            startNow = ((int) startButton.selectedIndex.get()) == 0;
            startHour = hourPicker.value.get().intValue();
            startMinute = minutePicker.value.get().intValue();
            horizonHours = horizonPicker.value.get().intValue();

            RNetworking.sendToServer(new RequestSimulationPacket(new SimulationService.Settings(
                    carriages, locomotives, accelerationMode, customAcceleration,
                    horizonHours, startNow, startHour, startMinute)));
            host.simulationRequested();
            closeWindow();
            return false;
        });
    }

    private static int rowY(int row) {
        return FIRST_ROW_Y + row * ROW_HEIGHT;
    }

    private DLRichTextLabel label(int row, String translationKey) {
        DLRichTextLabel label = addComponent(new DLRichTextLabel(LABEL_X, rowY(row) + 3, 100, 14));
        label.text.get().set(Component.translatable(translationKey).getString());
        return label;
    }

    private DLNumberPicker picker(int row, int x, int width) {
        return addComponent(new DLNumberPicker(x, rowY(row), width, 16));
    }

    private DLCycleButton<String> cycle(int row, int width) {
        DLCycleButton<String> button = addComponent(new DLCycleButton<>(CONTROL_X, rowY(row), width, 16));
        button.textFormat.set((ITextFormatter<DLCycleButton<String>>) (src ->
                TextUtils.text(src.selectedItem.get().map(Object::toString).orElse(""))
                        .withStyle(src.text.get().getStyle())));
        return button;
    }
}
