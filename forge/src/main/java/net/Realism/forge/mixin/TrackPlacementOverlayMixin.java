// forge/src/main/java/net/Realism/forge/mixin/TrackPlacementOverlayMixin.java
package net.Realism.forge.mixin;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.content.trains.track.TrackPlacement;
import com.simibubi.create.content.trains.track.TrackPlacementOverlay;
import com.simibubi.create.foundation.utility.Components;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.ITrackPlacementInterface;
import net.Realism.Interfaces.TrackPlacementMixinStraight;
import net.Realism.mixinaccesors.PlacementInfoAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrackPlacementOverlay.class, remap = false)
public class TrackPlacementOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    public void injectRender(ForgeGui gui, GuiGraphics graphics, float partialTicks, int width, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        PlacementInfoAccessor placementInfo = (PlacementInfoAccessor) TrackPlacement.cached;
        if (placementInfo == null) {
            return;
        }
        if (placementInfo.getCurve() == null) {
            return;
        }
        boolean Straight = ((TrackPlacementMixinStraight) placementInfo.getCurve()).isStraight();
        boolean Slope = ((TrackPlacementMixinStraight) placementInfo.getCurve()).isSlope();
        float mxspeed = (AllConfigs.server().trains.poweredTrainTopSpeed).getF() * 3.6f;
        double radius = placementInfo.getCurve().getRadius();
        double handleLength = placementInfo.getCurve().getHandleLength();
        if (mxspeed == 0) {
            mxspeed = 1;
        }
        if (radius > 90) {
            radius = 90;
        }
        MutableComponent radiusText = Components.literal(Math.round(mxspeed * radius / 90) + "km/h" + " " + Math.round(mxspeed * radius / 90 / mxspeed * 100) + "%");
        if (radius == 0) {
            if (handleLength > 45) {
                handleLength = 45;
            }
            radiusText = Components.literal(Math.round(mxspeed * handleLength / 45) + "km/h" + " " + Math.round(mxspeed * handleLength / 45 / mxspeed * 100) + "%");
        }
        if (Straight) {
            return;
        }
        if (Slope) {
            radiusText = Components.literal(Math.round(mxspeed) + "km/h" + " 100%");
        }
        int radiusX = (window.getGuiScaledWidth() - gui.getFont().width(radiusText)) / 2;
        int radiusY = window.getGuiScaledHeight() - 40; // Adjusted position
        graphics.drawString(gui.getFont(), radiusText, radiusX, radiusY, 0xFFFFFF, false);

        ITrackPlacementInterface mixin = (ITrackPlacementInterface) TrackPlacement.cached;
        double grade = mixin.getGrade();
        MutableComponent gradeText = Components.literal(Math.round(grade * 100) + "%");
        int gradeX = (window.getGuiScaledWidth() - gui.getFont().width(gradeText)) / 2;
        int gradeY = window.getGuiScaledHeight() - 50; // Adjusted position
        graphics.drawString(gui.getFont(), gradeText, gradeX, gradeY, 0xFFFFFF, false);
    }
}