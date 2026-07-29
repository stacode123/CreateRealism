package net.Realism.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.Realism.content.graph.v2.RailCrossing;
import net.Realism.content.graph.v2.RailEdge;
import net.Realism.content.graph.v2.RailGraph;
import net.Realism.content.graph.v2.RailNode;
import net.Realism.content.graph.v2.RailSign;
import net.Realism.content.graph.v2.RailStation;
import net.Realism.content.graph.v2.SignalKind;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Top-down 2D viewer for a {@link RailGraph}: edges as polylines, markers for
 * junctions, signals, stations, crossings and Tramways signs. Drag to pan,
 * scroll to zoom, button to cycle dimensions. Opens centered on the track
 * position the player clicked, with the nearest edge highlighted.
 */
public class GraphMapScreen extends Screen {

    private static final int COLOR_BACKGROUND = 0xF0101018;
    private static final int COLOR_EDGE = 0xFFB0B0B8;
    private static final int COLOR_EDGE_HIGHLIGHT = 0xFF40FF70;
    private static final int COLOR_EDGE_HOVER = 0xFFFFE060;
    private static final int COLOR_EDGE_CONTEXT = 0xFF565662;
    private static final int COLOR_JUNCTION = 0xFFFFFFFF;
    private static final int COLOR_DEAD_END = 0xFFCC4444;
    private static final int COLOR_SIGNAL_ENTRY = 0xFFFF5555;
    private static final int COLOR_SIGNAL_CHAIN = 0xFFFFA030;
    private static final int COLOR_PORTAL = 0xFFCC55FF;
    private static final int COLOR_STATION = 0xFF55DDEE;
    private static final int COLOR_CROSSING = 0xFFFFE040;
    private static final int COLOR_SIGN = 0xFFDD88FF;

    private final RailGraph graph;
    /** Nearby separate networks, drawn dimmed and non-interactive. */
    private final List<RailGraph> contextGraphs;
    private final BlockPos focus;
    private final String focusDimension;

    private int dimensionIndex;
    private double centerX;
    private double centerZ;
    private double scale = 2.0;
    private int highlightedEdge = -1;
    private int hoveredEdge = -1;
    private int hoveredNode = -1;
    private double hoveredOffset = 0;
    private boolean showStationNames = true;
    private Button dimensionButton;
    private Button stationNamesButton;

    private static final String[] COMPASS = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };

    public GraphMapScreen(RailGraph graph, BlockPos focus, String dimension, @Nullable Vec3 clickedAxis,
                          List<RailGraph> contextGraphs) {
        super(Component.translatable("realism.map.title"));
        this.graph = graph;
        this.contextGraphs = contextGraphs;
        this.focus = focus;
        this.focusDimension = dimension;
        this.centerX = focus.getX() + 0.5;
        this.centerZ = focus.getZ() + 0.5;
        this.dimensionIndex = Math.max(0, graph.dimensions.indexOf(dimension));
        this.highlightedEdge = nearestEdge(centerX, centerZ, 12, this.dimensionIndex, clickedAxis);
        if (this.highlightedEdge < 0)
            this.highlightedEdge = nearestEdge(centerX, centerZ, 12, this.dimensionIndex, null);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width - 58, height - 26, 50, 20)
                .build());
        stationNamesButton = addRenderableWidget(Button.builder(stationNamesIcon(), b -> {
            showStationNames = !showStationNames;
            stationNamesButton.setMessage(stationNamesIcon());
            stationNamesButton.setTooltip(Tooltip.create(stationNamesLabel()));
        }).bounds(width - 28, 6, 20, 20).tooltip(Tooltip.create(stationNamesLabel())).build());
        if (graph.dimensions.size() > 1) {
            dimensionButton = addRenderableWidget(Button.builder(dimensionLabel(), b -> {
                dimensionIndex = (dimensionIndex + 1) % graph.dimensions.size();
                dimensionButton.setMessage(dimensionLabel());
                highlightedEdge = -1;
                centerOnDimension();
            }).bounds(8, height - 26, 140, 20).build());
        }
    }

    private Component stationNamesIcon() {
        return Component.literal("Aa")
                .withStyle(showStationNames ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
    }

    private Component stationNamesLabel() {
        return Component.translatable("realism.map.station_names",
                showStationNames ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private Component dimensionLabel() {
        return Component.translatable("realism.map.dimension", shortDimension(graph.dimensions.get(dimensionIndex)));
    }

    private static String shortDimension(String dimension) {
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    private boolean inDimension(RailEdge edge) {
        return graph.node(edge.from).dimension == dimensionIndex;
    }

    private void centerOnDimension() {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        boolean any = false;
        for (RailNode node : graph.nodes) {
            if (node.dimension != dimensionIndex)
                continue;
            any = true;
            minX = Math.min(minX, node.position.x);
            maxX = Math.max(maxX, node.position.x);
            minZ = Math.min(minZ, node.position.z);
            maxZ = Math.max(maxZ, node.position.z);
        }
        if (!any)
            return;
        centerX = (minX + maxX) / 2;
        centerZ = (minZ + maxZ) / 2;
        double spanX = Math.max(32, maxX - minX);
        double spanZ = Math.max(32, maxZ - minZ);
        scale = Math.min(3.0, Math.min((width - 40) / spanX, (height - 60) / spanZ));
    }

    private double toScreenX(double worldX) {
        return (worldX - centerX) * scale + width / 2.0;
    }

    private double toScreenY(double worldZ) {
        return (worldZ - centerZ) * scale + height / 2.0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        hoveredEdge = nearestEdgeOnScreen(mouseX, mouseY, 8);
        hoveredNode = nearestSignalNode(mouseX, mouseY, 7);

        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Line quads wind either way depending on direction; culling would
        // silently drop half of them.
        RenderSystem.disableCull();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // GuiGraphics.fill is batch-deferred and would flush on top of this
        // immediate-mode geometry, so the background lives in the same batch.
        rect(buffer, matrix, 0, 0, width, height, COLOR_BACKGROUND);

        String dimensionName = graph.dimensions.get(dimensionIndex);
        for (RailGraph contextGraph : contextGraphs) {
            int contextDimension = contextGraph.dimensions.indexOf(dimensionName);
            if (contextDimension < 0)
                continue;
            for (RailEdge edge : contextGraph.edges) {
                if (!canonical(edge) || edge.interDimensional
                        || contextGraph.node(edge.from).dimension != contextDimension)
                    continue;
                List<Vec3> shape = edge.shape;
                for (int i = 1; i < shape.size(); i++)
                    line(buffer, matrix,
                            toScreenX(shape.get(i - 1).x), toScreenY(shape.get(i - 1).z),
                            toScreenX(shape.get(i).x), toScreenY(shape.get(i).z),
                            1.5f, COLOR_EDGE_CONTEXT);
            }
        }

        for (RailEdge edge : graph.edges) {
            if (!canonical(edge) || !inDimension(edge) || edge.interDimensional)
                continue;
            int color = COLOR_EDGE;
            float thickness = 1.5f;
            if (edge.id == highlightedEdge || edge.oppositeId == highlightedEdge) {
                color = COLOR_EDGE_HIGHLIGHT;
                thickness = 3f;
            } else if (edge.id == hoveredEdge || edge.oppositeId == hoveredEdge) {
                color = COLOR_EDGE_HOVER;
                thickness = 2.5f;
            }
            List<Vec3> shape = edge.shape;
            for (int i = 1; i < shape.size(); i++)
                line(buffer, matrix,
                        toScreenX(shape.get(i - 1).x), toScreenY(shape.get(i - 1).z),
                        toScreenX(shape.get(i).x), toScreenY(shape.get(i).z),
                        thickness, color);
        }

        for (RailNode node : graph.nodes) {
            if (node.dimension != dimensionIndex)
                continue;
            double x = toScreenX(node.position.x);
            double y = toScreenY(node.position.z);
            switch (node.type) {
                case JUNCTION -> square(buffer, matrix, x, y, 2.5, COLOR_JUNCTION);
                case DEAD_END -> square(buffer, matrix, x, y, 2, COLOR_DEAD_END);
                case SIGNAL -> drawSignalNode(buffer, matrix, node, x, y);
                case PORTAL -> square(buffer, matrix, x, y, 3, COLOR_PORTAL);
            }
        }

        Set<UUID> drawnStations = new HashSet<>();
        Set<UUID> drawnCrossings = new HashSet<>();
        for (RailEdge edge : graph.edges) {
            if (!canonical(edge) || !inDimension(edge))
                continue;
            for (RailStation station : edge.stations) {
                if (!drawnStations.add(station.stationId()))
                    continue;
                Vec3 position = pointAlong(edge, station.offset());
                square(buffer, matrix, toScreenX(position.x), toScreenY(position.z), 3, COLOR_STATION);
            }
            for (RailCrossing crossing : edge.crossings) {
                if (!drawnCrossings.add(crossing.crossingId()))
                    continue;
                double x = toScreenX(crossing.position().x);
                double y = toScreenY(crossing.position().z);
                line(buffer, matrix, x - 3, y - 3, x + 3, y + 3, 1.5f, COLOR_CROSSING);
                line(buffer, matrix, x - 3, y + 3, x + 3, y - 3, 1.5f, COLOR_CROSSING);
            }
            for (RailSign sign : edge.signs)
                drawSignArrow(buffer, matrix, edge, sign);
            if (edge.oppositeId >= 0) {
                RailEdge opposite = graph.edges.get(edge.oppositeId);
                for (RailSign sign : opposite.signs)
                    drawSignArrow(buffer, matrix, opposite, sign);
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        if (showStationNames && scale >= 1.0) {
            Set<UUID> labeled = new HashSet<>();
            for (RailEdge edge : graph.edges) {
                if (!canonical(edge) || !inDimension(edge))
                    continue;
                for (RailStation station : edge.stations) {
                    if (station.name().isEmpty() || !labeled.add(station.stationId()))
                        continue;
                    Vec3 position = pointAlong(edge, station.offset());
                    // 45° off the approach direction, on its right-hand side;
                    // reverse-facing stations mirror to the other side of the
                    // track, with the text itself kept upright.
                    Vec3 facing = tangentAt(edge, station.offset());
                    if (!station.approachable())
                        facing = facing.scale(-1);
                    double angle = Math.toDegrees(Math.atan2(facing.z, facing.x)) + 45;
                    boolean flipped = false;
                    while (angle <= -90) {
                        angle += 180;
                        flipped = !flipped;
                    }
                    while (angle > 90) {
                        angle -= 180;
                        flipped = !flipped;
                    }
                    graphics.pose().pushPose();
                    graphics.pose().translate((float) toScreenX(position.x),
                            (float) toScreenY(position.z), 0);
                    graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) angle));
                    int xOffset = flipped ? -6 - font.width(station.name()) : 6;
                    graphics.drawString(font, station.name(), xOffset, -font.lineHeight / 2, COLOR_STATION);
                    graphics.pose().popPose();
                }
            }
        }

        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        String stats = graph.nodes.size() + " nodes, " + graph.edges.size() / 2 + " sections";
        graphics.drawString(font, stats, 8, 8, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("realism.map.hint"), 8, 20, 0x808080);
        if (!contextGraphs.isEmpty())
            graphics.drawString(font, Component.translatable("realism.map.other_networks"), 8, 32, 0x606068);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (hoveredNode >= 0)
            graphics.renderComponentTooltip(font, nodeTooltip(graph.node(hoveredNode)), mouseX, mouseY);
        else if (hoveredEdge >= 0)
            graphics.renderComponentTooltip(font, edgeTooltip(graph.edges.get(hoveredEdge)), mouseX, mouseY);
    }

    /**
     * A signal boundary as one small white tick plus a colored dot per
     * governed direction, offset toward the edge that direction enters —
     * bundled two-way signals show both dots.
     */
    private void drawSignalNode(BufferBuilder buffer, Matrix4f matrix, RailNode node, double x, double y) {
        square(buffer, matrix, x, y, 1.2, 0xFFE0E0E0);
        for (int edgeId : node.outgoing) {
            RailEdge edge = graph.edges.get(edgeId);
            if (edge.entrySignal == SignalKind.NONE)
                continue;
            Vec3 direction = edgeStartDirection(edge);
            square(buffer, matrix, x + direction.x * 4.5, y + direction.z * 4.5, 2,
                    edge.entrySignal == SignalKind.ENTRY ? COLOR_SIGNAL_ENTRY : COLOR_SIGNAL_CHAIN);
        }
    }

    /** A tram sign as an arrow pointing in the travel direction it governs. */
    private void drawSignArrow(BufferBuilder buffer, Matrix4f matrix, RailEdge edge, RailSign sign) {
        double x = toScreenX(sign.position().x);
        double y = toScreenY(sign.position().z);
        Vec3 direction = tangentAt(edge, sign.offset());
        square(buffer, matrix, x, y, 1.5, COLOR_SIGN);
        line(buffer, matrix, x, y, x + direction.x * 7, y + direction.z * 7, 2.5f, COLOR_SIGN);
    }

    private List<Component> edgeTooltip(RailEdge edge) {
        RailEdge opposite = edge.oppositeId >= 0 ? graph.edges.get(edge.oppositeId) : null;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("realism.map.edge.length", String.format("%.0f", edge.length)));
        // Curvature speeds are advisory only — Create trains ignore curve
        // radius; only Tramways signs impose real limits.
        double hereCap = edge.capAt(hoveredOffset);
        if (hereCap > 0)
            lines.add(Component.translatable("realism.map.edge.speed_here", String.format("%.0f", hereCap)));
        double curveMin = 0;
        for (double[] range : edge.capProfile)
            curveMin = curveMin == 0 ? range[2] : Math.min(curveMin, range[2]);
        if (curveMin > 0 && (hereCap == 0 || curveMin < hereCap - 0.5))
            lines.add(Component.translatable("realism.map.edge.speed_min", String.format("%.0f", curveMin)));
        addSignalLine(lines, edge);
        if (opposite != null)
            addSignalLine(lines, opposite);
        for (RailStation station : edge.stations)
            lines.add(Component.translatable("realism.map.edge.station", station.name()));
        if (!edge.crossings.isEmpty())
            lines.add(Component.translatable("realism.map.edge.crossings", edge.crossings.size()));
        addSignLines(lines, edge);
        if (opposite != null)
            addSignLines(lines, opposite);
        return lines;
    }

    private void addSignalLine(List<Component> lines, RailEdge edge) {
        if (edge.entrySignal == SignalKind.NONE)
            return;
        Vec3 direction = edgeStartDirection(edge);
        lines.add(Component.translatable("realism.map.edge.signal",
                Component.translatable("realism.map.signal." + edge.entrySignal.name().toLowerCase()),
                compass(direction.x, direction.z)));
    }

    private void addSignLines(List<Component> lines, RailEdge edge) {
        for (RailSign sign : edge.signs) {
            Vec3 direction = tangentAt(edge, sign.offset());
            lines.add(Component.translatable("realism.map.edge.sign",
                    String.format("%.0f", sign.capKmh()), compass(direction.x, direction.z)));
        }
    }

    private List<Component> nodeTooltip(RailNode node) {
        List<Component> lines = new ArrayList<>();
        for (int edgeId : node.outgoing) {
            RailEdge edge = graph.edges.get(edgeId);
            if (edge.entrySignal == SignalKind.NONE)
                continue;
            Vec3 direction = edgeStartDirection(edge);
            lines.add(Component.translatable("realism.map.node.signal",
                    Component.translatable("realism.map.signal." + edge.entrySignal.name().toLowerCase()),
                    compass(direction.x, direction.z)));
        }
        if (lines.isEmpty())
            lines.add(Component.translatable("realism.map.signal.none"));
        return lines;
    }

    private static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.floor(((angle + 360 + 22.5) % 360) / 45);
        return COMPASS[index % 8];
    }

    /** Normalized horizontal direction of an edge leaving its start node. */
    private static Vec3 edgeStartDirection(RailEdge edge) {
        List<Vec3> shape = edge.shape;
        if (shape.size() < 2)
            return Vec3.ZERO;
        return horizontalUnit(shape.get(1).subtract(shape.get(0)));
    }

    /** Normalized horizontal travel direction at an offset along an edge. */
    private static Vec3 tangentAt(RailEdge edge, double offset) {
        List<Vec3> shape = edge.shape;
        if (shape.size() < 2 || edge.length <= 0)
            return Vec3.ZERO;
        double shapeLength = 0;
        for (int i = 1; i < shape.size(); i++)
            shapeLength += shape.get(i - 1).distanceTo(shape.get(i));
        double target = Math.max(0, Math.min(1, offset / edge.length)) * shapeLength;
        double accumulated = 0;
        for (int i = 1; i < shape.size(); i++) {
            double segment = shape.get(i - 1).distanceTo(shape.get(i));
            if (segment > 0 && accumulated + segment >= target)
                return horizontalUnit(shape.get(i).subtract(shape.get(i - 1)));
            accumulated += segment;
        }
        return horizontalUnit(shape.get(shape.size() - 1).subtract(shape.get(shape.size() - 2)));
    }

    private static Vec3 horizontalUnit(Vec3 vector) {
        double length = Math.hypot(vector.x, vector.z);
        return length <= 0 ? Vec3.ZERO : new Vec3(vector.x / length, 0, vector.z / length);
    }

    /** Nearest signal node to a screen position, within maxPixels. */
    private int nearestSignalNode(double screenX, double screenY, double maxPixels) {
        int best = -1;
        double bestDistance = maxPixels;
        for (RailNode node : graph.nodes) {
            if (node.type != RailNode.NodeType.SIGNAL || node.dimension != dimensionIndex)
                continue;
            double distance = Math.hypot(toScreenX(node.position.x) - screenX,
                    toScreenY(node.position.z) - screenY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = node.id;
            }
        }
        return best;
    }

    /** Renders only one direction of each edge pair. */
    private boolean canonical(RailEdge edge) {
        return edge.oppositeId < 0 || edge.id < edge.oppositeId;
    }

    private static Vec3 pointAlong(RailEdge edge, double offset) {
        List<Vec3> shape = edge.shape;
        if (shape.isEmpty())
            return Vec3.ZERO;
        if (shape.size() == 1 || edge.length <= 0)
            return shape.get(0);
        double shapeLength = 0;
        for (int i = 1; i < shape.size(); i++)
            shapeLength += shape.get(i - 1).distanceTo(shape.get(i));
        double target = Math.max(0, Math.min(1, offset / edge.length)) * shapeLength;
        double accumulated = 0;
        for (int i = 1; i < shape.size(); i++) {
            double segment = shape.get(i - 1).distanceTo(shape.get(i));
            if (segment > 0 && accumulated + segment >= target) {
                double t = (target - accumulated) / segment;
                return shape.get(i - 1).lerp(shape.get(i), t);
            }
            accumulated += segment;
        }
        return shape.get(shape.size() - 1);
    }

    /** Nearest canonical edge to a world-space point, within maxWorldDistance. */
    private int nearestEdge(double worldX, double worldZ, double maxWorldDistance, int dimension) {
        return nearestEdge(worldX, worldZ, maxWorldDistance, dimension, null);
    }

    /**
     * Nearest canonical edge to a world-space point. With {@code alongAxis}
     * set, only edges locally parallel to that axis count — this is how a
     * click near a diamond crossing snaps to the line that was clicked
     * rather than the one crossing it.
     */
    private int nearestEdge(double worldX, double worldZ, double maxWorldDistance, int dimension,
                            @Nullable Vec3 alongAxis) {
        int best = -1;
        int bestSegment = -1;
        double bestT = 0;
        double bestDistance = maxWorldDistance;
        for (RailEdge edge : graph.edges) {
            if (!canonical(edge) || edge.interDimensional || graph.node(edge.from).dimension != dimension)
                continue;
            List<Vec3> shape = edge.shape;
            for (int i = 1; i < shape.size(); i++) {
                if (alongAxis != null) {
                    double dx = shape.get(i).x - shape.get(i - 1).x;
                    double dz = shape.get(i).z - shape.get(i - 1).z;
                    double length = Math.sqrt(dx * dx + dz * dz);
                    if (length > 1e-6
                            && Math.abs((dx * alongAxis.x + dz * alongAxis.z) / length) < 0.9)
                        continue;
                }
                double t = projectOnSegment(worldX, worldZ,
                        shape.get(i - 1).x, shape.get(i - 1).z, shape.get(i).x, shape.get(i).z);
                double cx = shape.get(i - 1).x + t * (shape.get(i).x - shape.get(i - 1).x) - worldX;
                double cz = shape.get(i - 1).z + t * (shape.get(i).z - shape.get(i - 1).z) - worldZ;
                double distance = Math.sqrt(cx * cx + cz * cz);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = edge.id;
                    bestSegment = i;
                    bestT = t;
                }
            }
        }
        if (best >= 0)
            hoveredOffset = offsetAlong(graph.edges.get(best), bestSegment, bestT);
        return best;
    }

    /** Distance along the edge of a point on shape segment i at fraction t. */
    private static double offsetAlong(RailEdge edge, int segment, double t) {
        List<Vec3> shape = edge.shape;
        double total = 0;
        double toPoint = 0;
        for (int i = 1; i < shape.size(); i++) {
            double length = shape.get(i - 1).distanceTo(shape.get(i));
            if (i < segment)
                toPoint += length;
            else if (i == segment)
                toPoint += length * t;
            total += length;
        }
        return total <= 0 ? 0 : toPoint / total * edge.length;
    }

    /** Nearest canonical edge to a screen-space point, within maxPixels. */
    private int nearestEdgeOnScreen(double screenX, double screenY, double maxPixels) {
        double worldX = (screenX - width / 2.0) / scale + centerX;
        double worldZ = (screenY - height / 2.0) / scale + centerZ;
        return nearestEdge(worldX, worldZ, maxPixels / scale, dimensionIndex);
    }

    private static double projectOnSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lengthSqr = dx * dx + dy * dy;
        return lengthSqr <= 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSqr));
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix,
                             double x1, double y1, double x2, double y2, float thickness, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0)
            return;
        double nx = -dy / length * thickness / 2;
        double ny = dx / length * thickness / 2;
        float a = (color >>> 24 & 0xFF) / 255f;
        float r = (color >>> 16 & 0xFF) / 255f;
        float g = (color >>> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        buffer.vertex(matrix, (float) (x1 + nx), (float) (y1 + ny), 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) (x1 - nx), (float) (y1 - ny), 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) (x2 - nx), (float) (y2 - ny), 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) (x2 + nx), (float) (y2 + ny), 0).color(r, g, b, a).endVertex();
    }

    private static void rect(BufferBuilder buffer, Matrix4f matrix,
                             double x1, double y1, double x2, double y2, int color) {
        float a = (color >>> 24 & 0xFF) / 255f;
        float r = (color >>> 16 & 0xFF) / 255f;
        float g = (color >>> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        buffer.vertex(matrix, (float) x1, (float) y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) x1, (float) y2, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) x2, (float) y2, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) x2, (float) y1, 0).color(r, g, b, a).endVertex();
    }

    private static void square(BufferBuilder buffer, Matrix4f matrix, double x, double y, double half, int color) {
        float a = (color >>> 24 & 0xFF) / 255f;
        float r = (color >>> 16 & 0xFF) / 255f;
        float g = (color >>> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        buffer.vertex(matrix, (float) (x - half), (float) (y - half), 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) (x - half), (float) (y + half), 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) (x + half), (float) (y + half), 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) (x + half), (float) (y - half), 0).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            centerX -= dragX / scale;
            centerZ -= dragY / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double factor = Math.pow(1.2, delta);
        double newScale = Math.max(0.05, Math.min(8.0, scale * factor));
        factor = newScale / scale;
        // Keep the world point under the cursor fixed while zooming.
        centerX += (mouseX - width / 2.0) * (1 - 1 / factor) / scale;
        centerZ += (mouseY - height / 2.0) * (1 - 1 / factor) / scale;
        scale = newScale;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
