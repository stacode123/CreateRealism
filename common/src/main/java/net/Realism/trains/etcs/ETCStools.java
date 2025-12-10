package net.Realism.trains.etcs;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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

        float speedKmh = (float) Math.abs(trainSpeed * 20 * 3.6f);

        if (speedKmh <= 200f) {
            rotationDegrees = -151.5f + (0.9f) * speedKmh;
        } else if (speedKmh >= 420f) {
            rotationDegrees = 138.5f;
        } else {
            rotationDegrees = 28.5f + ((0.5f) * (speedKmh - 200f));
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

        if(maxSpeed > 420) {
            maxSpeed = 420;
        }
        if (maxSpeed <= 200) {
            endAngleRad = (float) ((-233.5f + (0.9f * maxSpeed)) * (float)(Math.PI / 180));
        } else {
            endAngleRad = (float) ((-53.5f + (0.5f * (maxSpeed - 200f))) * (float)(Math.PI / 180));
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
        BufferBuilder buffer = tesselator.getBuilder();

        // Draw arc as a triangle strip
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float ratio = (float)i / segments;
            float angle = startAngleRad + (endAngleRad - startAngleRad) * ratio;

            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);

            // Outer vertex
            float outerX = cos * radius;
            float outerY = sin * radius;
            buffer.vertex(matrix, outerX, outerY, 0).color(r, g, b, a).endVertex();

            // Inner vertex
            float innerX = cos * (radius - arcWidth);
            float innerY = sin * (radius - arcWidth);
            buffer.vertex(matrix, innerX, innerY, 0).color(r, g, b, a).endVertex();
        }

        tesselator.end();

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

        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, endX + perpX, endY + perpY, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, endX - perpX, endY - perpY, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, innerEndX + perpX, innerEndY + perpY, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, innerEndX - perpX, innerEndY - perpY, 0).color(r, g, b, a).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();

        poseStack.popPose();
    }






}
