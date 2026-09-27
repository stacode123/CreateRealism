package net.Realism;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.gui.RouteGuidanceGui;
import net.Realism.trains.RouteGuidanceManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only setup: registers the route-guidance key mapping and opens the GUI when pressed while
 * the local player is at a train's controls (i.e. while the ETCS display is active).
 */
public final class RealismClientSetup {

    public static final KeyMapping OPEN_ROUTE_GUIDANCE = new KeyMapping(
            "key.realism.route_guidance", GLFW.GLFW_KEY_UNKNOWN, "key.categories.realism");

    private static boolean registered = false;

    private RealismClientSetup() {}

    public static void register() {
        if (registered) return;
        registered = true;

        KeyMappingRegistry.register(OPEN_ROUTE_GUIDANCE);
        ClientTickEvent.CLIENT_POST.register(RealismClientSetup::onClientTick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            if (player != null) RouteGuidanceManager.clearClient(player.getUUID());
        });
    }

    /** Clears the local player's guidance and tells the server, e.g. when leaving the controls. */
    public static void clearGuidance() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        RouteGuidanceManager.clearClient(mc.player.getUUID());
        RNetworking.sendToServer(new net.Realism.network.RouteGuidanceSavePacket("", false));
    }

    private static void onClientTick(Minecraft mc) {
        while (OPEN_ROUTE_GUIDANCE.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            if (!(mc.player.getVehicle() instanceof CarriageContraptionEntity cce)) continue;

            Carriage carriage = cce.getCarriage();
            if (carriage == null || !(carriage.train instanceof ITrainInterface rtrain)) continue;

            // Only while actively at the controls, matching when the ETCS display is shown.
            if (rtrain.realism$getETCS() == null || !rtrain.realism$getETCS().toUpdate) continue;

            DLWindow.openWindow(manager -> new RouteGuidanceGui(manager, rtrain));
        }
    }
}
