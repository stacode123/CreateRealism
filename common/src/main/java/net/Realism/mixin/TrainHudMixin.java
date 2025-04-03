package net.Realism.mixin;


import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.placement.PlacementHelpers;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;
import net.Realism.trains.ETCS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TrainHUD.class, remap = false)
public class TrainHudMixin {
    @Shadow static LerpedFloat displayedSpeed;
    @Shadow static LerpedFloat displayedThrottle;
    @Shadow static LerpedFloat displayedPromptSize;


    @Shadow public static Component currentPrompt;
    @Shadow public static boolean currentPromptShadow;

    //I HATE MIXINS I HATE MIXINS :) IF you know how to properly to do using @Inject plsssss fix and pull request it
    /**
     * @author Stacode
     * @reason I HATE MIXINS I HATE MIXINS :) IF you know how to properly to do using @Inject plsssss fix and pull request it
     */
    @Overwrite
    public static void renderOverlay(GuiGraphics graphics, float partialTicks, Window window) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode.getPlayerMode() == GameType.SPECTATOR)
            return;
        if (!(ControlsHandler.getContraption() instanceof CarriageContraptionEntity cce))
            return;
        Carriage carriage = cce.getCarriage();
        if (carriage == null)
            return;
        Entity cameraEntity = Minecraft.getInstance()
                .getCameraEntity();
        if (cameraEntity == null)
            return;
        BlockPos localPos = ControlsHandler.getControlsPos();
        if (localPos == null)
            return;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(window.getGuiScaledWidth() / 2 - 91, window.getGuiScaledHeight() - 29, 0);

        //ETCS
        Font font = mc.font;
        ETCS.init(graphics, carriage.train, font);




        // Speed, Throttle



        AllGuiTextures.TRAIN_HUD_FRAME.render(graphics, -2, 1);
        AllGuiTextures.TRAIN_HUD_SPEED_BG.render(graphics, 0, 0);

        int w = (int) (AllGuiTextures.TRAIN_HUD_SPEED.width * displayedSpeed.getValue(partialTicks));
        int h = AllGuiTextures.TRAIN_HUD_SPEED.height;

        graphics.blit(AllGuiTextures.TRAIN_HUD_SPEED.location, 0, 0, 0, AllGuiTextures.TRAIN_HUD_SPEED.startX,
                AllGuiTextures.TRAIN_HUD_SPEED.startY, w, h, 256, 256);

        int promptSize = (int) displayedPromptSize.getValue(partialTicks);
        if (promptSize > 1) {

            poseStack.pushPose();
            poseStack.translate(promptSize / -2f + 91, -27, 100);

            AllGuiTextures.TRAIN_PROMPT_L.render(graphics, -3, 0);
            AllGuiTextures.TRAIN_PROMPT_R.render(graphics, promptSize, 0);
            graphics.blit(AllGuiTextures.TRAIN_PROMPT.location, 0, 0, 0, AllGuiTextures.TRAIN_PROMPT.startX + (128 - promptSize / 2f),
                    AllGuiTextures.TRAIN_PROMPT.startY, promptSize, AllGuiTextures.TRAIN_PROMPT.height, 256, 256);

            poseStack.popPose();


            if (currentPrompt != null && font.width(currentPrompt) < promptSize - 10) {
                poseStack.pushPose();
                poseStack.translate(font.width(currentPrompt) / -2f + 82, -27, 100);
                if (currentPromptShadow)
                    graphics.drawString(font, currentPrompt, 9, 4, 0x544D45);
                else
                    graphics.drawString(font, currentPrompt, 9, 4, 0x544D45, false);
                poseStack.popPose();
            }
        }

        AllGuiTextures.TRAIN_HUD_DIRECTION.render(graphics, 77, -20);

        w = (int) (AllGuiTextures.TRAIN_HUD_THROTTLE.width * (1 - displayedThrottle.getValue(partialTicks)));
        int invW = AllGuiTextures.TRAIN_HUD_THROTTLE.width - w;
        graphics.blit(AllGuiTextures.TRAIN_HUD_THROTTLE.location, invW, 0, 0, AllGuiTextures.TRAIN_HUD_THROTTLE.startX + invW,
                AllGuiTextures.TRAIN_HUD_THROTTLE.startY, w, h, 256, 256);
        AllGuiTextures.TRAIN_HUD_THROTTLE_POINTER.render(graphics,
                Math.max(1, AllGuiTextures.TRAIN_HUD_THROTTLE.width - w) - 3, -2);

        // Direction

        StructureTemplate.StructureBlockInfo info = cce.getContraption()
                .getBlocks()
                .get(localPos);
        Direction initialOrientation = cce.getInitialOrientation()
                .getCounterClockWise();
        boolean inverted = false;
        if (info != null && info.state().hasProperty(ControlsBlock.FACING))
            inverted = !info.state().getValue(ControlsBlock.FACING)
                    .equals(initialOrientation);

        boolean reversing = ControlsHandler.currentlyPressed.contains(1);
        inverted ^= reversing;
        int angleOffset = (ControlsHandler.currentlyPressed.contains(2) ? -45 : 0)
                + (ControlsHandler.currentlyPressed.contains(3) ? 45 : 0);
        if (reversing)
            angleOffset *= -1;

        float snapSize = 22.5f;
        float diff = AngleHelper.getShortestAngleDiff(cameraEntity.getYRot(), cce.yaw) + (inverted ? -90 : 90);
        if (Math.abs(diff) < 60)
            diff = 0;

        float angle = diff + angleOffset;
        float snappedAngle = (snapSize * Math.round(angle / snapSize)) % 360f;

        poseStack.translate(91, -9, 0);
        poseStack.scale(0.925f, 0.925f, 1);
        PlacementHelpers.textured(poseStack, 0, 0, 1, snappedAngle);

        poseStack.popPose();
    }

}
