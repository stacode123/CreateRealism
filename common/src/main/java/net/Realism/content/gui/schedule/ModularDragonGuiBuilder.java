package net.Realism.content.gui.schedule;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.foundation.gui.widget.TooltipArea;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLNumberPicker;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextEditBox;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.INumberFormatAdapter;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.ITextFormatter;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.createmod.catnip.data.Glob;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ModularDragonGuiBuilder extends ModularGuiLineBuilder {
    private final DLPanel target;
    private final CompoundTag data;

    public ModularDragonGuiBuilder(DLPanel target, CompoundTag data) {
        super(Minecraft.getInstance().font, new ModularGuiLine(), 0, 0);
        this.target = target;
        this.data = data;
    }

    @Override
    public ModularGuiLineBuilder addScrollInput(int x, int width, BiConsumer<ScrollInput, Label> inputTransform, String dataKey) {
        ExposedScrollInput exposedInput = new ExposedScrollInput(x, 0, width, 18);
        ExposedLabel exposedLabel = new ExposedLabel(x, 0, Component.empty());

        inputTransform.accept(exposedInput, exposedLabel);

        DLNumberPicker picker = new DLNumberPicker(x, 0, width, 20);
        picker.showButtons.set(false);
        picker.min.set((double) exposedInput.getMin());
        picker.max.set((double) exposedInput.getMax() - 1); // Create max is exclusive


        if (data.contains(dataKey)) {
            picker.value.set((double) data.getInt(dataKey));
        } else {
            picker.value.set((double) exposedInput.getState());
        }

        picker.format.set(new INumberFormatAdapter() {
            @Override
            public String format(double value) {
                return exposedInput.getFormatted((int) value).getString();
            }

            @Override
            public double parse(String text) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        });

        String suffix = exposedLabel.suffix;
        if (suffix != null && !suffix.isEmpty()) {
            // If there's a suffix, we might want to prioritize it or wrap the previous formatter.
            // But for now, let's just keep the existing logic where it might override.
            // Actually, if we have both, the custom formatter is probably better.
        }

        Consumer<Integer> onScroll = exposedInput.getOnScroll();
        picker.addEventListener(DLNumberPicker.ValueChangedEvent.class, (src, event) -> {
            int val = (int) (double) event.value();
            data.putInt(dataKey, val);
            if (onScroll != null) {
                onScroll.accept(val);
            }
            return false;
        });

        target.addComponent(picker);
        return this;
    }

    @Override
    public ModularGuiLineBuilder addSelectionScrollInput(int x, int width, BiConsumer<SelectionScrollInput, Label> inputTransform, String dataKey) {
        ExposedSelectionScrollInput exposedInput = new ExposedSelectionScrollInput(x, 0, width, 18);
        ExposedLabel exposedLabel = new ExposedLabel(x, 0, Component.empty());

        inputTransform.accept(exposedInput, exposedLabel);

        DLCycleButton<Component> cycleButton = new DLCycleButton<>(x, 0, width, 20);
        cycleButton.items.addAll(exposedInput.getOptions());

        if (data.contains(dataKey)) {
            cycleButton.selectedIndex.set(data.getInt(dataKey));
        } else {
            cycleButton.selectedIndex.set(exposedInput.getState());
        }

        cycleButton.textFormat.set((ITextFormatter<DLCycleButton<Component>>) (src -> {
            Component selected = src.selectedItem.get().orElse(null);
            if (selected == null) return TextUtils.text("");
            
            // Check if there's a custom formatter in the original widget
            // Create's SelectionScrollInput.format is a Function<Integer, Component>
            // but we can't easily access it from the base class.
            // However, CargoThresholdCondition uses .format(state -> Component.literal(" " + Ops.values()[state].formatted))
            
            Component formattedText = exposedInput.getFormatted(src.selectedIndex.get());
            if (formattedText != null) {
                return TextUtils.text(formattedText.getString());
            }

            return TextUtils.text(selected.getString());
        }));

        Consumer<Integer> onScroll = exposedInput.getOnScroll();
        cycleButton.addEventListener(DLCycleButton.SelectedItemChanged.class, (src, event) -> {
            int index = cycleButton.selectedIndex.get();
            data.putInt(dataKey, index);
            if (onScroll != null) {
                onScroll.accept(index);
            }
            return false;
        });
        cycleButton.addEventListener(DLGuiStandardEvents.ScrollEvent.class, (src, event) -> {
            int index = cycleButton.selectedIndex.get();
            if(cycleButton.items.size()-1 > index)
                cycleButton.selectedIndex.set(index + 1);
            else cycleButton.selectedIndex.set(0);
            return false;
        });

        target.addComponent(cycleButton);
        return this;
    }

    @Override
    public ModularGuiLineBuilder addTextInput(int x, int width, BiConsumer<EditBox, TooltipArea> inputTransform, String dataKey) {
        DLRichTextEditBox editBox = new DLRichTextEditBox(x, 0, width, 20){};

        if (data.contains(dataKey)) {
            editBox.text.get().set(data.getString(dataKey));
        }

        editBox.addEventListener(DLRichTextEditBox.TextChangedEvent.class, (src, event) -> {
            String newText = event.text().getPlainText();
            data.putString(dataKey, newText);
            return false;
        });

        target.addComponent(editBox);
        return this;
    }

    @Override
    public ModularGuiLineBuilder addIntegerTextInput(int x, int width, BiConsumer<EditBox, TooltipArea> inputTransform, String dataKey) {
        DLRichTextEditBox editBox = new DLRichTextEditBox(x, 0, width, 20){};

        if (data.contains(dataKey)) {
            editBox.text.get().set(data.getString(dataKey));
        }

        editBox.addEventListener(DLRichTextEditBox.TextChangedEvent.class, (src, event) -> {
            String newText = event.text().getPlainText();
            if (newText.isEmpty()) {
                data.putString(dataKey, "");
                return false;
            }
            try {
                Integer.parseInt(newText);
                data.putString(dataKey, newText);
            } catch (NumberFormatException e) {
            }
            return false;
        });

        target.addComponent(editBox);
        return this;
    }

    @Override
    public ModularGuiLineBuilder customArea(int x, int width) {
        return this;
    }

    @Override
    public ModularGuiLineBuilder speechBubble() {
        return this;
    }

    // Exposed classes to access protected fields of Create widgets
    private static class ExposedScrollInput extends ScrollInput {
        public ExposedScrollInput(int x, int y, int w, int h) { super(x, y, w, h); }
        public int getMin() { return min; }
        public int getMax() { return max; }
        public Consumer<Integer> getOnScroll() { return onScroll; }
        public Component getFormatted(int state) { return formatter.apply(state); }
    }

    private static class ExposedSelectionScrollInput extends SelectionScrollInput {
        public ExposedSelectionScrollInput(int x, int y, int w, int h) { super(x, y, w, h); }
        public List<? extends Component> getOptions() { return options; }
        public Consumer<Integer> getOnScroll() { return onScroll; }
        public Component getFormatted(int state) { return formatter.apply(state); }
    }

    private static class ExposedLabel extends Label {
        public ExposedLabel(int x, int y, Component text) { super(x, y, text); }
        // suffix is public, color/hasShadow are protected
    }

    private List<IntAttached<String>> getViableStations(String field, boolean use) {
        if (!use) return null;
        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(null);
        Set<TrackGraph> viableGraphs = new HashSet<>(railwayManager.trackNetworks.values());

        String filter = Glob.toRegexPattern(field, "");
        if (filter.isBlank())
            return null;
        Graphs:
        for (Iterator<TrackGraph> iterator = viableGraphs.iterator(); iterator.hasNext(); ) {
            TrackGraph trackGraph = iterator.next();
            for (GlobalStation station : trackGraph.getPoints(EdgePointType.STATION)) {
                if (station.name.matches(filter))
                    continue Graphs;
            }
            iterator.remove();
        }

        if (viableGraphs.isEmpty())
            viableGraphs = new HashSet<>(railwayManager.trackNetworks.values());

        Vec3 position = Minecraft.getInstance().player.position();
        Set<String> visited = new HashSet<>();

        return viableGraphs.stream()
                .flatMap(g -> g.getPoints(EdgePointType.STATION)
                        .stream())
                .filter(station -> station.blockEntityPos != null)
                .filter(station -> visited.add(station.name))
                .map(station -> IntAttached.with((int) Vec3.atBottomCenterOf(station.blockEntityPos)
                        .distanceTo(position), station.name))
                .toList();
    }
}
