// fabric/src/main/java/net/Realism/fabric/mixin/TrackPlacementOverlayMixin.java
package net.Realism.fabric.mixin;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.content.trains.track.TrackPlacement;
import com.simibubi.create.content.trains.track.TrackPlacementOverlay;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.ITrackPlacementInterface;
import net.Realism.Interfaces.ITrackPlacementMixin;
import net.Realism.mixin.mixinaccesors.PlacementInfoAccessor;
import net.Realism.trains.TrackOverlay;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.GameType;
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
        Pair<MutableComponent,MutableComponent> text = TrackOverlay.getText(placementInfo);
        if (text==null){
            return;
        }
        int radiusX = (window.getGuiScaledWidth() - gui.getFont().width(text.getFirst())) / 2;
        int radiusY = window.getGuiScaledHeight() - 40; // Adjusted position
        if(mc.gameMode.getPlayerMode() == GameType.SURVIVAL) {
            radiusX = (window.getGuiScaledWidth() - gui.getFont().width(text.getFirst())) / 2+50;
            radiusY = window.getGuiScaledHeight() - 50;
        }
         // Adjusted position
        graphics.drawString(gui.getFont(), text.getFirst(), radiusX, radiusY, 0xFFFFFF, false);
        int gradeX = (window.getGuiScaledWidth() - gui.getFont().width(text.getSecond())) / 2;
        int gradeY = window.getGuiScaledHeight() - 50; // Adjusted position
        graphics.drawString(gui.getFont(), text.getSecond(), gradeX, gradeY, 0xFFFFFF, false);
    }
}