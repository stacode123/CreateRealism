package net.Realism.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.Realism.Interfaces.IOrientedContraptionEntity;
import net.Realism.debug.RealismDebuger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mixin to apply roll rotation during rendering
 */
@Mixin(value = OrientedContraptionEntity.class, remap = false)
public abstract class OrientedContraptionEntityRenderMixin {

    /**
     * Inject roll rotation into the rendering transform
     * This injects right after the existing transformations in applyLocalTransforms
     */
    @Inject(method = "applyLocalTransforms", at = @At(value = "TAIL"))
    private void applyRollTransform(PoseStack matrixStack, float partialTicks, CallbackInfo ci) {
        // Get the roll angle with interpolation
        IOrientedContraptionEntity orientedEntity = (IOrientedContraptionEntity) this;
        float angleRoll = orientedEntity.realism$getViewRoll(partialTicks);

        // Only apply roll if it's non-zero
        if (Math.abs(angleRoll) > 0.001f) {
            // Apply roll rotation around the X-axis (forward axis of the train)
            // This is applied after all other transformations in the method
            TransformStack.of(matrixStack).rotateX((float) Math.toRadians(angleRoll));
        }
        String debug = String.format("Roll angle: %.2f", angleRoll);
        RealismDebuger.getInstance().addDebugMessage(debug);
    }
}

