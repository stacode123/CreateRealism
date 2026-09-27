package net.Realism.gui;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextEditBox;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.autocomplete.DLAutocompleteWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.autocomplete.IAutocompletionManager;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.network.RouteGuidanceSavePacket;
import net.Realism.trains.RouteGuidance;
import net.Realism.trains.RouteGuidanceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Manual route guidance settings: pick a destination station (with autocomplete) and optionally let
 * the mod steer at junctions while the player keeps manual throttle control.
 */
public class RouteGuidanceGui extends DLWindow {

    public RouteGuidanceGui(DLWindowManager manager, ITrainInterface Rtrain) {
        super(manager);

        setSize(280, 170);
        setPosition(0, 0);

        RouteGuidance current = clientGuidance();

        DLRichTextLabel title = addComponent(new DLRichTextLabel(10, 6, 260, 18));
        title.text.get().set(Component.translatable("realism.gui.route.title").getString());

        DLRichTextLabel destLabel = addComponent(new DLRichTextLabel(10, 34, 260, 18));
        destLabel.text.get().set(Component.translatable("realism.gui.route.destination").getString());

        DLRichTextEditBox destBox = addComponent(new DLRichTextEditBox(10, 52, 260, 18));
        destBox.multiline.set(false);
        destBox.text.get().set(current.destinationName);
        destBox.autocompleteManager.set(new StationSuggestionProvider());

        DLCheckBox autoBox = addComponent(new DLCheckBox(10, 82, 260, 16));
        autoBox.text.set(Component.translatable("realism.gui.route.auto_steer"));
        autoBox.checked.set(current.autoSteer);

        DLButton confirm = addComponent(new DLButton(10, 130, 90, 20));
        confirm.text.set(Component.translatable("realism.button.confirm"));
        confirm.addEventListener(DLGuiStandardEvents.ClickEvent.class, (evt, s) -> {
            send(destBox.text.get().getPlainText(), autoBox.checked.get());
            closeWindow();
            return false;
        });

        DLButton clear = addComponent(new DLButton(110, 130, 90, 20));
        clear.text.set(Component.translatable("realism.gui.route.clear"));
        clear.addEventListener(DLGuiStandardEvents.ClickEvent.class, (evt, s) -> {
            send("", false);
            closeWindow();
            return false;
        });
    }

    private static RouteGuidance clientGuidance() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return new RouteGuidance();
        return RouteGuidanceManager.getClient(mc.player.getUUID());
    }

    private static void send(String destinationName, boolean autoSteer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            RouteGuidanceManager.setClient(mc.player.getUUID(), destinationName, autoSteer);
        }
        RNetworking.sendToServer(new RouteGuidanceSavePacket(destinationName, autoSteer));
    }

    /** Autocomplete backed by every station name known to the client. */
    public static class StationSuggestionProvider implements IAutocompletionManager<String> {

        @Override
        public void configureWindow(DLAutocompleteWindow<String> window, DLRichTextEditBox textBox) {
            List<String> all = stationNames();
            if (all.isEmpty()) return;

            String query = textBox.text.get().getPlainText();
            String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);

            // Populate first, then narrow: the filter change selects the first match.
            window.suggestions.set(all);
            window.filter.set(name -> name.toLowerCase(Locale.ROOT).contains(lower));
        }

        private static List<String> stationNames() {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            try {
                for (TrackGraph graph : Create.RAILWAYS.sided(null).trackNetworks.values()) {
                    for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                        if (station.name != null && !station.name.isBlank()) names.add(station.name);
                    }
                }
            } catch (Throwable ignored) {
                // Client side graph may not be available yet; just show no suggestions.
            }
            return new ArrayList<>(names);
        }
    }
}
