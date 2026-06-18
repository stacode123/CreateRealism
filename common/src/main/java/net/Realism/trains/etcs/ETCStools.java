package net.Realism.trains.etcs;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Realism.config.RealismConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class ETCStools {
    /**
     * Calculate the needle rotation angle based on current train speed
     */
    public static float calculateNeedleRotation(double trainSpeed) {
        float rotationDegrees;
        boolean useMph = RealismConfig.CLIENT.ETCSMPH.get();
        float speed = (float) trainSpeed * 20 * 3.6f;
        if (useMph){
            speed =  speed * 0.621371192f;
        }
        if (speed <= 160f) {
            rotationDegrees = -151.5f + (1.134f) * speed;
        } else {
            rotationDegrees = 30f + ((0.671f) * (speed - 160f));
        }

        return rotationDegrees;
    }

    public static void renderElement(GuiGraphics graphics, ResourceLocation path, int x, int y, int width, int height) {
        RenderSystem.setShaderTexture(0, path);
        graphics.blit(path, x, y, 0, 0, width, height, width, height);
    }

    public static void optimizedRenderSpeedCurve(GuiGraphics graphics, PoseStack poseStack, int centerX, int centerY, double maxSpeed, int color) {
        // Arc parameters
        int radius = 115;
        int arcWidth = 7;

        // Determine angle range based on speed
        float startAngleRad = -233.5f * (float)(Math.PI / 180);
        float endAngleRad;

        if(maxSpeed > 300) {
            maxSpeed = 300;
        }
        if(RealismConfig.CLIENT.ETCSMPH.get()) {
            maxSpeed =  (maxSpeed * 0.621371192);
        }

        if (maxSpeed <= 160) {
            endAngleRad = (float) ((-233.5f + (1.134f * maxSpeed)) * (float)(Math.PI / 180));
        } else {
            endAngleRad = (float) ((-50f + (0.671f * (maxSpeed - 160f))) * (float)(Math.PI / 180));
        }

        // Extract color components
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        // Use fewer segments - GPU interpolation will smooth it out
        int segments = 64;

        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 0);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float ratio = (float)i / segments;
            float angle = startAngleRad + (endAngleRad - startAngleRad) * ratio;

            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);

            // Outer vertex
            float outerX = cos * radius;
            float outerY = sin * radius;
            buffer.addVertex(matrix, outerX, outerY, 0.0f).setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));

            // Inner vertex
            float innerX = cos * (radius - arcWidth);
            float innerY = sin * (radius - arcWidth);
            buffer.addVertex(matrix, innerX, innerY, 0.0f).setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
        }

        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        // Draw the inward indicator at the end
        float endX = (float)Math.cos(endAngleRad) * (radius - (float) arcWidth / 2);
        float endY = (float)Math.sin(endAngleRad) * (radius - (float) arcWidth / 2);
        float innerEndX = (float)Math.cos(endAngleRad) * (radius - arcWidth - 16);
        float innerEndY = (float)Math.sin(endAngleRad) * (radius - arcWidth - 16);

        // Calculate perpendicular for width
        float perpX = -(endY - innerEndY);
        float perpY = (endX - innerEndX);
        float perpLen = (float)Math.sqrt(perpX * perpX + perpY * perpY);
        perpX = (perpX / perpLen) * 2; // 4 pixel width (2 on each side)
        perpY = (perpY / perpLen) * 2;

        // Second draw call — need a new buffer from tesselator.begin()
        BufferBuilder buffer2 = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        buffer2.addVertex(matrix, endX + perpX, endY + perpY, 0.0f).setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
        buffer2.addVertex(matrix, endX - perpX, endY - perpY, 0.0f).setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
        buffer2.addVertex(matrix, innerEndX + perpX, innerEndY + perpY, 0.0f).setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
        buffer2.addVertex(matrix, innerEndX - perpX, innerEndY - perpY, 0.0f).setColor((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));

        meshData = buffer2.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        RenderSystem.disableBlend();

        poseStack.popPose();
    }



}