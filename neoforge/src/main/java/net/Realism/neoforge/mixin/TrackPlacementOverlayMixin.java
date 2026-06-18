package net.Realism.neoforge.mixin;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.content.trains.track.TrackPlacement;
import net.Realism.mixin.mixinaccesors.PlacementInfoAccessor;
import net.Realism.trains.TrackOverlay;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.simibubi.create.content.trains.track.TrackPlacementOverlay.class, remap = false)
public class TrackPlacementOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    public void injectRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        PlacementInfoAccessor placementInfo = (PlacementInfoAccessor) TrackPlacement.cached;
        Pair<MutableComponent, MutableComponent> text = TrackOverlay.getText(placementInfo);
        if (text == null) {
            return;
        }
        int radiusX = (window.getGuiScaledWidth() - mc.font.width(text.getFirst())) / 2;
        int radiusY = window.getGuiScaledHeight() - 40;
        if (mc.gameMode.getPlayerMode() == GameType.SURVIVAL) {
            radiusX = (window.getGuiScaledWidth() - mc.font.width(text.getFirst())) / 2 + 50;
            radiusY = window.getGuiScaledHeight() - 50;
        }
        graphics.drawString(mc.font, text.getFirst(), radiusX, radiusY, 0xFFFFFF, false);

        int gradeX = (window.getGuiScaledWidth() - mc.font.width(text.getSecond())) / 2;
        int gradeY = window.getGuiScaledHeight() - 50;
        graphics.drawString(mc.font, text.getSecond(), gradeX, gradeY, 0xFFFFFF, false);
    }
}