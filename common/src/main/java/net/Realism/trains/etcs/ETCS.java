package net.Realism.trains.etcs;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.RealismExpectPlatform;
import net.Realism.compat.TramwaysCompat;
import net.Realism.config.RealismConfig;
import net.Realism.network.*;
import net.Realism.trains.*;
import net.Realism.trains.SignalFinder.SignalScanResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Stream;

import static net.Realism.network.SteerDirectionPacket.KeyPressType.*;
import static net.Realism.trains.etcs.ETCSsounds.*;
import static net.Realism.trains.etcs.ETCStools.optimizedRenderSpeedCurve;
import static net.Realism.trains.etcs.ETCStools.renderElement;


public class ETCS {
    boolean backward = false;
    public Train train;
    public SignalScanResult previousSignalScanResult;
    boolean previousBackward;

    private double speedLimit = -1; // -1 means no limit
    private double distanceToSignal = 0;
    // Unused since the needle became a render-time calculation; still carried in the
    // sync packet and NBT so mixed 0.6.x clients stay wire-compatible.
    private float needleRotationDegrees = 0;

    // Last update timestamp to handle client/server synchronization
    private long lastUpdateTime = 0;

    private boolean needsSync = false;
    private long lastSyncTime = 0;
    private static final int SYNC_INTERVAL_MS = 200; // Sync every 200ms

    private int curvedropping;
    private int diffrenceCounter = 0;
    private int zoom = 1;
    // Flag to trigger info sound when a new route is detected
    private boolean pendingBeepSound = false;

    // Braking distances
    private double cachedEmergencyBrakingDist;
    private double cachedServiceBrakingDist;
    private double cachedWarningBrakingDist;
    private boolean cachedCurveIsDropping = false;
    private double cachedAllowedSpeed = -1;
    private List<SpeedLimit> cachedSpeedLimits = new ArrayList<>();
    private double distanceToBrakingPoint = 0;

    // Route guidance display state (server-computed, synced to clients).
    private double cachedDistToDestination = -1;
    private boolean cachedDestBehind = false;
    private RouteTurnFinder.TurnDir cachedTurnDir = RouteTurnFinder.TurnDir.NONE;
    private double cachedTurnDistance = -1;
    private RouteTurnFinder.TurnDir cachedTurn2Dir = RouteTurnFinder.TurnDir.NONE;
    private double cachedTurn2Distance = -1;

    // DMI settings pages: drawn in place of the route overview / target panel while open
    // (focused cab screen only, client side).
    private boolean routeSettingsOpen = false;
    /** 0 = route guidance menu, 1 = station list, 2 = train settings. */
    private int routeSettingsPage = 0;
    private int routeSettingsIndex = 0;
    private int routeSettingsScroll = 0;
    private List<String> routeSettingsStations = List.of();
    private List<String> routeSettingsFiltered = List.of();
    private String routeSettingsQuery = "";
    private static final int ROUTE_MENU_ITEMS = 3;
    private static final int ROUTE_SETTINGS_ROWS = 14;
    // Settings page geometry, in DMI texture units. Rendering and mouse hit-testing share
    // these so a click always lands on the row the driver sees.
    private static final int SETTINGS_X = 279;
    private static final int SETTINGS_Y = 14;
    private static final int SETTINGS_W = 212;
    private static final int SETTINGS_H = 339;
    private static final int ROUTE_MENU_TOP = SETTINGS_Y + 32;
    private static final int ROUTE_MENU_ROW_H = 22;
    private static final int STATION_LIST_TOP = SETTINGS_Y + 56;
    private static final int STATION_ROW_H = 18;
    private static final int TRAIN_SETTINGS_TOP = SETTINGS_Y + 34;
    private static final int TRAIN_SETTINGS_ROW_H = 22;

    // Train settings page (page 2), edited as a draft and committed by ENTER.
    private TrainSettings trainSettingsDraft;
    private boolean trainSettingsEditing = false;
    private static final int TS_TILT_MODE = 0;
    private static final int TS_TILT_DIRECTION = 1;
    private static final int TS_MIN_SPEED = 2;
    private static final int TS_MAX_TILT = 3;
    private static final int TS_TILT_INTENSITY = 4;
    private static final int TS_ACCEL_MODE = 5;
    private static final int TS_ACCEL_VALUE = 6;

    private boolean plusKeyWasDown = false;
    private boolean minusKeyWasDown = false;
    private long lastKeyPressTime = 0;
    private static final long KEY_COOLDOWN_MS = 300;

    private int trackspeedlimit = 300;
    public ETCS(Train train) {
        this.train = train;
    }

    public boolean toUpdate = false;

    /**
     * Update method to be called during game logic/tick updates.
     * This handles all calculations independent of rendering.
     */
    public void update() {
        // Record update time
        this.lastUpdateTime = System.currentTimeMillis();

        // Determine train direction
        if (train.speed < 0) {
            previousBackward = backward;
            backward = true;
        } else if (train.speed > 0) {
            previousBackward = backward;
            backward = false;
        }
        ReciveKeys();



        // When the driver has set a destination station, scan along that route and stop there.
        RouteGuidance guidance = RouteGuidanceManager.getForTrain(train);
        if (guidance != null) {
            RouteGuidanceManager.ensurePath(train, guidance);
            if (!guidance.valid) guidance = null;
        }
        if (guidance != null) {
            cachedDistToDestination = Math.abs(guidance.distanceToDestination);
            cachedDestBehind = guidance.destinationBehindTrain;
            java.util.List<RouteTurnFinder.TurnInfo> turns = RouteTurnFinder.findNextTurns(train, guidance, backward);
            if (!turns.isEmpty()) {
                cachedTurnDir = turns.get(0).dir();
                cachedTurnDistance = turns.get(0).distance();
            } else {
                cachedTurnDir = RouteTurnFinder.TurnDir.NONE;
                cachedTurnDistance = -1;
            }
            if (turns.size() > 1) {
                cachedTurn2Dir = turns.get(1).dir();
                cachedTurn2Distance = turns.get(1).distance();
            } else {
                cachedTurn2Dir = RouteTurnFinder.TurnDir.NONE;
                cachedTurn2Distance = -1;
            }
        } else {
            cachedDistToDestination = -1;
            cachedDestBehind = false;
            cachedTurnDir = RouteTurnFinder.TurnDir.NONE;
            cachedTurnDistance = -1;
            cachedTurn2Dir = RouteTurnFinder.TurnDir.NONE;
            cachedTurn2Distance = -1;
        }
        SignalScanResult s = guidance != null
                ? SignalFinder.scanAheadForSignals(train, (double) 4000 /zoom, backward, guidance)
                : SignalFinder.scanAheadForSignals(train, (double) 4000 /zoom, backward);

        // Logic to decide whether to use previous scan result
        if (previousSignalScanResult != null) {
            if (diffrenceCounter<10){
            if (((Math.abs(s.getDistanceToClosestOccupiedSignal() - previousSignalScanResult.getDistanceToClosestOccupiedSignal()) > 30)
                    && backward == previousBackward)) {

                diffrenceCounter += 1;
                s = previousSignalScanResult;
            }}
            else {
                pendingBeepSound = true;
                diffrenceCounter = 0;
            }
        }
        this.previousSignalScanResult = s;
        this.distanceToSignal = s.getDistanceToClosestOccupiedSignal();

        // Process tram signs for speed limits
        if (RealismExpectPlatform.isModLoaded("tramways")) {
            if (trackspeedlimit==0){
                trackspeedlimit = 20;
            }
            cachedSpeedLimits = TramwaysCompat.processTramSigns(s,train.maxSpeed()*20*3.6f,train);
        } else {
            // Reset speed limits when Tramways isn't loaded
            cachedSpeedLimits = new ArrayList<>();
            trackspeedlimit = 300;
        }

        float distance = (float) s.getDistanceToClosestOccupiedSignal();
        float maxDeceleration = train.acceleration() * 2f * 400;
        updateBrakingDistances(trackspeedlimit,0);
        speedLimit = Math.min(calculateAllowedSpeed(distance, maxDeceleration),trackspeedlimit);
        updateBrakingDistances((int) (train.speed*20*3.6f),0);
        if (cachedAllowedSpeed > speedLimit){
            cachedCurveIsDropping = true;
            curvedropping =0;
        }
        else {
            curvedropping+=1;
            if (curvedropping >10){
            cachedCurveIsDropping = false;}
        }
        cachedAllowedSpeed = speedLimit;

        // Needle rotation is computed client-side at render time, from the viewer's
        // own km/h-vs-mph setting - computing it here would read the client config
        // on the dedicated server, where that spec is never loaded.

        // Update braking distances


        // After calculations are done
        markDirty();

        // Call sync at the end of update
        // Save state to train's NBT

        // Send data to clients if needed
        syncToClients();
    }

    public void start(){
        toUpdate = true;
        // A different train means a different drive: start a fresh message archive.
        ETCSMessages.beginDrive(train.id);
        // A sync received before the drive began can already have flagged the destination
        // behind; re-post the sticky advisory so it is visible for this drive.
        if (cachedDestBehind) {
            ETCSMessages.push("behind", ETCSMessages.Severity.ADVISORY,
                    Component.translatable("realism.etcs.msg.dest_behind").getString(), 0);
        }
        RNetworking.sendToServer(new ETCSStartStopPacket(toUpdate,train.id));
    }

    public void stop(){
        toUpdate = false;
        RNetworking.sendToServer(new ETCSStartStopPacket(toUpdate,train.id));
    }
    /**
     * Render method to be called during render cycle (HUD mode).
     * Only contains rendering code, using data calculated in update().
     * <p>
     * While the focused ETCS cab screen ({@link net.Realism.gui.ETCSGui#isOpen()}) is open,
     * the HUD copy is paused so there is exactly one visible driver-machine interface -
     * the focused one draws the very same shared render below.
     */
    public void render(GuiGraphics graphics) {
        if (net.Realism.gui.ETCSGui.isOpen()) {
            return;
        }
        double ScaleFactor = RealismConfig.CLIENT.ETCSSize.get();
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int xPos = (int) ((screenWidth/ScaleFactor) - 536);  // 10 scaled pixels from right edge
        int yPos = 0;
        sendKeysToServer();
        renderDMI(graphics, xPos, yPos, ScaleFactor);
    }

    /**
     * Focused mode: the identical DMI, drawn at the given origin and scale (e.g. centered
     * inside the interactive cab window). No input polling - the window routes keyboard
     * and mouse input through the same {@link #sendKeysToServer()}/{@link net.Realism.gui.ETCSGui} actions.
     */
    public void renderFocused(GuiGraphics graphics, int xPos, int yPos, double scaleFactor) {
        renderDMI(graphics, xPos, yPos, scaleFactor);
    }

    /**
     * The one and only DMI drawing routine used by both the HUD overlay and the focused
     * cab screen, so both always show the same thing. Origin is given in texture units
     * (536x401); the pose is scaled by {@code scaleFactor} inside.
     */
    private void renderDMI(GuiGraphics graphics, int xPos, int yPos, double ScaleFactor) {
        // Load the most current data before rendering
        PoseStack posestack = graphics.pose();
        posestack.pushPose();
        posestack.scale((float) ScaleFactor, (float) ScaleFactor, (float) ScaleFactor);

        if (zoom == 0){
            zoom = 1;
        }
        // Render the ETCS panel background
        String zoomTexture = String.format("realism:textures/etcszoom%d.png", zoom);
        RenderSystem.setShaderTexture(0, ResourceLocation.tryBuild("realism", "textures/etcszoom" + zoom + ".png"));
        graphics.blit(ResourceLocation.tryBuild("realism", "textures/etcszoom" + zoom + ".png"),
                xPos, yPos,   // screen position
                0, 0,         // texture position (top left of texture)
                536, 401,   // width and height to render
                536, 401);  // texture width and height

        // Render the speedometer needle
        posestack.pushPose();
        posestack.translate(xPos + 145, yPos + 125, 0);

        float rotationRadians = ETCStools.calculateNeedleRotation(train.speed) * (float)(Math.PI / 180);
        posestack.mulPose(Axis.ZP.rotation(rotationRadians));
        posestack.translate(-24, -89, 0);

        // Render the image
        RenderSystem.setShaderTexture(0, ResourceLocation.tryBuild("realism", "textures/etcshand.png"));
        graphics.blit(ResourceLocation.tryBuild("realism", "textures/etcshand.png"),
                0, 0,   // screen position
                0, 0,         // texture position (top left of texture)
                49, 112,   // width and height to render
                49, 112);
        posestack.popPose();
        //Render the speedometer label
        posestack.pushPose();
        posestack.translate(xPos + 21, yPos + 221, 0);
        if(RealismConfig.CLIENT.ETCSMPH.get()) {
            RenderSystem.setShaderTexture(0, ResourceLocation.tryBuild("realism", "textures/mph.png"));
            graphics.blit(ResourceLocation.tryBuild("realism", "textures/mph.png"),
                    0, 0,   // screen position
                    0, 0,         // texture position (top left of texture)
                    27, 15,   // width and height to render
                    27, 15);

        }
        else {
            RenderSystem.setShaderTexture(0,ResourceLocation.tryBuild("realism", "textures/kph.png"));
            graphics.blit(ResourceLocation.tryBuild("realism", "textures/kph.png"),
                    0, 0,   // screen position
                    0, 0,         // texture position (top left of texture)
                    27, 15,   // width and height to render
                    27, 15);
        }
        posestack.popPose();
        // Play info sound when a new route is detected
        if (pendingBeepSound) {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level != null) {
                playBeepSound();
            }
            pendingBeepSound = false;
        }

        //graphics.drawString(mc.font,String.valueOf((int)train.speed*20*3.6),xPos + 156,yPos+124,0x294A6DFF);

        // Render ETCS limits display
        renderETCSlimits(graphics, posestack, xPos + 10, yPos + 10);
        if (!routeSettingsOpen) {
            renderOverviewItems(graphics, xPos, yPos, zoom);
            renderTurnMarkers(graphics, xPos, yPos, zoom);
        }
        renderBrakingCurve(graphics, posestack, xPos-12, yPos+5);
        renderGuidanceCells(graphics, xPos, yPos);
        renderMessages(graphics, xPos, yPos);
        renderTargetPanel(graphics, posestack, xPos, yPos);
        if (routeSettingsOpen) {
            renderRouteSettings(graphics, xPos, yPos);
        }
        updateWarningLoop();
        posestack.popPose();
    }

    /**
     * Route validity lamp beside the kph/mph label (cell x123-150): amber while a change is
     * pending, green once the server confirmed the route. All other cells stay dark.
     */
    private void renderGuidanceCells(GuiGraphics graphics, int xPos, int yPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        RouteGuidance guidance = RouteGuidanceManager.getClient(mc.player.getUUID());
        if (!guidance.hasDestination()) return;

        int lamp = RouteGuidanceManager.isClientPending(mc.player.getUUID()) ? 0xFFFFB300 : 0xFF40C060;
        graphics.fill(xPos + 131, yPos + 224, xPos + 142, yPos + 235, lamp);
    }

    /**
     * Turn ticks on the route overview: large amber chevrons in the left gutter of the strip,
     * at the live scale position of the next turn, dim chevron for the one after.
     * No distance text. Skipped beyond the scale range.
     */
    private void renderTurnMarkers(GuiGraphics graphics, int xPos, int yPos, int zoom) {
        double max = 4000.0 / zoom;
        renderTurnMarker(graphics, xPos, yPos, zoom, max, cachedTurnDir, cachedTurnDistance, true);
        renderTurnMarker(graphics, xPos, yPos, zoom, max, cachedTurn2Dir, cachedTurn2Distance, false);
    }

    private void renderTurnMarker(GuiGraphics graphics, int xPos, int yPos, int zoom, double max,
                                  RouteTurnFinder.TurnDir dir, double distance, boolean primary) {
        if (dir != RouteTurnFinder.TurnDir.LEFT && dir != RouteTurnFinder.TurnDir.RIGHT) return;
        double d = deadReckoned(distance);
        if (d < 0 || d > max) return;
        int pixelPos = calculateDistancePixelPosition(d, zoom);
        String arrow = dir == RouteTurnFinder.TurnDir.LEFT ? "<" : ">";
        int color = primary ? 0xFFFFB300 : 0xFF9A7300;

        Font font = Minecraft.getInstance().font;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // Left gutter of the route overview, vertically centered on the marker position.
        pose.translate(xPos + 356, yPos + 235 - pixelPos - 9, 0f);
        pose.scale(2f, 2f, 1f);
        graphics.drawString(font, arrow, 0, 0, color, false);
        pose.popPose();
    }

    /**
     * Onboard message window (message block x61-243, y246-353): severity-colored border,
     * HH:MM timestamp and wrapped text of the newest queued message. Dark when empty.
     */
    private void renderMessages(GuiGraphics graphics, int xPos, int yPos) {
        ETCSMessages.Entry entry = ETCSMessages.current();
        if (entry == null) return;

        int x0 = xPos + 61;
        int y0 = yPos + 246;
        int x1 = xPos + 243;
        int y1 = yPos + 353;
        int border = entry.severity().border;
        graphics.fill(x0, y0, x1, y0 + 1, border);
        graphics.fill(x0, y1 - 1, x1, y1, border);
        graphics.fill(x0, y0, x0 + 1, y1, border);
        graphics.fill(x1 - 1, y0, x1, y1, border);

        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, entry.time(), x0 + 6, y0 + 6, 0xFF9AA0AA, false);
        var lines = font.split(Component.literal(entry.text()), x1 - x0 - 12);
        int y = y0 + 22;
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x0 + 6, y, 0xFFFFFFFF, false);
            y += 12;
        }
        int archived = ETCSMessages.archiveCount();
        if (archived > 1) {
            String more = ETCSMessages.displayPosition() + "/" + archived;
            graphics.drawString(font, more, x1 - 6 - font.width(more), y1 - 14, 0xFF9AA0AA, false);
        }
    }

    /**
     * Target station panel under the route overview (x285-430, y255-355): station name,
     * live distance countdown and next-turn countdown. Dark when no destination is set.
     * Distances are dead-reckoned from the last sync using current speed.
     */
    private void renderTargetPanel(GuiGraphics graphics, PoseStack posestack, int xPos, int yPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cachedDistToDestination < 0) return;
        RouteGuidance guidance = RouteGuidanceManager.getClient(mc.player.getUUID());
        if (!guidance.hasDestination()) return;

        Font font = mc.font;
        String name = guidance.destinationName;
        while (name.length() > 1 && font.width(name + "...") > 128) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.length() < guidance.destinationName.length()) name += "...";
        graphics.drawString(font, name, xPos + 291, yPos + 261, 0xFFFFB300, false);

        posestack.pushPose();
        posestack.translate(xPos + 291, yPos + 283, 0);
        posestack.scale(1.6f, 1.6f, 1.6f);
        graphics.drawString(font, formatDistance(deadReckoned(cachedDistToDestination)), 0, 0, 0xFFFFFFFF, false);
        posestack.popPose();

        if (cachedTurnDir == RouteTurnFinder.TurnDir.LEFT || cachedTurnDir == RouteTurnFinder.TurnDir.RIGHT) {
            String arrow = cachedTurnDir == RouteTurnFinder.TurnDir.LEFT ? "<" : ">";
            graphics.drawString(font, arrow + " " + formatDistance(deadReckoned(cachedTurnDistance)),
                    xPos + 291, yPos + 322, 0xFFFFB300, false);
        }
    }

    /** Distance minus travel since the last sync, so the countdown stays live between packets. */
    private double deadReckoned(double syncedDistance) {
        if (syncedDistance < 0 || train == null) return syncedDistance;
        double speedMs = Math.abs(train.speed) * 20.0;
        double elapsed = (System.currentTimeMillis() - lastUpdateTime) / 1000.0;
        return Math.max(0, syncedDistance - speedMs * Math.min(elapsed, 5));
    }

    private static String formatDistance(double meters) {
        if (meters < 0) return "--";
        if (meters < 1000) return (int) meters + " m";
        return String.format("%.1f km", meters / 1000.0);
    }

    // ------------------------------------------------------------------ DMI settings pages

    /** Opens the in-DMI route guidance settings, replacing the route overview. */
    public void openRouteSettings() {
        routeSettingsOpen = true;
        routeSettingsPage = 0;
        routeSettingsIndex = 0;
    }

    /** Opens the train settings page in the same right-hand panel as route guidance. */
    public void openTrainSettingsPage() {
        TrainSettings current = currentTrainSettings();
        trainSettingsDraft = current != null ? current.copy() : new TrainSettings();
        trainSettingsEditing = false;
        routeSettingsOpen = true;
        routeSettingsPage = 2;
        routeSettingsIndex = 0;
    }

    public void closeRouteSettings() {
        routeSettingsOpen = false;
        routeSettingsPage = 0;
        trainSettingsEditing = false;
        trainSettingsDraft = null;
    }

    public boolean isRouteSettingsOpen() {
        return routeSettingsOpen;
    }

    public boolean isRouteStationListOpen() {
        return routeSettingsOpen && routeSettingsPage == 1;
    }

    public boolean isTrainSettingsEditing() {
        return routeSettingsOpen && routeSettingsPage == 2 && trainSettingsEditing;
    }

    /** Moves the menu/list selection (negative = up, positive = down), wrapping at the ends. */
    public void routeSettingsMove(int delta) {
        if (!routeSettingsOpen) return;
        if (routeSettingsPage == 2) {
            trainSettingsMove(delta);
            return;
        }
        int size = routeSettingsPage == 0 ? ROUTE_MENU_ITEMS : routeSettingsFiltered.size();
        if (size <= 0) return;
        routeSettingsIndex = Math.floorMod(routeSettingsIndex + delta, size);
        ensureRouteSettingsVisible();
    }

    /** ENTER on the focused screen: open the station list, clear the route, or apply a station. */
    public void routeSettingsAccept() {
        if (!routeSettingsOpen) return;
        if (routeSettingsPage == 2) {
            trainSettingsAccept();
            return;
        }
        if (routeSettingsPage == 0) {
            if (routeSettingsIndex == 0) {
                routeSettingsStations = availableStations();
                routeSettingsQuery = "";
                routeSettingsFiltered = routeSettingsStations;
                RouteGuidance guidance = currentGuidance();
                int current = guidance == null ? -1 : routeSettingsFiltered.indexOf(guidance.destinationName);
                routeSettingsIndex = Math.max(0, current);
                routeSettingsScroll = 0;
                routeSettingsPage = 1;
                ensureRouteSettingsVisible();
            } else if (routeSettingsIndex == 1) {
                applyRouteDestination("");
            } else {
                closeRouteSettings();
            }
            return;
        }
        if (routeSettingsFiltered.isEmpty()) return;
        applyRouteDestination(routeSettingsFiltered.get(routeSettingsIndex));
        closeRouteSettings();
    }

    /** Typing on the station list filters it. */
    public void routeSettingsType(char codePoint) {
        if (!isRouteStationListOpen() || codePoint < 32 || codePoint == 127) return;
        routeSettingsQuery += codePoint;
        refilterStations();
    }

    public void routeSettingsBackspace() {
        if (!isRouteStationListOpen() || routeSettingsQuery.isEmpty()) return;
        routeSettingsQuery = routeSettingsQuery.substring(0, routeSettingsQuery.length() - 1);
        refilterStations();
    }

    private void refilterStations() {
        String query = routeSettingsQuery.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            routeSettingsFiltered = routeSettingsStations;
        } else {
            List<String> matches = new ArrayList<>();
            for (String name : routeSettingsStations) {
                if (name.toLowerCase(Locale.ROOT).contains(query)) matches.add(name);
            }
            routeSettingsFiltered = matches;
        }
        routeSettingsIndex = 0;
        routeSettingsScroll = 0;
    }

    private void ensureRouteSettingsVisible() {
        if (routeSettingsPage != 1) return;
        int size = routeSettingsFiltered.size();
        int maxScroll = Math.max(0, size - ROUTE_SETTINGS_ROWS);
        routeSettingsScroll = Math.max(0, Math.min(routeSettingsScroll, maxScroll));
        if (routeSettingsIndex < routeSettingsScroll) {
            routeSettingsScroll = routeSettingsIndex;
        } else if (routeSettingsIndex >= routeSettingsScroll + ROUTE_SETTINGS_ROWS) {
            routeSettingsScroll = routeSettingsIndex - ROUTE_SETTINGS_ROWS + 1;
        }
    }

    // ------------------------------------------------------------------ train settings

    private static TrainSettings currentTrainSettingsFor(Train train) {
        return train instanceof ITrainInterface rtrain ? rtrain.realism$getSettings() : new TrainSettings();
    }

    private TrainSettings currentTrainSettings() {
        return currentTrainSettingsFor(train);
    }

    private List<Integer> trainSettingRows() {
        TrainSettings s = trainSettingsDraft;
        List<Integer> rows = new ArrayList<>();
        rows.add(TS_TILT_MODE);
        rows.add(TS_TILT_DIRECTION);
        if (s != null && s.ts == TrainSettings.tiltSetting.CUSTOM) {
            rows.add(TS_MIN_SPEED);
            rows.add(TS_MAX_TILT);
            rows.add(TS_TILT_INTENSITY);
        }
        rows.add(TS_ACCEL_MODE);
        if (s != null && s.as == TrainSettings.accelerationSetting.CUSTOM) {
            rows.add(TS_ACCEL_VALUE);
        }
        return rows;
    }

    private void trainSettingsMove(int delta) {
        List<Integer> rows = trainSettingRows();
        if (rows.isEmpty()) return;
        if (routeSettingsIndex >= rows.size()) routeSettingsIndex = 0;
        if (trainSettingsEditing) {
            adjustTrainSetting(rows.get(routeSettingsIndex), delta);
            return;
        }
        routeSettingsIndex = Math.floorMod(routeSettingsIndex + delta, rows.size());
    }

    private void trainSettingsAccept() {
        List<Integer> rows = trainSettingRows();
        if (rows.isEmpty()) return;
        if (routeSettingsIndex >= rows.size()) routeSettingsIndex = 0;

        if (trainSettingsEditing) {
            trainSettingsEditing = false;
            saveTrainSettings(trainSettingsDraft);
            return;
        }
        if (trainSettingsDraft == null) return;

        int row = rows.get(routeSettingsIndex);
        switch (row) {
            case TS_TILT_MODE -> {
                trainSettingsDraft.ts = nextTiltMode(trainSettingsDraft.ts);
                clampTrainSettingsIndex();
                saveTrainSettings(trainSettingsDraft);
            }
            case TS_ACCEL_MODE -> {
                trainSettingsDraft.as = nextAccelerationMode(trainSettingsDraft.as);
                clampTrainSettingsIndex();
                saveTrainSettings(trainSettingsDraft);
            }
            case TS_TILT_DIRECTION -> {
                trainSettingsDraft.Inside = !trainSettingsDraft.Inside;
                saveTrainSettings(trainSettingsDraft);
            }
            case TS_MIN_SPEED, TS_MAX_TILT, TS_TILT_INTENSITY, TS_ACCEL_VALUE ->
                    trainSettingsEditing = true;
            default -> { }
        }
    }

    /** Abandons a numeric edit and reloads the values the train currently has. */
    public void cancelTrainSettingsEdit() {
        if (!trainSettingsEditing) return;
        trainSettingsEditing = false;
        TrainSettings current = currentTrainSettings();
        if (current != null) trainSettingsDraft = current.copy();
    }

    private void clampTrainSettingsIndex() {
        int size = trainSettingRows().size();
        if (size > 0) routeSettingsIndex = Math.min(routeSettingsIndex, size - 1);
    }

    private void adjustTrainSetting(int row, int delta) {
        if (trainSettingsDraft == null || delta == 0) return;
        switch (row) {
            case TS_MIN_SPEED -> trainSettingsDraft.customMinSpeed =
                    clamp(Math.round(trainSettingsDraft.customMinSpeed + delta * 5.0), 0, 300);
            case TS_MAX_TILT -> trainSettingsDraft.customMaxTilt =
                    (float) clamp(Math.round((trainSettingsDraft.customMaxTilt + delta * 0.5) * 10.0) / 10.0, 0, 90);
            case TS_TILT_INTENSITY -> trainSettingsDraft.customTiltIntensity =
                    (float) clamp(Math.round((trainSettingsDraft.customTiltIntensity + delta * 0.1) * 10.0) / 10.0, 0, 20);
            case TS_ACCEL_VALUE -> trainSettingsDraft.customAcceleration =
                    clamp(Math.round((trainSettingsDraft.customAcceleration + delta * 0.1) * 10.0) / 10.0, 0,
                            TrainSettingsLimits.maxCustomAcceleration());
            default -> { }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static TrainSettings.tiltSetting nextTiltMode(TrainSettings.tiltSetting mode) {
        TrainSettings.tiltSetting[] values = TrainSettings.tiltSetting.values();
        return values[(mode.ordinal() + 1) % values.length];
    }

    private static TrainSettings.accelerationSetting nextAccelerationMode(TrainSettings.accelerationSetting mode) {
        TrainSettings.accelerationSetting[] values = TrainSettings.accelerationSetting.values();
        return values[(mode.ordinal() + 1) % values.length];
    }

    private void saveTrainSettings(TrainSettings settings) {
        if (settings == null || train == null || !(train instanceof ITrainInterface rtrain)) return;
        TrainSettings committed = settings.copy();
        rtrain.realism$setSettings(committed);
        RNetworking.sendToServer(new TrainSettingsSavePacket(committed, train.id));
    }

    private static RouteGuidance currentGuidance() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : RouteGuidanceManager.getClient(mc.player.getUUID());
    }

    /** Optimistic client update + save packet, shared by the settings page and any future UI. */
    private static void applyRouteDestination(String destinationName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID playerId = mc.player.getUUID();
        RouteGuidance before = RouteGuidanceManager.getClient(playerId);
        boolean hadDestination = before.hasDestination();
        // Capture before setClient: it mutates the shared RouteGuidance instance in place.
        String previousDestination = before.destinationName;
        String destination = destinationName == null ? "" : destinationName.trim();

        RouteGuidanceManager.setClient(playerId, destination);
        RouteGuidanceManager.markClientPending(playerId);

        if (!destination.isEmpty()) {
            if (!hadDestination) {
                ETCSMessages.push("route", ETCSMessages.Severity.INFO,
                        Component.translatable("realism.etcs.msg.route_found", destination).getString(), 4000);
            } else if (!destination.equals(previousDestination)) {
                ETCSMessages.push("route", ETCSMessages.Severity.INFO,
                        Component.translatable("realism.etcs.msg.route_changed", destination).getString(), 4000);
            }
        } else if (hadDestination) {
            ETCSMessages.push("route", ETCSMessages.Severity.INFO,
                    Component.translatable("realism.etcs.msg.guidance_cleared").getString(), 3000);
        }
        RNetworking.sendToServer(new RouteGuidanceSavePacket(destination));
    }

    /** Every station name known to the client, sorted case-insensitively. */
    private static List<String> availableStations() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        try {
            for (TrackGraph graph : Create.RAILWAYS.sided(null).trackNetworks.values()) {
                for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                    if (station.name != null && !station.name.isBlank()) names.add(station.name);
                }
            }
        } catch (Throwable ignored) {
            // The client-side graph may not be synced yet; show an empty list instead.
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort((a, b) -> a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT)));
        return sorted;
    }

    /**
     * Settings pages drawn over the whole right-hand glass area of the DMI (texture x279-491,
     * y14-353), replacing both the route overview and the target panel. Rows can be clicked
     * directly; the bezel keys remain the secondary control (up/down move the selection,
     * ENTER accepts, MAIN returns).
     */
    private void renderRouteSettings(GuiGraphics graphics, int xPos, int yPos) {
        final int x0 = xPos + SETTINGS_X;
        final int y0 = yPos + SETTINGS_Y;
        final int w = SETTINGS_W;
        final int h = SETTINGS_H;
        final int amber = 0xFFFFB300;
        final int grey = 0xFF9AA0AA;

        // Cover the whole right screen so no live route/scale art shows through the menu.
        graphics.fill(x0, y0, x0 + w, y0 + h, 0xFF070B12);
        graphics.fill(x0, y0, x0 + w, y0 + 1, 0xFF3A4252);
        graphics.fill(x0, y0 + h - 1, x0 + w, y0 + h, 0xFF3A4252);
        graphics.fill(x0, y0, x0 + 1, y0 + h, 0xFF3A4252);
        graphics.fill(x0 + w - 1, y0, x0 + w, y0 + h, 0xFF3A4252);

        Font font = Minecraft.getInstance().font;
        String title = Component.translatable(switch (routeSettingsPage) {
            case 0 -> "realism.etcs.route_settings.title";
            case 1 -> "realism.etcs.route_settings.select_station";
            default -> "realism.etcs.train_settings.title";
        }).getString();
        graphics.drawString(font, fitText(font, title, w - 36), x0 + 7, y0 + 6, amber, false);
        graphics.fill(x0 + 5, y0 + 18, x0 + w - 5, y0 + 19, 0xFF39414F);

        switch (routeSettingsPage) {
            case 0 -> renderRouteSettingsMenu(graphics, font, x0, y0, w, amber, grey);
            case 1 -> renderRouteStationList(graphics, font, x0, y0, w, h, amber, grey);
            default -> renderTrainSettings(graphics, font, x0, y0, w, amber, grey);
        }

        String hint = Component.translatable(switch (routeSettingsPage) {
            case 0 -> "realism.etcs.route_settings.hint_menu";
            case 1 -> "realism.etcs.route_settings.hint_list";
            default -> trainSettingsEditing
                    ? "realism.etcs.train_settings.edit_hint"
                    : "realism.etcs.train_settings.hint";
        }).getString();
        graphics.drawString(font, fitText(font, hint, w - 14), x0 + 7, y0 + h - 13, 0xFF6E7787, false);
    }

    /**
     * Mouse click on the DMI glass, in DMI texture units. Only the settings pages react;
     * clicking a row selects it and performs the same action as ENTER on that row, so the
     * bezel keys and the mouse are interchangeable. Returns true when the click was consumed.
     */
    public boolean routeSettingsClick(double dmiX, double dmiY) {
        if (!routeSettingsOpen) return false;
        if (dmiX < SETTINGS_X || dmiX >= SETTINGS_X + SETTINGS_W
                || dmiY < SETTINGS_Y || dmiY >= SETTINGS_Y + SETTINGS_H) {
            return false;
        }
        return switch (routeSettingsPage) {
            case 0 -> clickRouteMenuRow(dmiY);
            case 1 -> clickStationRow(dmiY);
            default -> clickTrainSettingRow(dmiY);
        };
    }

    private boolean clickRouteMenuRow(double dmiY) {
        int row = rowAt(dmiY, ROUTE_MENU_TOP, ROUTE_MENU_ROW_H, ROUTE_MENU_ITEMS);
        if (row < 0) return false;
        routeSettingsIndex = row;
        routeSettingsAccept();
        return true;
    }

    private boolean clickStationRow(double dmiY) {
        int row = rowAt(dmiY, STATION_LIST_TOP, STATION_ROW_H, ROUTE_SETTINGS_ROWS);
        if (row < 0) return false;
        int index = routeSettingsScroll + row;
        if (index < 0 || index >= routeSettingsFiltered.size()) return false;
        routeSettingsIndex = index;
        applyRouteDestination(routeSettingsFiltered.get(index));
        closeRouteSettings();
        return true;
    }

    private boolean clickTrainSettingRow(double dmiY) {
        List<Integer> rows = trainSettingRows();
        int row = rowAt(dmiY, TRAIN_SETTINGS_TOP, TRAIN_SETTINGS_ROW_H, rows.size());
        if (row < 0) return false;
        if (trainSettingsEditing) {
            // Clicking the row being edited confirms it; clicking another row commits the
            // edit and moves the selection there.
            trainSettingsAccept();
            routeSettingsIndex = row;
            return true;
        }
        routeSettingsIndex = row;
        trainSettingsAccept();
        return true;
    }

    /** Row index under a click, with a small padding matching the drawn row highlight. */
    private static int rowAt(double dmiY, int top, int rowHeight, int count) {
        if (count <= 0) return -1;
        int row = (int) Math.floor((dmiY - top + 4) / rowHeight);
        return row >= 0 && row < count ? row : -1;
    }

    private void renderRouteSettingsMenu(GuiGraphics graphics, Font font, int x0, int y0, int w,
                                         int amber, int grey) {
        String[] labels = {
                Component.translatable("realism.etcs.route_settings.set_destination").getString(),
                Component.translatable("realism.etcs.route_settings.clear").getString(),
                Component.translatable("realism.etcs.route_settings.back").getString()
        };
        int rowY = y0 + (ROUTE_MENU_TOP - SETTINGS_Y);
        for (int i = 0; i < labels.length; i++) {
            drawRouteSettingsRow(graphics, font, x0, w, rowY + i * ROUTE_MENU_ROW_H, labels[i],
                    i == routeSettingsIndex, false, amber);
        }

        RouteGuidance guidance = currentGuidance();
        String current = guidance != null && guidance.hasDestination()
                ? guidance.destinationName
                : Component.translatable("realism.etcs.route_settings.none").getString();
        int infoY = rowY + labels.length * ROUTE_MENU_ROW_H + 18;
        graphics.drawString(font, Component.translatable("realism.etcs.route_settings.current").getString(),
                x0 + 8, infoY, grey, false);
        graphics.drawString(font, fitText(font, current, w - 16), x0 + 8, infoY + 13, 0xFFD6D9DF, false);
    }

    private void renderRouteStationList(GuiGraphics graphics, Font font, int x0, int y0, int w, int h,
                                        int amber, int grey) {
        // Visible search bar: typing filters the station list live.
        int barX = x0 + 8;
        int barY = y0 + 28;
        int barW = w - 16;
        int barH = 20;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF101720);
        graphics.fill(barX, barY, barX + barW, barY + 1, 0xFF39414F);
        graphics.fill(barX, barY + barH - 1, barX + barW, barY + barH, 0xFF39414F);
        graphics.fill(barX, barY, barX + 1, barY + barH, 0xFF39414F);
        graphics.fill(barX + barW - 1, barY, barX + barW, barY + barH, 0xFF39414F);

        boolean empty = routeSettingsQuery.isEmpty();
        String shown = empty
                ? Component.translatable("realism.etcs.route_settings.search_hint").getString()
                : routeSettingsQuery;
        String fitted = fitText(font, shown, barW - 12);
        graphics.drawString(font, fitted, barX + 6, barY + 6, empty ? 0xFF6E7787 : 0xFFD6D9DF, false);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = Math.min(barX + barW - 4, barX + 6 + font.width(fitted));
            graphics.fill(caretX, barY + 5, caretX + 1, barY + barH - 5, amber);
        }

        int size = routeSettingsFiltered.size();
        if (size == 0) {
            String message = routeSettingsStations.isEmpty()
                    ? Component.translatable("realism.etcs.route_settings.no_stations").getString()
                    : Component.translatable("realism.etcs.route_settings.no_matches").getString();
            graphics.drawString(font, message, x0 + 8, y0 + 62, grey, false);
            return;
        }

        RouteGuidance guidance = currentGuidance();
        String currentName = guidance != null && guidance.hasDestination() ? guidance.destinationName : null;
        int listTop = y0 + (STATION_LIST_TOP - SETTINGS_Y);
        int rowHeight = STATION_ROW_H;
        for (int row = 0; row < ROUTE_SETTINGS_ROWS; row++) {
            int index = routeSettingsScroll + row;
            if (index >= size) break;
            String name = routeSettingsFiltered.get(index);
            drawRouteSettingsRow(graphics, font, x0, w, listTop + row * rowHeight, name,
                    index == routeSettingsIndex, name.equals(currentName), amber);
        }

        String position = (routeSettingsIndex + 1) + "/" + size;
        graphics.drawString(font, position, x0 + w - 8 - font.width(position), y0 + 7, grey, false);
    }

    private void renderTrainSettings(GuiGraphics graphics, Font font, int x0, int y0, int w,
                                     int amber, int grey) {
        if (trainSettingsDraft == null) return;
        List<Integer> rows = trainSettingRows();
        int rowY = y0 + (TRAIN_SETTINGS_TOP - SETTINGS_Y);
        int rowHeight = TRAIN_SETTINGS_ROW_H;
        for (int i = 0; i < rows.size(); i++) {
            int id = rows.get(i);
            boolean selected = i == routeSettingsIndex;
            boolean editing = selected && trainSettingsEditing && isNumericTrainSetting(id);
            drawTrainSettingsRow(graphics, font, x0, w, rowY + i * rowHeight,
                    trainSettingLabel(id), trainSettingValue(id), selected, editing, amber);
        }
    }

    private void drawTrainSettingsRow(GuiGraphics graphics, Font font, int x0, int w, int y,
                                      String label, String value, boolean selected, boolean editing, int amber) {
        if (selected) {
            graphics.fill(x0 + 4, y - 3, x0 + w - 5, y + 11, 0x33FFB300);
            graphics.fill(x0 + 4, y - 3, x0 + 5, y + 11, amber);
        }
        String displayValue = editing ? "< " + value + " >" : value;
        int valueWidth = Math.min(font.width(displayValue), (w - 24) / 2);
        String fittedLabel = fitText(font, label, w - 24 - valueWidth);
        graphics.drawString(font, fittedLabel, x0 + 8, y, selected ? 0xFFFFD066 : 0xFFD6D9DF, false);
        graphics.drawString(font, fitText(font, displayValue, valueWidth + 8),
                x0 + w - 8 - font.width(fitText(font, displayValue, valueWidth + 8)), y,
                editing ? 0xFFFFB300 : (selected ? 0xFFFFD066 : greyColor()), false);
    }

    private static int greyColor() {
        return 0xFFD6D9DF;
    }

    private static boolean isNumericTrainSetting(int id) {
        return id == TS_MIN_SPEED || id == TS_MAX_TILT || id == TS_TILT_INTENSITY || id == TS_ACCEL_VALUE;
    }

    private String trainSettingLabel(int id) {
        return Component.translatable(switch (id) {
            case TS_TILT_MODE -> "realism.gui.tilt.label";
            case TS_TILT_DIRECTION -> "realism.gui.tilt.direction.label";
            case TS_MIN_SPEED -> "realism.gui.tilt.min_speed.label";
            case TS_MAX_TILT -> "realism.gui.tilt.max_tilt.label";
            case TS_TILT_INTENSITY -> "realism.gui.tilt.intensity.label";
            case TS_ACCEL_MODE -> "realism.gui.accel.label";
            case TS_ACCEL_VALUE -> "realism.gui.accel.value.label";
            default -> "realism.etcs.train_settings.title";
        }).getString();
    }

    private String trainSettingValue(int id) {
        TrainSettings s = trainSettingsDraft;
        if (s == null) return "";
        return switch (id) {
            case TS_TILT_MODE -> Component.translatable("realism.gui.tilt." +
                    s.ts.name().toLowerCase(Locale.ROOT)).getString();
            case TS_TILT_DIRECTION -> Component.translatable(s.Inside
                    ? "realism.gui.tilt.direction.inside"
                    : "realism.gui.tilt.direction.outside").getString();
            case TS_MIN_SPEED -> String.format(Locale.ROOT, "%.0f", s.customMinSpeed);
            case TS_MAX_TILT -> String.format(Locale.ROOT, "%.1f°", s.customMaxTilt);
            case TS_TILT_INTENSITY -> String.format(Locale.ROOT, "%.1f", s.customTiltIntensity);
            case TS_ACCEL_MODE -> Component.translatable(switch (s.as) {
                case NONE -> "realism.gui.accel.setting.none";
                case REALISTIC -> "realism.gui.accel.setting.standard";
                case CUSTOM -> "realism.gui.accel.setting.custom";
            }).getString();
            case TS_ACCEL_VALUE -> String.format(Locale.ROOT, "%.1f m/s^2", s.customAcceleration);
            default -> "";
        };
    }

    private void drawRouteSettingsRow(GuiGraphics graphics, Font font, int x0, int w, int y,
                                      String text, boolean selected, boolean current, int amber) {
        if (selected) {
            graphics.fill(x0 + 4, y - 2, x0 + w - 5, y + 9, 0x33FFB300);
            graphics.fill(x0 + 4, y - 2, x0 + 5, y + 9, amber);
        }
        String label = (current ? "* " : "") + text;
        graphics.drawString(font, fitText(font, label, w - 20), x0 + 8, y,
                selected ? 0xFFFFD066 : 0xFFD6D9DF, false);
    }

    /** Truncates text with an ellipsis so it never leaves its cell. */
    private static String fitText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        String trimmed = text;
        while (trimmed.length() > 1 && font.width(trimmed + ellipsis) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    public void renderETCSlimits(GuiGraphics graphics, PoseStack posestack, int Xpos, int Ypos) {
        posestack.pushPose();
        posestack.translate(Xpos + 361, Ypos + 227, 0);
        int CurrentY = 0;

        ResourceLocation startTex = ResourceLocation.tryBuild("realism", "textures/etcsplusstart.png");
        ResourceLocation midTex = ResourceLocation.tryBuild("realism", "textures/etcsplusmid.png");
        ResourceLocation endTex = ResourceLocation.tryBuild("realism", "textures/etcsplusend.png");
        ResourceLocation flagTex = ResourceLocation.tryBuild("realism", "textures/flag.png");

        // Scale boundaries based on zoom to match calculateDistancePixelPosition
        double scaledMax = 4000.0 / zoom;
        double scaledBoundary1 = 1.0 / zoom;
        double scaledBoundary2 = 100.0 / zoom;
        double scaledBoundary3 = 200.0 / zoom;
        double scaledBoundary4 = 300.0 / zoom;
        double scaledBoundary5 = 400.0 / zoom;
        double scaledBoundary6 = 500.0 / zoom;
        double scaledBoundary7 = 1000.0 / zoom;
        double scaledBoundary8 = 2000.0 / zoom;

        // Handle case where distance is beyond max range
        if (distanceToSignal > scaledMax) {
            CurrentY -= 9;
            renderElement(graphics, startTex, 0, CurrentY, 15, 9);

            for (int i = 0; i < 198; i++) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }

            posestack.popPose();
            return;
        }
        // Start rendering

        if(distanceToSignal >100) {
            CurrentY -= 9;
            renderElement(graphics, startTex, 0, CurrentY, 15, 9);
        }

        // 1st range: 0-1/zoom units
        double range1 = Math.min(scaledBoundary1, distanceToSignal);
        int pixelLength1 = (int)(range1 * 0.25 * zoom);

        // Render first segment
        for (int i = 0; i < pixelLength1; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within the first boundary
        if (distanceToSignal <= scaledBoundary1) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 2nd range: 1-100/zoom units
        double range2 = Math.min(scaledBoundary2, distanceToSignal) - scaledBoundary1;
        int pixelLength2 = (int)(range2 * 0.36 * zoom);

        // Render second segment
        for (int i = 0; i < pixelLength2; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within second boundary
        if (distanceToSignal <= scaledBoundary2) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 3rd range: 100-200/zoom units
        double range3 = Math.min(scaledBoundary3, distanceToSignal) - scaledBoundary2;
        int pixelLength3 = (int)(range3 * 0.21 * zoom);

        // Render third segment
        for (int i = 0; i < pixelLength3; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within third boundary
        if (distanceToSignal <= scaledBoundary3) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 4th range: 200-300/zoom units
        double range4 = Math.min(scaledBoundary4, distanceToSignal) - scaledBoundary3;
        int pixelLength4 = (int)(range4 * 0.14 * zoom);

        // Render fourth segment
        for (int i = 0; i < pixelLength4; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within fourth boundary
        if (distanceToSignal <= scaledBoundary4) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 5th range: 300-400/zoom units
        double range5 = Math.min(scaledBoundary5, distanceToSignal) - scaledBoundary4;
        int pixelLength5 = (int)(range5 * 0.11 * zoom);

        // Render fifth segment
        for (int i = 0; i < pixelLength5; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within fifth boundary
        if (distanceToSignal <= scaledBoundary5) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 6th range: 400-500/zoom units
        double range6 = Math.min(scaledBoundary6, distanceToSignal) - scaledBoundary5;
        int pixelLength6 = (int)(range6 * 0.11 * zoom);

        // Render sixth segment
        for (int i = 0; i < pixelLength6; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within sixth boundary
        if (distanceToSignal <= scaledBoundary6) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 7th range: 500-1000/zoom units
        double range7 = Math.min(scaledBoundary7, distanceToSignal) - scaledBoundary6;
        int pixelLength7 = (int)(range7 * 0.068 * zoom);

        // Render seventh segment
        for (int i = 0; i < pixelLength7; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within seventh boundary
        if (distanceToSignal <= scaledBoundary7) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 8th range: 1000-2000/zoom units
        double range8 = Math.min(scaledBoundary8, distanceToSignal) - scaledBoundary7;
        int pixelLength8 = (int)(range8 * 0.034 * zoom);

        // Render eighth segment
        for (int i = 0; i < pixelLength8; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // Exit if within eighth boundary
        if (distanceToSignal <= scaledBoundary8) {
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
            posestack.popPose();
            return;
        }

        // 9th range: 2000+ units
        double range9 = distanceToSignal - scaledBoundary8;
        int pixelLength9 = (int)(range9 * 0.017 * zoom);

        // Render ninth segment
        for (int i = 0; i < pixelLength9; i++) {
            CurrentY -= 1;
            renderElement(graphics, midTex, 0, CurrentY, 15, 1);
        }

        // End rendering
        CurrentY -= 9;
        renderElement(graphics, endTex, 0, CurrentY, 15, 9);
        renderElement(graphics, flagTex, 15, CurrentY, 19, 11);
        posestack.popPose();
    }

    public void renderBrakingCurve(GuiGraphics graphics, PoseStack poseStack, int xPos, int yPos) {
        float currentSpeed = (float) Math.abs(train.speed * 20f * 3.6f);
        float distance = (float) distanceToSignal;

        // Recalculate these values client-side
        double allowedSpeed = speedLimit;

        boolean approachingBrakingZone = distance <= (cachedWarningBrakingDist * 1.5) + 100;

        // Determine curve color
        int curveColor;
        if (currentSpeed > speedLimit) {
            curveColor = 0xFFFF0000;  // Red
            startWarningLoop();
        } else if (cachedCurveIsDropping || approachingBrakingZone) {
            curveColor = 0xFFFFFF00;  // Yellow
            stopWarningLoop();

        } else {
            curveColor = 0xFF888888;  // Gray
            //braking = false;
            stopWarningLoop();
        }

        // Optimize render speed curve using calculated values
        optimizedRenderSpeedCurve(graphics, poseStack, xPos + 155, yPos + 119, allowedSpeed, curveColor);
    }

    public void renderOverviewItems(GuiGraphics graphics, int xPos, int yPos, int zoom) {
        Font font = Minecraft.getInstance().font;
        ResourceLocation flag = ResourceLocation.tryBuild("realism", "textures/flag.png");
        for (SpeedLimit s : cachedSpeedLimits){
            if (s.distance() > distanceToSignal){continue;}
            int pixelPos = calculateDistancePixelPosition(s.distance(), zoom);
            //int pixelPos = 1;
            // Render the item at the calculated position
            renderElement(graphics, flag, xPos+386 , yPos +235 - pixelPos, 19, 11);
            graphics.drawString(font, String.valueOf((int)s.speedLimit()), xPos+406, yPos+235 - pixelPos, 0xFFFFFFFF);
        }
        if( distanceToBrakingPoint >0 && distanceToBrakingPoint < ((double) 4000 /zoom)){
            int pixelPos = calculateDistancePixelPosition(distanceToBrakingPoint, zoom);
            //int pixelPos = 1;
            // Render the item at the calculated position
            graphics.fill(xPos+396, yPos+235-pixelPos, xPos+471, yPos+238-pixelPos, 0xFFFFFF00);
        }







    }


    public int calculateDistancePixelPosition(double distance, int zoom) {
        int pixelPos = 0;

        // Scale distance boundaries by zoom factor
        double scaledMax = 4000.0 / zoom;
        double scaledBoundary1 = 100.0 / zoom;
        double scaledBoundary2 = 200.0 / zoom;
        double scaledBoundary3 = 300.0 / zoom;
        double scaledBoundary4 = 400.0 / zoom;
        double scaledBoundary5 = 500.0 / zoom;
        double scaledBoundary6 = 1000.0 / zoom;
        double scaledBoundary7 = 2000.0 / zoom;
        double scaledBoundary8 = 4000.0 / zoom;

        if (distance > scaledMax) {
            return 198; // Maximum offset
        }
        // 1st range: 0-100/zoom units
        double range1 = Math.min(scaledBoundary1, distance);
        pixelPos += (int)(range1 * 0.25 * zoom);
        if (distance <= scaledBoundary1) {
            return pixelPos;
        }
        // 2nd range: 101-200/zoom units
        double range2 = Math.min(scaledBoundary2, distance) - scaledBoundary1;
        pixelPos += (int)(range2 * 0.36 * zoom);
        if (distance <= scaledBoundary2) {
            return pixelPos;
        }
        // 3rd range: 201-300/zoom units
        double range3 = Math.min(scaledBoundary3, distance) - scaledBoundary2;
        pixelPos += (int)(range3 * 0.21 * zoom);

        if (distance <= scaledBoundary3) {
            return pixelPos;
        }
        // 4th range: 301-400/zoom units
        double range4 = Math.min(scaledBoundary4, distance) - scaledBoundary3;
        pixelPos += (int)(range4 * 0.14 * zoom);

        if (distance <= scaledBoundary4) {
            return pixelPos;
        }

        // 5th range: 400-500/zoom units
        double range5 = Math.min(scaledBoundary5, distance) - scaledBoundary4;
        pixelPos += (int)(range5 * 0.11 * zoom);

        if (distance <= scaledBoundary5) {
            return pixelPos;
        }

        // 6th range: 501-1000/zoom units
        double range6 = Math.min(scaledBoundary6, distance) - scaledBoundary5;
        pixelPos += (int)(range6 * 0.068 * zoom);

        if (distance <= scaledBoundary6) {
            return pixelPos;
        }

        // 7th range: 1001-2000/zoom units
        double range7 = Math.min(scaledBoundary7, distance) - scaledBoundary6;
        pixelPos += (int)(range7 * 0.034 * zoom);

        if (distance <= scaledBoundary7) {
            return pixelPos;
        }

        // 8th range: 2001+ units
        double range8 = distance - scaledBoundary8;
        pixelPos += (int)(range8 * 0.017 * zoom);

        return pixelPos;
    }

    /**
     * Update data from network packet
     */
     private void markDirty() {
        this.needsSync = true;
     }


    private double calculateStoppingDistance(float speedKmh, float deceleration, float targetSpeedKmh) {
        // Convert km/h to m/s
        float speedMs = speedKmh / 3.6f;
        float targetSpeedMs = targetSpeedKmh / 3.6f;
        // Basic physics: d = (v² - u²)/(2a)
        return ((speedMs * speedMs) - (targetSpeedMs * targetSpeedMs)) / (2 * deceleration);
    }

    private int calculateAllowedSpeed(float distance, float maxDeceleration) {
         if (Float.isNaN(distance) || distance < 0) distance = 0;
         distanceToBrakingPoint = 999999999;
         int distanctolimit;
         int targetspeedlimit;
        float safetyFactor = 1.2f;

        // Calculate safe speed based on distance to signal
        float safeSpeed = (float) (Math.sqrt(2 * (maxDeceleration * 0.9) * distance) * 3.6);
        distanctolimit = (int) distance;
        targetspeedlimit = 0;

        int signalBasedSpeed = (int)(safeSpeed / safetyFactor);

        // Check for speed limits from tram signs
        int limitBasedSpeed = Integer.MAX_VALUE;
        for (SpeedLimit speedLimit : cachedSpeedLimits) {
            if (speedLimit.distance() > 0 && speedLimit.distance() <= distance * 1.5) {
                float distToLimit = (float) speedLimit.distance();
                float targetSpeed = (float) speedLimit.speedLimit() / 3.6f; // Convert km/h to m/s

                float targetSpeedSq = targetSpeed * targetSpeed;
                safeSpeed = (float) (Math.sqrt(targetSpeedSq + 2 * (maxDeceleration * 0.9) * distToLimit) * 3.6);

                int adjustedLimit = (int)(safeSpeed);

                if (adjustedLimit < limitBasedSpeed) {
                    limitBasedSpeed = adjustedLimit;
                    distanctolimit = (int) speedLimit.distance();
                    targetspeedlimit = (int) speedLimit.speedLimit();
                }
            }
            if(speedLimit.distance()<5){
                trackspeedlimit = (int)speedLimit.speedLimit();
            }

        }
        updateBrakingDistances(trackspeedlimit,targetspeedlimit);
        distanceToBrakingPoint = distanctolimit - (cachedServiceBrakingDist) - 100;

        // Return the most restrictive of signal-based and limit-based speeds
        return limitBasedSpeed < Integer.MAX_VALUE ?
               Math.min(signalBasedSpeed, limitBasedSpeed) :
               signalBasedSpeed;
    }

    private void updateBrakingDistances(int currentSpeed,int targetspeed) {
        float maxDeceleration = train.acceleration() * 2f * 400;

        cachedEmergencyBrakingDist = calculateStoppingDistance(currentSpeed, (float) (maxDeceleration * 1.0),targetspeed);
        cachedServiceBrakingDist = calculateStoppingDistance(currentSpeed, (float) (maxDeceleration * 0.9),targetspeed);
        cachedWarningBrakingDist = calculateStoppingDistance(currentSpeed, (float) (maxDeceleration * 0.5),targetspeed);
    }

    public record SpeedLimit(double distance, double speedLimit) {
    }

    /**
     * Synchronize ETCS data to clients
     */
    private void syncToClients() {
        // Only run on server side and when sync is needed
        if (train == null || !needsSync) return;

        // Check if it's time to sync
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSyncTime < SYNC_INTERVAL_MS) return;

        lastSyncTime = currentTime;
        needsSync = false;
        if(train.carriages.get(0).anyAvailableEntity() == null){return;}
        Level level = train.carriages.get(0).anyAvailableEntity().level();
        if (level == null || level.isClientSide()) return;

        MinecraftServer server = level.getServer();
        if (server == null) return;

        // Create and send the sync packet, including new-route sound flag
        ETCSSyncPacket packet = new ETCSSyncPacket(
                train.id,
                distanceToSignal,
                speedLimit,
                needleRotationDegrees,
                backward,
                cachedEmergencyBrakingDist,
                cachedServiceBrakingDist,
                cachedWarningBrakingDist,
                cachedCurveIsDropping,
                cachedSpeedLimits,
                zoom,
                pendingBeepSound,
                distanceToBrakingPoint,
                toUpdate,
                cachedDistToDestination,
                cachedDestBehind,
                cachedTurnDir.toByte(),
                cachedTurnDistance,
                cachedTurn2Dir.toByte(),
                cachedTurn2Distance
        );

        // Send to all players
        RNetworking.sendToAll(packet);
        // Clear the new-route sound flag on server after sending
        pendingBeepSound = false;
    }

    public void updateFromNetwork(double distanceToSignal, double speedLimit, float needleRotation, boolean backward,
                                  double emergencyBrakingDist, double serviceBrakingDist, double warningBrakingDist,
                                  boolean curveIsDropping, List<SpeedLimit> speedLimits, int zoom,
                                  boolean newRouteSound, double distanceToBrakingPoint, boolean toUpdate,
                                  double distToDestination, boolean destBehind, byte turnDir, double turnDistance,
                                  byte turn2Dir, double turn2Distance) {
        this.distanceToSignal = distanceToSignal;
        this.speedLimit = speedLimit;
        this.needleRotationDegrees = needleRotation;
        this.backward = backward;

        // Update speed limits from network
        this.cachedSpeedLimits = new ArrayList<>(speedLimits);

        // Update the braking distances
        this.cachedEmergencyBrakingDist = emergencyBrakingDist;
        this.cachedServiceBrakingDist = serviceBrakingDist;
        this.cachedWarningBrakingDist = warningBrakingDist;
        this.cachedCurveIsDropping = curveIsDropping;
        this.zoom = zoom;
        this.distanceToBrakingPoint = distanceToBrakingPoint;
        // Trigger sound on client side if new route detected
        this.pendingBeepSound = newRouteSound;
        this.cachedDistToDestination = distToDestination;
        if (ETCSMessages.isCurrentDrive(train.id)) {
            if (destBehind && !this.cachedDestBehind) {
                ETCSMessages.push("behind", ETCSMessages.Severity.ADVISORY,
                        Component.translatable("realism.etcs.msg.dest_behind").getString(), 0);
            } else if (!destBehind) {
                ETCSMessages.clear("behind");
            }
        }
        this.cachedDestBehind = destBehind;
        this.cachedTurnDir = RouteTurnFinder.TurnDir.fromByte(turnDir);
        this.cachedTurnDistance = turnDistance;
        this.cachedTurn2Dir = RouteTurnFinder.TurnDir.fromByte(turn2Dir);
        this.cachedTurn2Distance = turn2Distance;
        this.lastUpdateTime = System.currentTimeMillis();
        // Clear the sync flag since we just received fresh data
        this.needsSync = false;
        this.toUpdate = toUpdate;
    }

    /**
     * Save ETCS state to the train's NBT data
     */
    public CompoundTag saveToNBT() {
        if (train == null) return null;

        CompoundTag etcsData = new CompoundTag();
        etcsData.putDouble("distanceToSignal", distanceToSignal);
        etcsData.putDouble("speedLimit", speedLimit);
        etcsData.putFloat("needleRotation", needleRotationDegrees);
        etcsData.putBoolean("backward", backward);
        etcsData.putBoolean("previousBackward", previousBackward);
        etcsData.putLong("lastUpdateTime", lastUpdateTime);

        // Add braking distances and curve dropping flag
        etcsData.putDouble("emergencyBrakingDist", cachedEmergencyBrakingDist);
        etcsData.putDouble("serviceBrakingDist", cachedServiceBrakingDist);
        etcsData.putDouble("warningBrakingDist", cachedWarningBrakingDist);
        etcsData.putBoolean("curveIsDropping", cachedCurveIsDropping);
        etcsData.putDouble("allowedSpeed", cachedAllowedSpeed);
        etcsData.putInt("zoom", zoom);
        etcsData.putInt("trackspeedlimit", trackspeedlimit);
        etcsData.putDouble("distToDestination", cachedDistToDestination);
        etcsData.putBoolean("destBehind", cachedDestBehind);
        etcsData.putByte("turnDir", cachedTurnDir.toByte());
        etcsData.putDouble("turnDistance", cachedTurnDistance);
        etcsData.putByte("turn2Dir", cachedTurn2Dir.toByte());
        etcsData.putDouble("turn2Distance", cachedTurn2Distance);

        // Save speed limits
        CompoundTag speedLimitsTag = new CompoundTag();
        speedLimitsTag.putInt("size", cachedSpeedLimits.size());
        for (int i = 0; i < cachedSpeedLimits.size(); i++) {
            SpeedLimit limit = cachedSpeedLimits.get(i);
            CompoundTag limitTag = new CompoundTag();
            limitTag.putDouble("distance", limit.distance());
            limitTag.putDouble("speed", limit.speedLimit());
            speedLimitsTag.put("limit" + i, limitTag);
        }
        etcsData.put("speedLimits", speedLimitsTag);

        return etcsData;
    }

    /**
     * Load ETCS state from the train's NBT data
     */
    public void loadFromNBT(CompoundTag etcsData) {
        if (train == null) return;

        this.distanceToSignal = etcsData.getDouble("distanceToSignal");
        this.speedLimit = etcsData.getDouble("speedLimit");
        this.needleRotationDegrees = etcsData.getFloat("needleRotation");
        this.backward = etcsData.getBoolean("backward");
        this.previousBackward = etcsData.getBoolean("previousBackward");
        this.lastUpdateTime = etcsData.getLong("lastUpdateTime");

        // Load braking distances and curve dropping flag
        this.cachedEmergencyBrakingDist = etcsData.getDouble("emergencyBrakingDist");
        this.cachedServiceBrakingDist = etcsData.getDouble("serviceBrakingDist");
        this.cachedWarningBrakingDist = etcsData.getDouble("warningBrakingDist");
        this.cachedCurveIsDropping = etcsData.getBoolean("curveIsDropping");
        this.cachedAllowedSpeed = etcsData.getDouble("allowedSpeed");
        this.zoom = etcsData.getInt("zoom");
        this.trackspeedlimit = etcsData.getInt("trackspeedlimit");
        this.cachedDistToDestination = etcsData.contains("distToDestination") ? etcsData.getDouble("distToDestination") : -1;
        this.cachedDestBehind = etcsData.getBoolean("destBehind");
        this.cachedTurnDir = RouteTurnFinder.TurnDir.fromByte(etcsData.getByte("turnDir"));
        this.cachedTurnDistance = etcsData.contains("turnDistance") ? etcsData.getDouble("turnDistance") : -1;
        this.cachedTurn2Dir = RouteTurnFinder.TurnDir.fromByte(etcsData.getByte("turn2Dir"));
        this.cachedTurn2Distance = etcsData.contains("turn2Distance") ? etcsData.getDouble("turn2Distance") : -1;

        // Load speed limits
        if (etcsData.contains("speedLimits")) {
            CompoundTag speedLimitsTag = etcsData.getCompound("speedLimits");
            int size = speedLimitsTag.getInt("size");
            this.cachedSpeedLimits = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                CompoundTag limitTag = speedLimitsTag.getCompound("limit" + i);
                double distance = limitTag.getDouble("distance");
                double speed = limitTag.getDouble("speed");
                this.cachedSpeedLimits.add(new SpeedLimit(distance, speed));
            }
        }
    }

    private void sendKeysToServer() {
        boolean plusKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_EQUALS);
        boolean minusKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_MINUS);
        long currentTime = System.currentTimeMillis();

        // Detect key press events (transition from not pressed to pressed)
        if (plusKeyDown && !plusKeyWasDown && currentTime - lastKeyPressTime > KEY_COOLDOWN_MS) {
            RNetworking.sendToServer(new SteerDirectionPacket(PLUS));
            lastKeyPressTime = currentTime;
        } else if (minusKeyDown && !minusKeyWasDown && currentTime - lastKeyPressTime > KEY_COOLDOWN_MS) {
            RNetworking.sendToServer(new SteerDirectionPacket(MINUS));
            lastKeyPressTime = currentTime;
        } else if (!plusKeyDown && !minusKeyDown) {
            // Only send NONE when both keys are released
            RNetworking.sendToServer(new SteerDirectionPacket(NONE));
        }

        // Update previous key states
        plusKeyWasDown = plusKeyDown;
        minusKeyWasDown = minusKeyDown;
    }

    private void ReciveKeys() {
        Optional<UUID> controllingPlayerUuid = train.carriages.stream()
                .flatMap(carriage -> {
                    CarriageContraptionEntity entity = carriage.anyAvailableEntity();
                    return entity != null ? Stream.of(entity.getControllingPlayer()) : Stream.empty();
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (controllingPlayerUuid.isEmpty()) return;

        SteerDirectionPacket.KeyPressType currentKeyPress = SteerDirectionPacket.getPlayerKeyPress(controllingPlayerUuid.get());
        if (zoom == 0){
            zoom = 1;
        }
        // Only process key press once
        switch (currentKeyPress) {
            case PLUS:
                if (zoom < 4) {
                    zoom *= 2;
                    // Reset key state after processing
                    SteerDirectionPacket.setPlayerKeyPresses(controllingPlayerUuid.get(), NONE);
                }
                break;
            case MINUS:
                if (zoom > 1) {
                    zoom /= 2;
                    // Reset key state after processing
                    SteerDirectionPacket.setPlayerKeyPresses(controllingPlayerUuid.get(), NONE);
                }
                break;
        }
    }
}
