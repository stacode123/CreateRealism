package net.Realism.fabric.mixin;


import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.Carriage;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.trains.ETCS;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrainHUD.class, remap = false)
public abstract class TrainHudMixin {


    @Shadow
    protected static Carriage getCarriage() {
        return null;
    }

    @Inject(method = "renderOverlay", at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            ordinal = 0, // This targets the third translate call (0-indexed)
            shift = At.Shift.BEFORE // Important: inject AFTER the translate call
    ))
    private static void injectAfterDirectionTranslate(GuiGraphics graphics, float partialTicks,
                                                      Window window, CallbackInfo ci) {
        if(getCarriage().train instanceof ITrainInterface RTrain) {
        RTrain.realism$getETCS().render(graphics);}
    }
}
