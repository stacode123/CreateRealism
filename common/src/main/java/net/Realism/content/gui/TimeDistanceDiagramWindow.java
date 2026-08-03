package net.Realism.content.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.Realism.content.simulator.SimulationPayload;
import net.Realism.content.simulator.core.SimConflict;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The classic train graph (SPEC §4.8): X = game time, Y = distance along the
 * phantom's route corridor. One polyline per train from the payload's
 * corridor-projected segments — the phantom bright, opposing traffic
 * descending — plus station gridlines and conflict markers. Drag pans and
 * scroll zooms the time axis; the distance axis always shows the whole
 * corridor.
 */
public class TimeDistanceDiagramWindow extends DLWindow {

    private static final int COLOR_BACKGROUND = 0xFF10101C;
    private static final int COLOR_GRID = 0xFF262636;
    private static final int COLOR_STATION_LINE = 0xFF34344A;
    private static final int COLOR_STATION_TEXT = 0xFF55DDEE;
    private static final int COLOR_AXIS_TEXT = 0xFF9090A0;
    private static final int COLOR_PHANTOM = 0xFF40FF70;
    private static final int COLOR_OBSTACLE = 0xFF707078;
    private static final int[] PALETTE = {
            0xFFE0B040, 0xFF60A0FF, 0xFFFF6090, 0xFF80E0D0,
            0xFFC080FF, 0xFFFFA060, 0xFFB0D060, 0xFFDD8888 };
    /** Marker colors, indexed by {@link SimConflict.Type} ordinal. */
    private static final int[] CONFLICT_COLORS = { 0xFFFF6040, 0xFFDD2222, 0xFFFFC020, 0xFF40C0FF };

    /** Left margin for station names, bottom margin for time labels. */
    private static final int LABEL_WIDTH = 88;
    private static final int AXIS_HEIGHT = 14;
    /** Time-grid step candidates in game-time minutes. */
    private static final int[] GRID_MINUTES = { 5, 10, 30, 60, 180, 360, 720, 1440 };

    private final SimulationPayload payload;
    private final int windowWidth;
    private final int windowHeight;

    /** Visible time window, in sim ticks. */
    private double viewStart;
    private double viewSpan;
    private final long totalTicks;

    public TimeDistanceDiagramWindow(DLWindowManager manager, SimulationPayload payload) {
        super(manager);
        this.payload = payload;
        this.totalTicks = Math.max(1, payload.ticksSimulated);
        this.viewStart = 0;
        this.viewSpan = totalTicks;

        var mcWindow = Minecraft.getInstance().getWindow();
        int screenWidth = mcWindow.getGuiScaledWidth();
        int screenHeight = mcWindow.getGuiScaledHeight();
        windowWidth = Math.min(900, Math.max(470, screenWidth - 20));
        windowHeight = Math.max(248, screenHeight - 20);
        setSize(windowWidth, windowHeight);
        setPosition(Math.max(0, (screenWidth - windowWidth) / 2),
                Math.max(0, (screenHeight - windowHeight) / 2));

        DLRichTextLabel title = addComponent(new DLRichTextLabel(windowWidth / 2 - 100, 8, 200, 20));
        title.text.get().set(Component.translatable("realism.gui.diagram.title").getString());

        DiagramCanvas canvas = addComponent(new DiagramCanvas(10, 22, windowWidth - 20, windowHeight - 52));
        canvas.addEventListener(DLGuiStandardEvents.ScrollEvent.class, (src, event) -> {
            DLGuiStandardEvents.ScrollEvent scroll = (DLGuiStandardEvents.ScrollEvent) event;
            canvas.zoom(scroll.deltaY(), canvas.getLocalMouseX());
            return false;
        });
        canvas.addEventListener(DLGuiStandardEvents.DragEvent.class, (src, event) -> {
            DLGuiStandardEvents.DragEvent drag = (DLGuiStandardEvents.DragEvent) event;
            canvas.pan(drag.dragX());
            return false;
        });

        DLRichTextLabel hint = addComponent(new DLRichTextLabel(12, windowHeight - 26, windowWidth - 120, 14));
        hint.text.get().set(Component.translatable("realism.gui.diagram.hint").getString());
        DLButton closeButton = addComponent(new DLButton(windowWidth - 90, windowHeight - 26, 80, 20));
        closeButton.text.set(Component.translatable("gui.done"));
        closeButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
            closeWindow();
            return false;
        });
    }

    /** The plot area; all drawing is component-local (the pose is pre-translated). */
    private class DiagramCanvas extends DLGuiComponent {

        private final int plotX = LABEL_WIDTH;
        private final int plotY = 2;
        private final int plotWidth;
        private final int plotHeight;

        DiagramCanvas(int x, int y, int width, int height) {
            super(x, y, width, height);
            this.plotWidth = width - LABEL_WIDTH - 4;
            this.plotHeight = height - AXIS_HEIGHT - plotY;
        }

        void zoom(double delta, double localMouseX) {
            double anchorTick = tickAt(localMouseX);
            double factor = Math.pow(1.25, -delta);
            viewSpan = Math.max(100, Math.min(totalTicks, viewSpan * factor));
            viewStart = anchorTick - (anchorTick - viewStart) * (localMouseX > plotX ? factor : 1);
            clampView();
        }

        void pan(double dragX) {
            viewStart -= dragX * viewSpan / plotWidth;
            clampView();
        }

        private void clampView() {
            viewStart = Math.max(0, Math.min(totalTicks - viewSpan, viewStart));
        }

        private double tickAt(double localX) {
            return viewStart + (localX - plotX) * viewSpan / plotWidth;
        }

        private float toX(double tick) {
            return (float) (plotX + (tick - viewStart) / viewSpan * plotWidth);
        }

        private float toY(double pos) {
            double length = Math.max(1e-6, payload.diagramLength);
            return (float) (plotY + plotHeight - pos / length * plotHeight);
        }

        @Override
        public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY,
                                    Rectangle bounds) {
            super.renderMainLayer(graphics, mouseX, mouseY, bounds);
            var gui = graphics.graphics();
            Matrix4f matrix = graphics.poseStack().last().pose();
            // One managed batch: the screen's GuiGraphics is unmanaged, so
            // plain fill() calls flush themselves while raw vertex writes
            // linger in the buffer and lose the depth test to content drawn
            // in between. Batched together, everything renders in emission
            // order in a single draw.
            gui.drawManaged(() -> {
                gui.fill(0, 0, (int) width(), (int) height(), COLOR_BACKGROUND);

                long gridStep = pickGridStep();
                long firstLine = (long) Math.ceil(viewStart / gridStep) * gridStep;
                for (long tick = firstLine; tick <= viewStart + viewSpan; tick += gridStep) {
                    int x = Math.round(toX(tick));
                    if (x >= plotX && x <= plotX + plotWidth)
                        gui.fill(x, plotY, x + 1, plotY + plotHeight, COLOR_GRID);
                }
                for (SimulationPayload.DiagramStation station : payload.diagramStations) {
                    int y = Math.round(toY(station.pos()));
                    gui.fill(plotX, y, plotX + plotWidth, y + 1, COLOR_STATION_LINE);
                }

                VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
                List<SimulationPayload.DiagramLine> lines = payload.diagramLines;
                for (int i = lines.size() - 1; i >= 0; i--) {
                    SimulationPayload.DiagramLine line = lines.get(i);
                    int color = line.phantom() ? COLOR_PHANTOM
                            : line.segments().size() == 1 && line.segments().get(0).size() == 2
                                    && line.segments().get(0).get(0).pos() == line.segments().get(0).get(1).pos()
                                    && line.segments().get(0).get(0).tick() == 0
                            ? COLOR_OBSTACLE
                            : PALETTE[(i - 1 + PALETTE.length) % PALETTE.length];
                    float thickness = line.phantom() ? 2.5f : 1.5f;
                    for (List<SimulationPayload.DiagramPoint> segment : line.segments())
                        for (int p = 1; p < segment.size(); p++)
                            clippedLine(buffer, matrix,
                                    segment.get(p - 1).tick(), segment.get(p - 1).pos(),
                                    segment.get(p).tick(), segment.get(p).pos(), thickness, color);
                }

                for (SimulationPayload.ConflictLine conflict : payload.conflicts) {
                    if (Float.isNaN(conflict.diagramPos()))
                        continue;
                    double tick = conflict.startTick();
                    if (tick < viewStart || tick > viewStart + viewSpan)
                        continue;
                    diamond(buffer, matrix, toX(tick), toY(conflict.diagramPos()), 3.5f,
                            CONFLICT_COLORS[conflict.type() % CONFLICT_COLORS.length]);
                }
            });
        }

        @Override
        public void renderFrontLayer(DLGuiGraphics graphics, double mouseX, double mouseY,
                                     Rectangle bounds) {
            super.renderFrontLayer(graphics, mouseX, mouseY, bounds);
            var gui = graphics.graphics();
            Font font = graphics.defaultFont();

            // Stations ascend in pos, so their label y positions descend.
            int lastLabelY = Integer.MAX_VALUE;
            for (SimulationPayload.DiagramStation station : payload.diagramStations) {
                int y = Math.round(toY(station.pos()));
                if (lastLabelY - y < 9)
                    continue;
                lastLabelY = y;
                String name = font.plainSubstrByWidth(station.name(), LABEL_WIDTH - 6);
                gui.drawString(font, name, plotX - 4 - font.width(name), y - 4,
                        COLOR_STATION_TEXT, false);
            }

            long gridStep = pickGridStep();
            long firstLine = (long) Math.ceil(viewStart / gridStep) * gridStep;
            int lastLabelX = Integer.MIN_VALUE;
            for (long tick = firstLine; tick <= viewStart + viewSpan; tick += gridStep) {
                int x = Math.round(toX(tick));
                String label = SimTimeFormat.time(payload, tick);
                int labelX = x - font.width(label) / 2;
                if (labelX <= lastLabelX + 8 || x < plotX || x > plotX + plotWidth)
                    continue;
                lastLabelX = labelX + font.width(label);
                gui.drawString(font, label, labelX, plotY + plotHeight + 4, COLOR_AXIS_TEXT, false);
            }

            renderHover(graphics, font);
        }

        private void renderHover(DLGuiGraphics graphics, Font font) {
            double localX = getLocalMouseX();
            double localY = getLocalMouseY();
            if (localX < plotX || localX > plotX + plotWidth
                    || localY < plotY || localY > plotY + plotHeight)
                return;

            List<Component> tooltip = new ArrayList<>();
            SimulationPayload.ConflictLine conflict = nearestConflict(localX, localY, 6);
            if (conflict != null) {
                tooltip.add(Component.translatable("realism.gui.simres.conflict."
                        + SimConflict.Type.values()[conflict.type()].name()));
                tooltip.add(Component.translatable("realism.gui.simres.conflict.window",
                        SimTimeFormat.time(payload, conflict.startTick()),
                        SimTimeFormat.time(payload, conflict.endTick())));
                tooltip.add(Component.translatable("realism.gui.simres.conflict.trains",
                        String.join(", ", conflict.trainNames())));
            } else {
                SimulationPayload.DiagramLine nearest = nearestLine(localX, localY, 5);
                if (nearest == null)
                    return;
                String yourTrain = Component.translatable("realism.gui.simres.your_train").getString();
                tooltip.add(TextUtils.text(nearest.phantom() ? yourTrain : nearest.trainName()));
                // Temporary debug aid for the hidden-categories config.
                if (!nearest.category().isEmpty())
                    tooltip.add(TextUtils.text(nearest.category())
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                tooltip.add(TextUtils.text(SimTimeFormat.time(payload, (long) tickAt(localX))));
            }
            GuiUtils.drawTooltip(graphics, font, (int) localX, (int) localY,
                    tooltip, (int) getWindowManager().getScreenWidth());
        }

        private SimulationPayload.ConflictLine nearestConflict(double x, double y, double maxPixels) {
            SimulationPayload.ConflictLine best = null;
            double bestDistance = maxPixels;
            for (SimulationPayload.ConflictLine conflict : payload.conflicts) {
                if (Float.isNaN(conflict.diagramPos()))
                    continue;
                double distance = Math.hypot(toX(conflict.startTick()) - x,
                        toY(conflict.diagramPos()) - y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = conflict;
                }
            }
            return best;
        }

        private SimulationPayload.DiagramLine nearestLine(double x, double y, double maxPixels) {
            SimulationPayload.DiagramLine best = null;
            double bestDistance = maxPixels;
            for (SimulationPayload.DiagramLine line : payload.diagramLines)
                for (List<SimulationPayload.DiagramPoint> segment : line.segments())
                    for (int p = 1; p < segment.size(); p++) {
                        double distance = segmentDistance(x, y,
                                toX(segment.get(p - 1).tick()), toY(segment.get(p - 1).pos()),
                                toX(segment.get(p).tick()), toY(segment.get(p).pos()));
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = line;
                        }
                    }
            return best;
        }

        /** ~70px between gridlines, snapped to a friendly game-time step. */
        private long pickGridStep() {
            double ticksPerMinute = payload.dayTimeRate > 0
                    ? 1000.0 / 60 / payload.dayTimeRate
                    : 20 * 60; // frozen day time: label real minutes instead
            for (int minutes : GRID_MINUTES) {
                long step = Math.max(1, Math.round(minutes * ticksPerMinute));
                if (step / viewSpan * plotWidth >= 70)
                    return step;
            }
            return Math.max(1, Math.round(GRID_MINUTES[GRID_MINUTES.length - 1] * ticksPerMinute));
        }

        /** A polyline piece, clipped to the visible time window in data space. */
        private void clippedLine(VertexConsumer buffer, Matrix4f matrix,
                                 double tick1, double pos1, double tick2, double pos2,
                                 float thickness, int color) {
            double viewEnd = viewStart + viewSpan;
            if (tick2 < viewStart || tick1 > viewEnd)
                return;
            if (tick1 < viewStart && tick2 > tick1) {
                pos1 += (pos2 - pos1) * (viewStart - tick1) / (tick2 - tick1);
                tick1 = viewStart;
            }
            if (tick2 > viewEnd && tick2 > tick1) {
                pos2 -= (pos2 - pos1) * (tick2 - viewEnd) / (tick2 - tick1);
                tick2 = viewEnd;
            }
            line(buffer, matrix, toX(tick1), toY(pos1), toX(tick2), toY(pos2), thickness, color);
        }
    }

    private static double segmentDistance(double px, double py,
                                          double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lengthSqr = dx * dx + dy * dy;
        double t = lengthSqr <= 0 ? 0
                : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSqr));
        return Math.hypot(ax + t * dx - px, ay + t * dy - py);
    }

    /** Both windings, so the GUI render type's culling can never drop a quad. */
    private static void line(VertexConsumer buffer, Matrix4f matrix,
                             float x1, float y1, float x2, float y2, float thickness, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0)
            return;
        float nx = (float) (-dy / length * thickness / 2);
        float ny = (float) (dx / length * thickness / 2);
        buffer.vertex(matrix, x1 + nx, y1 + ny, 0).color(color).endVertex();
        buffer.vertex(matrix, x1 - nx, y1 - ny, 0).color(color).endVertex();
        buffer.vertex(matrix, x2 - nx, y2 - ny, 0).color(color).endVertex();
        buffer.vertex(matrix, x2 + nx, y2 + ny, 0).color(color).endVertex();
        buffer.vertex(matrix, x2 + nx, y2 + ny, 0).color(color).endVertex();
        buffer.vertex(matrix, x2 - nx, y2 - ny, 0).color(color).endVertex();
        buffer.vertex(matrix, x1 - nx, y1 - ny, 0).color(color).endVertex();
        buffer.vertex(matrix, x1 + nx, y1 + ny, 0).color(color).endVertex();
    }

    private static void diamond(VertexConsumer buffer, Matrix4f matrix,
                                float x, float y, float half, int color) {
        buffer.vertex(matrix, x, y - half, 0).color(color).endVertex();
        buffer.vertex(matrix, x - half, y, 0).color(color).endVertex();
        buffer.vertex(matrix, x, y + half, 0).color(color).endVertex();
        buffer.vertex(matrix, x + half, y, 0).color(color).endVertex();
        buffer.vertex(matrix, x + half, y, 0).color(color).endVertex();
        buffer.vertex(matrix, x, y + half, 0).color(color).endVertex();
        buffer.vertex(matrix, x - half, y, 0).color(color).endVertex();
        buffer.vertex(matrix, x, y - half, 0).color(color).endVertex();
    }
}
