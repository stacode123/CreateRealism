package net.Realism.forge.mixin;


import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.Carriage;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.config.RealismConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrainHUD.class)
public abstract class TrainHudMixin {

    @Shadow
    protected static Carriage getCarriage() {
        return null;
    }

    @Inject(
            method = "renderOverlay",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private static void afterPoseStackTranslate(ForgeGui gui, GuiGraphics graphics, float partialTicks,
                                                int width, int height, CallbackInfo ci) {
        if(!(getCarriage().train instanceof ITrainInterface Rtrain)){
            return;}
        if(RealismConfig.CLIENT.ETCSEnable.get() && RealismConfig.COMMON.GlobalETCSEnable.get() && Rtrain.realism$getETCS() != null) {
            Rtrain.realism$getETCS().render(graphics);}
    }

}
