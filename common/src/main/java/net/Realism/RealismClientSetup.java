package net.Realism;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.config.RealismConfig;
import net.Realism.gui.ETCSGui;
import net.Realism.trains.RouteGuidanceManager;
import net.Realism.trains.etcs.ETCSMessages;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only setup: registers the ETCS display key mapping and opens the focused cab screen
 * when pressed while the local player is at a train's controls (i.e. while the ETCS onboard is
 * running). The focused screen shows the same DMI as the HUD overlay, enlarged and interactive.
 */
public final class RealismClientSetup {

    public static final KeyMapping OPEN_ETCS_SCREEN = new KeyMapping(
            "key.realism.etcs_screen", GLFW.GLFW_KEY_V, "key.categories.realism");

    private static boolean registered = false;

    private RealismClientSetup() {}

    public static void register() {
        if (registered) return;
        registered = true;

        KeyMappingRegistry.register(OPEN_ETCS_SCREEN);
        ClientTickEvent.CLIENT_POST.register(RealismClientSetup::onClientTick);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            if (player != null) RouteGuidanceManager.clearClient(player.getUUID());
            ETCSGui.forceClose();
            ETCSMessages.endDrive();
        });
    }

    private static void onClientTick(Minecraft mc) {
        while (OPEN_ETCS_SCREEN.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            if (!(mc.player.getVehicle() instanceof CarriageContraptionEntity cce)) continue;
            if (!mc.player.getUUID().equals(cce.getControllingPlayer().orElse(null))) continue;

            Carriage carriage = cce.getCarriage();
            if (carriage == null || !(carriage.train instanceof ITrainInterface rtrain)) continue;

            // Only while actively at the controls, matching when the ETCS display is shown.
            if (rtrain.realism$getETCS() == null || !rtrain.realism$getETCS().toUpdate) continue;
            // Respect the same switches as the HUD: never open the cab screen when ETCS is off.
            if (!RealismConfig.CLIENT.ETCSEnable.get() || !RealismConfig.COMMON.GlobalETCSEnable.get()) continue;

            DLWindow.openWindow(manager -> new ETCSGui(manager, rtrain));
        }
    }
}
