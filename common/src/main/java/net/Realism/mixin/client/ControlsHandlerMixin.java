package net.Realism.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import net.Realism.gui.ETCSGui;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Create releases train control as soon as the raw ESC key is down. The focused ETCS cab
 * screen also closes on ESC, so without this filter a single press would both leave the
 * screen and drop the driver out of the controls. While the cab screen owns ESC - it is
 * open, or it was just closed by ESC and the key has not been released yet - report the key
 * as up. The next (second) ESC press is passed through and releases control as usual.
 */
@Mixin(value = ControlsHandler.class, remap = false)
public class ControlsHandlerMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z",
                    remap = true))
    private static boolean realism$consumeCabScreenEscape(long window, int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE && ETCSGui.shouldSuppressEscapeForTrainControl()) {
            return false;
        }
        return InputConstants.isKeyDown(window, key);
    }
}
