package net.Realism.content.gui;

import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.Realism.RNetworking;
import net.Realism.content.simulator.SimulationPayload;
import net.Realism.content.simulator.core.SimConflict;
import net.Realism.content.simulator.core.SimResult;
import net.Realism.foundation.network.RequestGraphViewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * M3 presentation of a simulation, organized in two panels: the phantom's
 * timetable on the left (paged, with travel times between stops), and a
 * paged list on the right — notes, then every other train by name. A +/-
 * button on a train row expands it in place to its details (stops, end
 * state, exclusion reason). Clamped lines show their full text as a hover
 * tooltip. Conflicts lead the right panel (M4); a Map button per conflict
 * jumps to its spot on the rail map and the Diagram button opens the
 * time-distance diagram (M5).
 */
public class SimulationResultsWindow extends DLWindow {

    private static final int LEFT_X = 15;
    private static final int TIMETABLE_TOP = 56;
    private static final int RIGHT_TOP = 40;
    private static final int RIGHT_ROW_HEIGHT = 15;

    // Layout is computed from the logical screen size: the window fills the
    // screen (centered), floored at 470x248 — the usable area at GUI scale 4
    // on 1080p-class screens — and capped at 800 wide, past which the two
    // text panels gain whitespace, not information. Extra height becomes
    // extra rows per page.
    private final int windowWidth;
    private final int windowHeight;
    private final int timetableRows;
    private final int rightRows;
    private final int leftWidth;
    private final int rightX;
    private final int rightWidth;
    private final int pagerY;

    /** Display line + untruncated original (equal when nothing was cut). */
    private record Line(String display, String full) {

        static Line of(String full, int width) {
            return new Line(fit(full, width), full);
        }

        boolean clamped() {
            return !display.equals(full);
        }
    }

    /** Ellipsis-truncates a line so it can never overflow its panel. */
    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width)
            return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    // Left panel: paged timetable.
    private final List<Line> timetableLines = new ArrayList<>();
    private final DLRichTextLabel[] timetableLabels;
    private final String[] timetableTooltips;
    private DLRichTextLabel pageLabel;
    private int page;

    // Right panel: one scrollable list with expandable train/conflict rows.
    private enum RowKind { HEADER, PLAIN, TRAIN, CONFLICT, ROOT, DETAIL }

    /** {@code index} points into rightTrains/rightConflicts/rightRoots by kind. */
    private record Row(String text, RowKind kind, int index) {}

    private record RightTrain(String name, List<String> detail, boolean excludedGroup) {}

    private record RightConflict(String title, List<String> detail, boolean yours) {}

    private record RightRoot(String title, List<String> detail) {}

    private final List<String> rightNotes = new ArrayList<>();
    private final List<RightTrain> rightTrains = new ArrayList<>();
    private final List<RightConflict> rightConflicts = new ArrayList<>();
    private final List<RightRoot> rightRoots = new ArrayList<>();
    private final Set<Integer> expandedTrains = new HashSet<>();
    private final Set<Integer> expandedConflicts = new HashSet<>();
    private final Set<Integer> expandedRoots = new HashSet<>();
    private int conflictsDropped;
    private boolean thoroughDiff;
    private final List<Row> rightRowList = new ArrayList<>();
    private final DLRichTextLabel[] rightLabels;
    private final String[] rightTooltips;
    private final DLButton[] rightToggleButtons;
    /** Per row: jumps to the conflict's spot on the rail map (M5). */
    private final DLButton[] rightMapButtons;
    private DLRichTextLabel rightPageLabel;
    private int rightPage;

    private final SimulationPayload payload;

    public SimulationResultsWindow(DLWindowManager manager, SimulationPayload payload) {
        super(manager);
        this.payload = payload;
        var mcWindow = Minecraft.getInstance().getWindow();
        int screenWidth = mcWindow.getGuiScaledWidth();
        int screenHeight = mcWindow.getGuiScaledHeight();
        windowWidth = Math.min(800, Math.max(470, screenWidth - 20));
        windowHeight = Math.max(248, screenHeight - 20);
        setSize(windowWidth, windowHeight);
        setPosition(Math.max(0, (screenWidth - windowWidth) / 2),
                Math.max(0, (screenHeight - windowHeight) / 2));

        pagerY = windowHeight - 46;
        timetableRows = (pagerY - 4 - TIMETABLE_TOP) / RIGHT_ROW_HEIGHT;
        rightRows = (pagerY - 4 - RIGHT_TOP) / RIGHT_ROW_HEIGHT;
        int contentWidth = windowWidth - 40;
        leftWidth = Math.round(contentWidth * 0.55f);
        rightWidth = contentWidth - leftWidth;
        rightX = LEFT_X + leftWidth + 10;
        timetableLabels = new DLRichTextLabel[timetableRows];
        timetableTooltips = new String[timetableRows];
        rightLabels = new DLRichTextLabel[rightRows];
        rightTooltips = new String[rightRows];
        rightToggleButtons = new DLButton[rightRows];
        rightMapButtons = new DLButton[rightRows];

        DLRichTextLabel title = addComponent(new DLRichTextLabel(windowWidth / 2 - 100, 8, 200, 20));
        title.text.get().set(Component.translatable("realism.gui.simres.title").getString());

        if (payload.refused()) {
            int y = wrappedBlock(LEFT_X, 26, windowWidth - 30,
                    Component.translatable("realism.gui.simres.refused").getString()) + 2;
            List<SimulationPayload.Refusal> refusals = payload.refusals;
            for (int i = 0; i < refusals.size(); i++) {
                if (i == 6 && refusals.size() > 7) {
                    wrappedBlock(LEFT_X + 6, y, windowWidth - 36, Component.translatable(
                            "realism.gui.simres.more", refusals.size() - i).getString());
                    break;
                }
                y = wrappedBlock(LEFT_X + 6, y, windowWidth - 36, "- " + Component.translatable(
                        refusals.get(i).translationKey(), refusals.get(i).detail()).getString());
            }
            buildCloseButton();
            return;
        }

        String meta = Component.translatable("realism.gui.simres.meta",
                payload.horizonTicks / 1000, formatTime(payload, 0)).getString();
        if (payload.truncated)
            meta += " " + Component.translatable("realism.gui.simres.truncated").getString();
        DLRichTextLabel metaLabel = addComponent(new DLRichTextLabel(LEFT_X, 22, windowWidth - 30, 14));
        metaLabel.text.get().set(meta);

        SimulationPayload.TrainLine phantom = payload.trains.stream()
                .filter(SimulationPayload.TrainLine::phantom).findFirst().orElse(null);
        DLRichTextLabel phantomHeader = addComponent(new DLRichTextLabel(LEFT_X, 40, leftWidth, 14));
        phantomHeader.text.get().set(Component.translatable("realism.gui.simres.phantom").getString());
        if (phantom != null)
            buildTimetableLines(payload, phantom);
        buildTimetablePanel(TIMETABLE_TOP);

        buildRightModel(payload, phantom);
        buildRightPanel();

        buildCloseButton();
    }

    // ------------------------------------------------------------------
    // Left panel: timetable with travel times
    // ------------------------------------------------------------------

    private void buildTimetableLines(SimulationPayload payload, SimulationPayload.TrainLine phantom) {
        List<SimulationPayload.Visit> visits = phantom.visits();
        for (int i = 0; i < visits.size(); i++) {
            SimulationPayload.Visit visit = visits.get(i);
            if (i > 0 && visits.get(i - 1).departureTick() >= 0)
                timetableLines.add(new Line(Component.translatable("realism.gui.simres.travel",
                        formatDuration(payload, visit.arrivalTick() - visits.get(i - 1).departureTick()))
                        .getString(), ""));
            String departure;
            if (visit.departureTick() >= 0)
                departure = formatTime(payload, visit.departureTick());
            else if (i == visits.size() - 1 && "WAITING".equals(phantom.endState()))
                departure = Component.translatable("realism.gui.simres.after_window").getString();
            else
                departure = "—";
            // Times first: if anything must clip, it's the name's tail.
            timetableLines.add(Line.of(Component.translatable("realism.gui.simres.stop",
                    visit.stationName(), formatTime(payload, visit.arrivalTick()), departure)
                    .getString(), leftWidth));
        }
        if (visits.isEmpty())
            timetableLines.add(new Line(
                    Component.translatable("realism.gui.simres.no_stops").getString(), ""));
    }

    private void buildTimetablePanel(int top) {
        for (int i = 0; i < timetableRows; i++) {
            int row = i;
            timetableLabels[i] = addComponent(
                    tooltipLabel(LEFT_X, top + i * 15, leftWidth, () -> timetableTooltips[row]));
        }
        int pages = pageCount();
        if (pages > 1) {
            DLButton previous = addComponent(new DLButton(LEFT_X, pagerY, 20, 16));
            previous.text.set(Component.literal("<"));
            previous.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
                page = Math.max(0, page - 1);
                refreshTimetable();
                return false;
            });
            DLButton next = addComponent(new DLButton(LEFT_X + 100, pagerY, 20, 16));
            next.text.set(Component.literal(">"));
            next.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
                page = Math.min(pageCount() - 1, page + 1);
                refreshTimetable();
                return false;
            });
            pageLabel = addComponent(new DLRichTextLabel(LEFT_X + 28, pagerY + 2, 70, 14));
        }
        refreshTimetable();
    }

    private int pageCount() {
        return Math.max(1, (timetableLines.size() + timetableRows - 1) / timetableRows);
    }

    private void refreshTimetable() {
        for (int i = 0; i < timetableRows; i++) {
            int index = page * timetableRows + i;
            if (index < timetableLines.size()) {
                Line line = timetableLines.get(index);
                timetableLabels[i].text.get().set(line.display());
                timetableTooltips[i] = line.clamped() ? line.full() : null;
            } else {
                timetableLabels[i].text.get().set("");
                timetableTooltips[i] = null;
            }
        }
        if (pageLabel != null)
            pageLabel.text.get().set(Component.translatable("realism.gui.simres.page",
                    page + 1, pageCount()).getString());
    }

    // ------------------------------------------------------------------
    // Right panel: scrollable notes + expandable train list
    // ------------------------------------------------------------------

    private void buildRightModel(SimulationPayload payload, SimulationPayload.TrainLine phantom) {
        thoroughDiff = payload.thorough;
        String yourTrain = Component.translatable("realism.gui.simres.your_train").getString();
        for (SimulationPayload.RootCauseLine cause : payload.rootCauses) {
            String kind = SimResult.RootCauseKind.values()[cause.kind()].name();
            String rootName = "phantom".equals(cause.rootName()) ? yourTrain : cause.rootName();
            String title = rootName + " - " + Component.translatable(
                    "realism.gui.simres.root." + kind, cause.detail()).getString();
            List<String> names = new ArrayList<>();
            for (String name : cause.strandedNames())
                names.add("phantom".equals(name) ? yourTrain : name);
            if (cause.strandedCount() > names.size())
                names.add(Component.translatable("realism.gui.simres.root.more",
                        cause.strandedCount() - names.size()).getString());
            List<String> detail = new ArrayList<>();
            detail.add(Component.translatable("realism.gui.simres.root.stuck",
                    cause.strandedCount(), String.join(", ", names)).getString());
            detail.add(Component.translatable("realism.gui.simres.root.since",
                    formatTime(payload, cause.sinceTick())).getString());
            detail.add(Component.translatable("realism.gui.simres.conflict.pos",
                    cause.x(), cause.y(), cause.z()).getString());
            rightRoots.add(new RightRoot(title, detail));
        }
        for (SimulationPayload.ConflictLine conflict : payload.conflicts) {
            String type = Component.translatable("realism.gui.simres.conflict."
                    + SimConflict.Type.values()[conflict.type()].name()).getString();
            String place = conflict.resourceName().isEmpty()
                    ? "(" + conflict.x() + ", " + conflict.y() + ", " + conflict.z() + ")"
                    : conflict.resourceName();
            String title = type + " - " + place
                    + (conflict.count() > 1 ? " x" + conflict.count() : "");
            boolean yours = conflict.trainNames().contains("phantom");
            List<String> names = conflict.trainNames().stream()
                    .map(name -> "phantom".equals(name) ? yourTrain : name).toList();
            List<String> detail = new ArrayList<>();
            detail.add(Component.translatable("realism.gui.simres.conflict.window",
                    formatTime(payload, conflict.startTick()),
                    formatTime(payload, conflict.endTick())).getString());
            detail.add(Component.translatable("realism.gui.simres.conflict.trains",
                    String.join(", ", names)).getString());
            if (!conflict.resourceName().isEmpty())
                detail.add(Component.translatable("realism.gui.simres.conflict.pos",
                        conflict.x(), conflict.y(), conflict.z()).getString());
            if (conflict.nonDeterministic())
                detail.add(Component.translatable("realism.gui.simres.conflict.nondet").getString());
            rightConflicts.add(new RightConflict(title, detail, yours));
        }
        conflictsDropped = payload.conflictsDropped;

        if (payload.thorough)
            rightNotes.add(Component.translatable("realism.gui.simres.thorough_note").getString());
        if (phantom != null)
            for (String notice : phantom.notices())
                rightNotes.add(noticeText(notice));
        if (!payload.perfSummary.isEmpty())
            rightNotes.add(Component.translatable("realism.gui.simres.perf",
                    payload.perfSummary).getString());

        for (SimulationPayload.TrainLine train : payload.trains) {
            if (train.phantom() || train.obstacle())
                continue;
            List<String> detail = new ArrayList<>();
            detail.add(Component.translatable("realism.gui.simres.detail.stops",
                    train.visits().size()).getString());
            detail.add(Component.translatable("realism.gui.simres.detail.state",
                    Component.translatable("realism.gui.simres.state." + train.endState())
                            .getString()).getString());
            for (String notice : train.notices())
                detail.add(noticeText(notice));
            rightTrains.add(new RightTrain(train.name(), detail, false));
        }
        for (SimulationPayload.ExcludedLine line : payload.excluded)
            rightTrains.add(new RightTrain(line.trainName(), List.of(Component
                    .translatable(line.translationKey(), line.detail()).getString()), true));

        flattenRight();
    }

    /** Rebuilds the flat row list from conflicts, notes, trains, expansion state. */
    private void flattenRight() {
        rightRowList.clear();
        if (!rightRoots.isEmpty()) {
            rightRowList.add(new Row(Component.translatable(
                    "realism.gui.simres.rootcauses").getString(), RowKind.HEADER, -1));
            for (int i = 0; i < rightRoots.size(); i++) {
                RightRoot root = rightRoots.get(i);
                rightRowList.add(new Row("     " + root.title(), RowKind.ROOT, i));
                if (expandedRoots.contains(i))
                    for (String detail : root.detail())
                        for (String part : wrap(detail, rightWidth - 12))
                            rightRowList.add(new Row("    " + part, RowKind.DETAIL, i));
            }
        }
        if (rightConflicts.isEmpty()) {
            rightRowList.add(new Row(Component.translatable(thoroughDiff
                    ? "realism.gui.simres.no_conflicts.thorough"
                    : "realism.gui.simres.no_conflicts").getString(), RowKind.PLAIN, -1));
        } else {
            addConflictGroup(true, "realism.gui.simres.conflicts.yours");
            addConflictGroup(false, "realism.gui.simres.conflicts.network");
            if (conflictsDropped > 0)
                rightRowList.add(new Row(" " + Component.translatable(
                        "realism.gui.simres.more_conflicts", conflictsDropped).getString(),
                        RowKind.PLAIN, -1));
        }
        if (!rightNotes.isEmpty()) {
            rightRowList.add(new Row(Component.translatable("realism.gui.simres.notes").getString(),
                    RowKind.HEADER, -1));
            for (String note : rightNotes)
                for (String part : wrap(note, rightWidth - 6))
                    rightRowList.add(new Row(" " + part, RowKind.PLAIN, -1));
        }
        addTrainGroup(false, "realism.gui.simres.others");
        addTrainGroup(true, "realism.gui.simres.excluded");
    }

    /** Phantom-involving conflicts first, then the rest of the network. */
    private void addConflictGroup(boolean yours, String headerKey) {
        boolean any = false;
        for (int i = 0; i < rightConflicts.size(); i++) {
            RightConflict conflict = rightConflicts.get(i);
            if (conflict.yours() != yours)
                continue;
            if (!any) {
                rightRowList.add(new Row(Component.translatable(headerKey).getString(),
                        RowKind.HEADER, -1));
                any = true;
            }
            rightRowList.add(new Row("     " + conflict.title(), RowKind.CONFLICT, i));
            if (expandedConflicts.contains(i))
                for (String detail : conflict.detail())
                    for (String part : wrap(detail, rightWidth - 12))
                        rightRowList.add(new Row("    " + part, RowKind.DETAIL, i));
        }
    }

    private void addTrainGroup(boolean excludedGroup, String headerKey) {
        boolean any = false;
        for (int i = 0; i < rightTrains.size(); i++) {
            RightTrain train = rightTrains.get(i);
            if (train.excludedGroup() != excludedGroup)
                continue;
            if (!any) {
                rightRowList.add(new Row(Component.translatable(headerKey).getString(),
                        RowKind.HEADER, -1));
                any = true;
            }
            rightRowList.add(new Row("     " + train.name(), RowKind.TRAIN, i));
            boolean expanded = expandedTrains.contains(i);
            if (expanded)
                for (String detail : train.detail())
                    for (String part : wrap(detail, rightWidth - 12))
                        rightRowList.add(new Row("    " + part, RowKind.DETAIL, i));
        }
    }

    private void buildRightPanel() {
        for (int i = 0; i < rightRows; i++) {
            int row = i;
            rightLabels[i] = addComponent(tooltipLabel(rightX, RIGHT_TOP + i * RIGHT_ROW_HEIGHT,
                    rightWidth, () -> rightTooltips[row]));
            DLButton toggle = addComponent(new DLButton(rightX,
                    RIGHT_TOP + i * RIGHT_ROW_HEIGHT - 1, 14, 13));
            toggle.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
                int index = rightPage * rightRows + row;
                if (index >= rightRowList.size())
                    return false;
                Row rowEntry = rightRowList.get(index);
                Set<Integer> expanded = switch (rowEntry.kind()) {
                    case TRAIN -> expandedTrains;
                    case CONFLICT -> expandedConflicts;
                    case ROOT -> expandedRoots;
                    default -> null;
                };
                if (expanded != null) {
                    if (!expanded.remove(rowEntry.index()))
                        expanded.add(rowEntry.index());
                    flattenRight();
                    refreshRight();
                }
                return false;
            });
            rightToggleButtons[i] = toggle;
            DLButton mapButton = addComponent(new DLButton(rightX + rightWidth - 30,
                    RIGHT_TOP + i * RIGHT_ROW_HEIGHT - 1, 28, 13));
            mapButton.text.set(Component.translatable("realism.gui.simres.map"));
            mapButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
                int index = rightPage * rightRows + row;
                if (index >= rightRowList.size())
                    return false;
                Row rowEntry = rightRowList.get(index);
                if (rowEntry.kind() == RowKind.CONFLICT) {
                    SimulationPayload.ConflictLine conflict = payload.conflicts.get(rowEntry.index());
                    RNetworking.sendToServer(new RequestGraphViewPacket(
                            new BlockPos(conflict.x(), conflict.y(), conflict.z())));
                } else if (rowEntry.kind() == RowKind.ROOT) {
                    SimulationPayload.RootCauseLine cause = payload.rootCauses.get(rowEntry.index());
                    RNetworking.sendToServer(new RequestGraphViewPacket(
                            new BlockPos(cause.x(), cause.y(), cause.z())));
                }
                return false;
            });
            rightMapButtons[i] = mapButton;
        }
        DLButton previous = addComponent(new DLButton(rightX, pagerY, 20, 16));
        previous.text.set(Component.literal("<"));
        previous.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
            rightPage = Math.max(0, rightPage - 1);
            refreshRight();
            return false;
        });
        DLButton next = addComponent(new DLButton(rightX + 100, pagerY, 20, 16));
        next.text.set(Component.literal(">"));
        next.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
            rightPage = Math.min(rightPageCount() - 1, rightPage + 1);
            refreshRight();
            return false;
        });
        rightPageLabel = addComponent(new DLRichTextLabel(rightX + 28, pagerY + 2, 70, 14));
        refreshRight();
    }

    private int rightPageCount() {
        return Math.max(1, (rightRowList.size() + rightRows - 1) / rightRows);
    }

    private void refreshRight() {
        rightPage = Math.min(rightPage, rightPageCount() - 1);
        for (int i = 0; i < rightRows; i++) {
            int index = rightPage * rightRows + i;
            if (index < rightRowList.size()) {
                Row row = rightRowList.get(index);
                String display = fit(row.text(), rightWidth);
                rightLabels[i].text.get().set(display);
                rightTooltips[i] = display.equals(row.text()) ? null : row.text();
                boolean toggleable = row.kind() == RowKind.TRAIN || row.kind() == RowKind.CONFLICT
                        || row.kind() == RowKind.ROOT;
                rightToggleButtons[i].visible.set(toggleable);
                if (toggleable) {
                    Set<Integer> expanded = switch (row.kind()) {
                        case TRAIN -> expandedTrains;
                        case ROOT -> expandedRoots;
                        default -> expandedConflicts;
                    };
                    rightToggleButtons[i].text.set(Component.literal(
                            expanded.contains(row.index()) ? "-" : "+"));
                }
                // Jump-to-map only works in the dimension the player is in.
                String rowDimension = row.kind() == RowKind.CONFLICT
                        ? payload.conflicts.get(row.index()).dimension()
                        : row.kind() == RowKind.ROOT
                                ? payload.rootCauses.get(row.index()).dimension()
                                : null;
                rightMapButtons[i].visible.set(rowDimension != null
                        && Minecraft.getInstance().level != null
                        && rowDimension.equals(
                                Minecraft.getInstance().level.dimension().location().toString()));
            } else {
                rightLabels[i].text.get().set("");
                rightTooltips[i] = null;
                rightToggleButtons[i].visible.set(false);
                rightMapButtons[i].visible.set(false);
            }
        }
        rightPageLabel.text.get().set(Component.translatable("realism.gui.simres.page",
                rightPage + 1, rightPageCount()).getString());
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** A notice is a translation key, optionally "keyargument". */
    private static String noticeText(String notice) {
        int separator = notice.indexOf('\u001F');
        if (separator < 0)
            return Component.translatable(notice).getString();
        return Component.translatable(notice.substring(0, separator),
                notice.substring(separator + 1)).getString();
    }

    /** A label that shows the supplied full text as a tooltip when hovered. */
    private DLRichTextLabel tooltipLabel(int x, int y, int width,
                                         java.util.function.Supplier<String> fullTextSupplier) {
        return new DLRichTextLabel(x, y, width, 14) {
            @Override
            public void renderFrontLayer(DLGuiGraphics graphics, double mouseX, double mouseY,
                                         Rectangle bounds) {
                super.renderFrontLayer(graphics, mouseX, mouseY, bounds);
                String full = fullTextSupplier.get();
                if (full != null && !full.isEmpty() && isMouseOver(mouseX, mouseY))
                    GuiUtils.drawTooltip(graphics, graphics.defaultFont(), (int) mouseX, (int) mouseY,
                            List.of(TextUtils.text(full)), (int) getWindowManager().getScreenWidth());
            }
        };
    }

    /** Adds one plain label per wrapped line (12px leading); returns the y below. */
    private int wrappedBlock(int x, int y, int width, String text) {
        for (String part : wrap(text, width)) {
            DLRichTextLabel label = addComponent(new DLRichTextLabel(x, y, width, 14));
            label.text.get().set(part);
            y += 12;
        }
        return y;
    }

    /** Splits on spaces so every line fits the width; hard-cuts spaceless runs. */
    private static List<String> wrap(String text, int width) {
        var font = Minecraft.getInstance().font;
        List<String> lines = new ArrayList<>();
        String rest = text;
        while (font.width(rest) > width) {
            String head = font.plainSubstrByWidth(rest, width);
            int cut = head.lastIndexOf(' ');
            if (cut < 1)
                cut = Math.max(1, head.length());
            lines.add(rest.substring(0, cut));
            rest = rest.substring(cut).stripLeading();
        }
        if (!rest.isEmpty() || lines.isEmpty())
            lines.add(rest);
        return lines;
    }

    private void buildCloseButton() {
        DLButton closeButton = addComponent(new DLButton(windowWidth / 2 - 40, windowHeight - 24, 80, 20));
        closeButton.text.set(Component.translatable("gui.done"));
        closeButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
            closeWindow();
            return false;
        });
        if (!payload.refused() && !payload.diagramLines.isEmpty()) {
            DLButton diagramButton = addComponent(
                    new DLButton(windowWidth / 2 - 130, windowHeight - 24, 84, 20));
            diagramButton.text.set(Component.translatable("realism.gui.simres.diagram"));
            diagramButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (event, source) -> {
                DLWindow.openWindow(manager -> new TimeDistanceDiagramWindow(manager, payload));
                return false;
            });
        }
    }

    private static String formatTime(SimulationPayload payload, long tick) {
        return SimTimeFormat.time(payload, tick);
    }

    private static String formatDuration(SimulationPayload payload, long ticks) {
        return SimTimeFormat.duration(payload, ticks);
    }
}
