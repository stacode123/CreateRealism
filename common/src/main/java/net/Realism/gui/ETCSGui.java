package net.Realism.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.RealismExpectPlatform;
import net.Realism.config.RealismConfig;
import net.Realism.network.SteerDirectionPacket;
import net.Realism.trains.etcs.ETCS;
import net.Realism.trains.etcs.ETCSMessages;
import net.Realism.trains.etcs.ETCSsounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The focused ETCS cab screen: the very same driver-machine interface that the HUD overlay
 * draws, enlarged and centered, wrapped in a bezel so it reads as a physical cab display.
 * <p>
 * There is exactly one DMI render ({@link ETCS#renderFocused}); this window adds invisible
 * soft-key hit zones over the bezel buttons already baked into the texture. Right column,
 * top to bottom: zoom out, zoom in, menu up, menu down. Bottom row, left to right: Main
 * (back to the default screen), two unassigned keys, route guidance settings, train
 * settings. The two keys above the bottom row scroll the message window, and the knob
 * glyph at the bottom right accepts/enters a menu selection.
 * <p>
 * Escape or leaving the controls closes the window.
 */
public class ETCSGui extends DLWindow {

    /** DMI texture size (constant across all etcszoom variants). */
    public static final int DMI_WIDTH = 536;
    public static final int DMI_HEIGHT = 401;
    /** Bezel thickness drawn around the glass, in window units. */
    private static final int BEZEL = 6;

    private static final int AMBER = 0xFFFFB300;
    private static final int BEZEL_DARK = 0xFF07080A;
    private static final int BEZEL_EDGE = 0xFF262B33;
    private static final int GLASS_EDGE = 0xFF0B0D11;

    private static boolean openInstance = false;
    /** Set when ESC closed the screen; cleared once the key is physically released. */
    private static boolean escapeClosedGui = false;
    /** Display scale of the focused screen. */
    private static final double focusedScale = 0.5;

    private final ITrainInterface rtrain;
    private DisplayComponent display;
    private final List<SoftKey> softKeys = new ArrayList<>();
    /** DragonLib reports GLFW repeats as presses, so track Enter until its release event. */
    private final Set<Integer> heldEnterKeys = new HashSet<>();
    /** Debounces the keyboard zoom keys against GLFW key-repeat. */
    private long lastZoomKeyTime = 0;
    private static final long ZOOM_KEY_COOLDOWN_MS = 250;

    public static boolean isOpen() {
        return openInstance;
    }

    /** Safety net for paths that never dispatch a CloseEvent (disconnect, world change). */
    public static void forceClose() {
        openInstance = false;
        escapeClosedGui = false;
    }

    /**
     * Create stops train control whenever the raw ESC key is down. The cab screen consumes
     * the ESC press that closes it, so that press must not also release the controls; only
     * the next press (after ESC has been released and pressed again) does, as usual.
     */
    public static boolean shouldSuppressEscapeForTrainControl() {
        if (openInstance) return true;
        if (!escapeClosedGui) return false;
        if (!isEscapeDown()) {
            escapeClosedGui = false;
            return false;
        }
        return true;
    }

    private static boolean isEscapeDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE);
    }

    public ETCSGui(DLWindowManager manager, ITrainInterface rtrain) {
        super(manager);
        this.rtrain = rtrain;

        windowSpawnPosition.set(WindowPosition.CENTER);
        pauseGame.set(false);
        // The cab screen is a live display: singleplayer must keep ticking so the train,
        // zoom requests and message countdowns continue while it is open.
        manager.setPauseScreen(false);
        applyScale();
        openInstance = true;

        addEventListener(DLGuiStandardEvents.CloseEvent.class, (src, e) -> {
            openInstance = false;
            // ESC closes the screen through DragonLib; remember that this press belongs to
            // the cab screen so Create does not treat it as "release the train controls".
            escapeClosedGui = isEscapeDown();
            heldEnterKeys.clear();
            ETCS etcs = etcs();
            if (etcs != null) etcs.closeRouteSettings();
            return false;
        });
        display = addComponent(new DisplayComponent(
                BEZEL, BEZEL,
                (int) Math.ceil(DMI_WIDTH * focusedScale),
                (int) Math.ceil(DMI_HEIGHT * focusedScale)));

        // Keyboard zoom shortcut. Key events reach the window manager (and the focused
        // component), never windows themselves, so the listener lives on the manager.
        manager.addEventListener(DLGuiStandardEvents.KeyPressEvent.class, (src, e) -> {
            if (!isOpen()) {
                return false;
            }
            // No component on this screen owns the keyboard (the bezel keys are mouse-only),
            // so key shortcuts stay active even after a soft key was clicked and focused.
            ETCS etcs = etcs();
            if (etcs != null && etcs.isRouteSettingsOpen()) {
                // The station list owns the keyboard: type to filter, backspace to erase.
                if (etcs.isRouteStationListOpen()) {
                    if (e.keyCode() == GLFW.GLFW_KEY_BACKSPACE) {
                        etcs.routeSettingsBackspace();
                        return true;
                    }
                    if (acceptEnterKey(e.keyCode())) return true;
                    return false;
                }
                // Menu pages: ENTER confirms the highlighted entry.
                if (acceptEnterKey(e.keyCode())) return true;
                return false;
            }
            long now = System.currentTimeMillis();
            if (e.keyCode() == GLFW.GLFW_KEY_EQUAL || e.keyCode() == GLFW.GLFW_KEY_KP_ADD) {
                if (now - lastZoomKeyTime >= ZOOM_KEY_COOLDOWN_MS) {
                    lastZoomKeyTime = now;
                    zoom(true);
                }
                return true;
            } else if (e.keyCode() == GLFW.GLFW_KEY_MINUS || e.keyCode() == GLFW.GLFW_KEY_KP_SUBTRACT) {
                if (now - lastZoomKeyTime >= ZOOM_KEY_COOLDOWN_MS) {
                    lastZoomKeyTime = now;
                    zoom(false);
                }
                return true;
            }
            return false;
        });
        manager.addEventListener(DLGuiStandardEvents.KeyReleaseEvent.class, (src, e) -> {
            heldEnterKeys.remove(e.keyCode());
            return false;
        });

        // Typed characters go straight into the station search box while it is open.
        manager.addEventListener(DLGuiStandardEvents.CharTypeEvent.class, (src, e) -> {
            ETCS etcs = etcs();
            if (etcs == null || !etcs.isRouteStationListOpen()) return false;
            etcs.routeSettingsType(e.codePoint());
            return true;
        });

        // Soft keys: hit zones over the bezel buttons baked into the DMI texture.
        // Right edge (top to bottom): zoom out, zoom in, menu up, menu down.
        addSoftKey(492, 80, 32, 52, "zoom_out", () -> zoom(false));
        addSoftKey(492, 132, 32, 53, "zoom_in", () -> zoom(true));
        addSoftKey(492, 185, 32, 51, "select_up", () -> menuMove(-1));
        addSoftKey(492, 236, 32, 51, "select_down", () -> menuMove(1));
        // Bottom row of the bezel, left to right.
        addSoftKey(10, 353, 51, 37, "main", this::mainKey);
        addSoftKey(61, 353, 50, 37, "override", () -> { });
        addSoftKey(111, 353, 52, 37, "data_view", () -> { });
        addSoftKey(163, 353, 54, 37, "route_settings", this::openRouteSettings);
        addSoftKey(217, 353, 48, 37, "train_settings", this::openTrainSettings);
        // Message scrolling, above the bottom row.
        addSoftKey(243, 289, 34, 33, "scroll_up", () -> scrollMessages(1));
        addSoftKey(243, 322, 34, 31, "scroll_down", () -> scrollMessages(-1));
        // Fifth key at the bottom of the right column: accept / enter.
        addSoftKey(493, 288, 30, 41, "enter", this::menuAccept);
        // Speedometer unit label (kph/mph): the hit zone is the whole DMI cell (x10-60,
        // y215-244) around the small label, so hover highlights the full button box.
        addSoftKey(10, 215, 50, 30, "speed_unit", this::toggleSpeedUnit);
    }

    @Override
    public void renderBackLayer(DLGuiGraphics g, double mouseX, double mouseY, Rectangle renderBounds) {
        super.renderBackLayer(g, mouseX, mouseY, renderBounds);
        // Physical bezel: dark housing with a subtly houselit edge around the glass.
        int w = width();
        int h = height();
        GuiUtils.fill(g, 0, 0, w, h, DLColor.fromInt(BEZEL_DARK));
        GuiUtils.fill(g, 0, 0, w, 1, DLColor.fromInt(BEZEL_EDGE));
        GuiUtils.fill(g, 0, h - 1, w, 1, DLColor.fromInt(BEZEL_EDGE));
        GuiUtils.fill(g, 0, 0, 1, h, DLColor.fromInt(BEZEL_EDGE));
        GuiUtils.fill(g, w - 1, 0, 1, h, DLColor.fromInt(BEZEL_EDGE));
        GuiUtils.fill(g, BEZEL - 1, BEZEL - 1, w - 2 * BEZEL + 2, 1, DLColor.fromInt(GLASS_EDGE));
        GuiUtils.fill(g, BEZEL - 1, h - BEZEL, w - 2 * BEZEL + 2, 1, DLColor.fromInt(GLASS_EDGE));
        GuiUtils.fill(g, BEZEL - 1, BEZEL - 1, 1, h - 2 * BEZEL + 2, DLColor.fromInt(GLASS_EDGE));
        GuiUtils.fill(g, w - BEZEL, BEZEL - 1, 1, h - 2 * BEZEL + 2, DLColor.fromInt(GLASS_EDGE));
    }

    /** Places the window, the DMI surface and all soft keys for the current focused scale. */
    private void applyScale() {
        int bw = (int) Math.ceil(DMI_WIDTH * focusedScale);
        int bh = (int) Math.ceil(DMI_HEIGHT * focusedScale);
        setSize(bw + 2 * BEZEL, bh + 2 * BEZEL);
        if (display != null) {
            display.setSize(bw, bh);
            // Shift soft keys so they keep hovering the same bezel buttons at any scale.
            for (SoftKey sk : softKeys) {
                sk.refit(focusedScale);
            }
        }
    }

    private ETCS etcs() {
        return rtrain != null ? rtrain.realism$getETCS() : null;
    }

    private void zoom(boolean plus) {
        RNetworking.sendToServer(new SteerDirectionPacket(plus
                ? SteerDirectionPacket.KeyPressType.PLUS
                : SteerDirectionPacket.KeyPressType.MINUS));
        ETCSsounds.playBeepSound();
    }

    /** Speedometer label: switches the whole DMI between km/h and mph. */
    private void toggleSpeedUnit() {
        RealismConfig.CLIENT.ETCSMPH.set(!RealismConfig.CLIENT.ETCSMPH.get());
        RealismExpectPlatform.saveClientConfig();
        ETCSsounds.playBeepSound();
    }

    /** Right-column arrows: only meaningful while the route settings menu is open. */
    private void menuMove(int delta) {
        ETCS etcs = etcs();
        if (etcs == null || !etcs.isRouteSettingsOpen()) return;
        etcs.routeSettingsMove(delta);
        ETCSsounds.playBeepSound();
    }

    /** Accepts once per physical Enter press; GLFW repeat events only consume the key. */
    private boolean acceptEnterKey(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_KP_ENTER) return false;
        if (heldEnterKeys.add(keyCode)) menuAccept();
        return true;
    }

    /** Knob glyph at the bottom right: accepts the highlighted menu entry. */
    private void menuAccept() {
        ETCS etcs = etcs();
        if (etcs == null || !etcs.isRouteSettingsOpen()) return;
        etcs.routeSettingsAccept();
        ETCSsounds.playBeepSound();
    }

    private void openRouteSettings() {
        ETCS etcs = etcs();
        if (etcs == null) return;
        etcs.openRouteSettings();
        ETCSsounds.playBeepSound();
    }

    /**
     * Main key: returns from the route settings page to the default screen, and from the
     * default screen back to the compact HUD.
     */
    private void mainKey() {
        ETCS etcs = etcs();
        if (etcs != null && etcs.isTrainSettingsEditing()) {
            etcs.cancelTrainSettingsEdit();
            ETCSsounds.playBeepSound();
        } else if (etcs != null && etcs.isRouteSettingsOpen()) {
            etcs.closeRouteSettings();
            ETCSsounds.playBeepSound();
        } else {
            closeFocused();
        }
    }

    private void scrollMessages(int delta) {
        ETCSMessages.scroll(delta);
        ETCSsounds.playBeepSound();
    }

    /** Wrench key: the train settings live in the same right-hand DMI panel. */
    private void openTrainSettings() {
        ETCS etcs = etcs();
        if (etcs == null) return;
        etcs.openTrainSettingsPage();
        ETCSsounds.playBeepSound();
    }

    private void closeFocused() {
        getAssignedModal().filter(id -> getWindowManager() != null && getWindowManager().hasModal(id))
                .ifPresent(id -> getWindowManager().closeModal(id));
    }

    private SoftKey addSoftKey(double dmiX, double dmiY, double dmiW, double dmiH, String key, Runnable action) {
        SoftKey k = new SoftKey(dmiX, dmiY, dmiW, dmiH, action, key);
        k.refit(focusedScale);
        addComponent(k);
        softKeys.add(k);
        return k;
    }

    /**
     * A click zone over one of the bezel buttons baked into the DMI texture. Invisible
     * unless hovered or pressed, so the HUD and the focused screen stay pixel-identical
     * when no pointer is on the display - like a real screen, keys light up only when used.
     */
    private static class SoftKey extends DLGuiComponent {

        /** Key geometry in DMI texture units, refitted into window units by {@link #refit(double)}. */
        private final double dmiX, dmiY, dmiW, dmiH;
        private final Runnable action;
        private long pressedUntil = 0;

        SoftKey(double dmiX, double dmiY, double dmiW, double dmiH, Runnable action, String key) {
            super(0, 0, (int) (dmiW), (int) (dmiH));
            this.dmiX = dmiX;
            this.dmiY = dmiY;
            this.dmiW = dmiW;
            this.dmiH = dmiH;
            this.action = action;
            tooltip.set(new DLTooltip(
                    List.of(TextUtils.text(Component.translatable("realism.gui.etcs.key." + key).getString())), 160));
            addEventListener(DLGuiStandardEvents.ClickEvent.class, (src, e) -> {
                pressedUntil = System.currentTimeMillis() + 140;
                action.run();
                return true;
            });
        }

        void refit(double scale) {
            setPosition(BEZEL + (int) Math.floor(dmiX * scale), BEZEL + (int) Math.floor(dmiY * scale));
            setSize((int) Math.ceil(dmiW * scale), (int) Math.ceil(dmiH * scale));
        }

        private boolean hovered() {
            double mx = getLocalMouseX();
            double my = getLocalMouseY();
            return mx >= 0 && my >= 0 && mx < width() && my < height();
        }

        @Override
        public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
            boolean isPressed = System.currentTimeMillis() < pressedUntil;
            if (hovered()) {
                int alpha = isPressed ? 0x40 : 0x22;
                DLColor tint = DLColor.fromInt((alpha << 24) | (AMBER & 0xFFFFFF));
                GuiUtils.fill(graphics, 0, 0, width(), height(), tint);
            } else if (isPressed) {
                GuiUtils.fill(graphics, 0, 0, width(), height(), DLColor.fromInt((0x30 << 24) | (AMBER & 0xFFFFFF)));
            }
            if (hovered() || isPressed) {
                GuiUtils.fill(graphics, 0, 0, width(), 1, DLColor.fromInt(AMBER));
                GuiUtils.fill(graphics, 0, height() - 1, width(), 1, DLColor.fromInt(AMBER));
                GuiUtils.fill(graphics, 0, 0, 1, height(), DLColor.fromInt(AMBER));
                GuiUtils.fill(graphics, width() - 1, 0, 1, height(), DLColor.fromInt(AMBER));
            }
        }
    }

    /**
     * The glass of the display: renders the one shared DMI ({@link ETCS#renderFocused})
     * at the component origin, scaled to the focused size.
     */
    private class DisplayComponent extends DLGuiComponent {

        DisplayComponent(int x, int y, int w, int h) {
            super(x, y, w, h);
            // Rows of the route guidance / train settings pages are directly clickable.
            // Soft keys sit on top of the glass and consume their own clicks first.
            addEventListener(DLGuiStandardEvents.ClickEvent.class, (src, e) -> {
                ETCS etcs = etcs();
                if (etcs == null) return false;
                if (!etcs.routeSettingsClick(e.mouseX() / focusedScale, e.mouseY() / focusedScale)) {
                    return false;
                }
                ETCSsounds.playBeepSound();
                return true;
            });
        }

        @Override
        public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
            ETCS etcs = rtrain != null ? rtrain.realism$getETCS() : null;
            if (etcs == null) {
                return;
            }
            GuiGraphics g = graphics.graphics();
            g.pose().pushPose();
            GuiUtils.fill(graphics, 0, 0, width(), height(), DLColor.fromInt(GLASS_EDGE));
            etcs.renderFocused(g, 0, 0, focusedScale);
            g.pose().popPose();
        }

        @Override
        public void tick() {
            super.tick();
            // The screen dies with its power source: switching ETCS off or losing the train
            // takes the display down instead of leaving a frozen image.
            ETCS etcs = rtrain != null ? rtrain.realism$getETCS() : null;
            if (etcs == null || !etcs.toUpdate) {
                closeFocused();
            }
        }
    }
}
