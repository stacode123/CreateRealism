// fabric/src/main/java/net/Realism/fabric/mixin/TrackPlacementOverlayMixin.java
package net.Realism.fabric.mixin;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.content.trains.track.TrackPlacement;
import com.simibubi.create.content.trains.track.TrackPlacementOverlay;
import com.simibubi.create.foundation.utility.Components;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.ITrackPlacementInterface;
import net.Realism.Interfaces.TrackPlacementMixinStraight;
import net.Realism.mixinaccesors.PlacementInfoAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackPlacementOverlay.class)
public class TrackPlacementOverlayMixin {

    @Inject(method = "renderOverlay", at = @At("RETURN"))
    private static void renderOverlay(Gui gui, GuiGraphics graphics, CallbackInfo ci) {
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
        float mxspeedP = (AllConfigs.server().trains.poweredTrainTopSpeed).getF() * 3.6f;
        float mxspeedU = (AllConfigs.server().trains.trainTopSpeed).getF() * 3.6f;
        float mxspeed = Math.max(mxspeedP, mxspeedU);
        double radius = placementInfo.getCurve().getRadius();
        double handleLength = placementInfo.getCurve().getHandleLength();
        if (mxspeed == 0) {
            mxspeed = 1;
        }
        if (radius > 90) {
            radius = 90;
        }
        MutableComponent radiusText = Components.literal(Math.round(mxspeed * radius / 90) + "km/h" + " " + Math.round(mxspeed * handleLength / 45 / mxspeed * 100) + "%");
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