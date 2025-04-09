package net.Realism.trains.etcs;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class ETCStools {
    /**
     * Calculate the needle rotation angle based on current train speed
     */
    public static float calculateNeedleRotation(double trainSpeed) {
        float rotationDegrees;

        float speedKmh = (float) Math.abs(trainSpeed * 20 * 3.6f);

        if (speedKmh <= 160f) {
            rotationDegrees = -151.5f + (1.134f) * speedKmh;
        } else {
            rotationDegrees = 30f + ((0.671f) * (speedKmh - 160f));
        }

        return rotationDegrees;
    }

    public static void renderElement(GuiGraphics graphics, ResourceLocation path, int x, int y, int width, int height) {
        RenderSystem.setShaderTexture(0, path);
        graphics.blit(path, x, y, 0, 0, width, height, width, height);
    }

    public static void optimizedRenderSpeedCurve(GuiGraphics graphics, PoseStack poseStack, int centerX, int centerY, double maxSpeed , int color) {
        // Arc parameters
        int radius = 115;
        int arcWidth = 7;

        // Determine angle range based on speed
        float startAngleRad = -233.5f * (float)(Math.PI / 180);
        float endAngleRad;

        if(maxSpeed > 300) {
            maxSpeed = 300;
        }

        if (maxSpeed <= 160) {
            endAngleRad = (float) ((-233.5f + (1.134f * maxSpeed)) * (float)(Math.PI / 180));
        } else {
            endAngleRad = (float) ((-50f + (0.671f * (maxSpeed - 160f))) * (float)(Math.PI / 180));
        }

        // Use more segments for smoother arc
        int segments = 100;

        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 0);

        // Draw the arc as a filled region
        for (int w = 0; w <= arcWidth; w++) {
            float r = radius - w;
            float prevX = (float)Math.cos(startAngleRad) * r;
            float prevY = (float)Math.sin(startAngleRad) * r;

            for (int i = 1; i <= segments; i++) {
                float ratio = (float)i / segments;
                float angle = startAngleRad + (endAngleRad - startAngleRad) * ratio;

                float x = (float)Math.cos(angle) * r;
                float y = (float)Math.sin(angle) * r;

                // Draw line from previous point to current point
                drawLine(graphics, (int)prevX, (int)prevY, (int)x, (int)y, color);

                prevX = x;
                prevY = y;
            }
        }

        // Add a 16 pixel wide indicator extending inward at the end of the arc
        float endX = (float)Math.cos(endAngleRad) * (radius - (float) arcWidth /2);
        float endY = (float)Math.sin(endAngleRad) * (radius - (float) arcWidth /2);

        // Calculate the inner point 16 pixels inward
        float innerEndX = (float)Math.cos(endAngleRad) * (radius - arcWidth - 16);
        float innerEndY = (float)Math.sin(endAngleRad) * (radius - arcWidth - 16);

        // Draw the inward indicator
        for (int i = 0; i < 16; i++) {
            float offsetX = i * (innerEndX - endX) / 16;
            float offsetY = i * (innerEndY - endY) / 16;
            drawLine(graphics,
                    (int)(endX + offsetX - 2), (int)(endY + offsetY - 2),
                    (int)(endX + offsetX + 2), (int)(endY + offsetY + 2),
                    color);
        }

        poseStack.popPose();
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        // For horizontal lines
        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            graphics.fill(minX, y1, maxX + 1, y1 + 1, color);
            return;
        }

        // For vertical lines
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            graphics.fill(x1, minY, x1 + 1, maxY + 1, color);
            return;
        }

        // For diagonal lines, use Bresenham's algorithm
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);

            if (x1 == x2 && y1 == y2)
                break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }


}

