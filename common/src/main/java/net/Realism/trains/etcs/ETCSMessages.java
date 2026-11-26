package net.Realism.trains.etcs;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ETCS-wide onboard message queue (client side only).
 * <p>
 * Any subsystem (route guidance today, supervision or modes tomorrow) can post transient or
 * sticky messages. The ETCS display renders the newest entry in the message window with a
 * severity-colored border and an {@code HH:MM} timestamp, like a real DMI message window.
 * <p>
 * Entries with a stable {@code id} replace each other in the live window instead of stacking;
 * sticky entries ({@code durationMs <= 0}) stay until explicitly cleared. Every message is also
 * kept in a per-drive archive so the driver can page back through everything that came up during
 * the current drive with the "older message" bezel key.
 */
public final class ETCSMessages {

    public enum Severity {
        INFO(0xFF9AA0AA),
        ADVISORY(0xFFFFB300),
        WARNING(0xFFFF5555);

        public final int border;

        Severity(int border) {
            this.border = border;
        }
    }

    public record Entry(String id, Severity severity, String text, String time, long expiresAt) {
        public boolean sticky() {
            return expiresAt <= 0;
        }
    }

    /** Live window: only unexpired messages, capped so the newest ones stay reachable. */
    private static final Deque<Entry> QUEUE = new ArrayDeque<>();
    /** Complete message log of the current drive; never pruned by expiry. */
    private static final Deque<Entry> ARCHIVE = new ArrayDeque<>();
    private static final int MAX_ACTIVE = 5;
    private static final int MAX_ARCHIVE = 200;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** The train whose drive the archive belongs to; a different train starts a new log. */
    private static UUID driveId = null;

    /** 0 shows the newest live entry; larger values walk back through the archive. */
    private static int scrollOffset = 0;

    private ETCSMessages() {}

    // ------------------------------------------------------------------ drive lifecycle

    /**
     * Starts a fresh archive when the driver takes over a different train. Re-entering the
     * controls of the same train (briefly leaving the seat) keeps the current drive's log.
     */
    public static void beginDrive(UUID id) {
        if (Objects.equals(driveId, id)) return;
        driveId = id;
        clearAll();
    }

    /** Ends the current drive, dropping the archive (disconnect, world change). */
    public static void endDrive() {
        driveId = null;
        clearAll();
    }

    public static boolean isCurrentDrive(UUID id) {
        return Objects.equals(driveId, id);
    }

    private static void clearAll() {
        QUEUE.clear();
        ARCHIVE.clear();
        scrollOffset = 0;
    }

    // ------------------------------------------------------------------ message production

    public static void push(String id, Severity severity, String text, long durationMs) {
        prune();
        if (id != null) QUEUE.removeIf(e -> id.equals(e.id()));
        long expiresAt = durationMs <= 0 ? 0 : System.currentTimeMillis() + durationMs;
        Entry entry = new Entry(id, severity, text, LocalTime.now().format(TIME_FORMAT), expiresAt);
        QUEUE.addLast(entry);
        while (QUEUE.size() > MAX_ACTIVE) QUEUE.removeFirst();
        ARCHIVE.addLast(entry);
        while (ARCHIVE.size() > MAX_ARCHIVE) ARCHIVE.removeFirst();
        // A fresh message always takes the window back to the newest entry.
        scrollOffset = 0;
    }

    /** Removes a message from the live window. The archive keeps it as drive history. */
    public static void clear(String id) {
        if (id != null) QUEUE.removeIf(e -> id.equals(e.id()));
        clampScroll();
    }

    // ------------------------------------------------------------------ archive navigation

    /**
     * Scrolls the message window: positive walks back through the drive archive, negative
     * returns towards the live messages. Ignored when there is nothing to scroll.
     */
    public static void scroll(int delta) {
        prune();
        scrollOffset = Math.max(0, Math.min(maxScrollOffset(), scrollOffset + delta));
    }

    /** 0 while the live window is shown, 1 for the first archived entry before it, etc. */
    public static int scrollOffset() {
        prune();
        clampScroll();
        return scrollOffset;
    }

    /** The entry currently shown: the newest live message at offset 0, archive entries behind. */
    public static Entry current() {
        prune();
        if (ARCHIVE.isEmpty()) {
            scrollOffset = 0;
            return null;
        }
        clampScroll();
        if (scrollOffset == 0) return QUEUE.peekLast();

        Entry live = QUEUE.peekLast();
        int archivedOffset = 0;
        Iterator<Entry> it = ARCHIVE.descendingIterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry == live) continue;
            if (++archivedOffset == scrollOffset) return entry;
        }
        return null;
    }

    /** 1-based position of the shown entry from the newest archived message. */
    public static int displayPosition() {
        prune();
        if (ARCHIVE.isEmpty()) return 0;
        Entry shown = current();
        if (shown == null) return 0;
        int index = indexOf(shown);
        return index < 0 ? 0 : ARCHIVE.size() - index;
    }

    /** Number of messages stored for this drive (live and expired). */
    public static int archiveCount() {
        return ARCHIVE.size();
    }

    /** Number of messages currently in the live window. */
    public static int activeCount() {
        prune();
        return QUEUE.size();
    }

    /** Furthest valid scroll offset: every archive entry except the current live one. */
    private static int maxScrollOffset() {
        if (ARCHIVE.isEmpty()) return 0;
        Entry live = QUEUE.peekLast();
        return ARCHIVE.size() - (live != null && indexOf(live) >= 0 ? 1 : 0);
    }

    private static void clampScroll() {
        scrollOffset = Math.max(0, Math.min(maxScrollOffset(), scrollOffset));
    }

    private static int indexOf(Entry entry) {
        int i = 0;
        for (Entry e : ARCHIVE) {
            if (e == entry) return i;
            i++;
        }
        return -1;
    }

    // ------------------------------------------------------------------ expiry

    private static void prune() {
        long now = System.currentTimeMillis();
        Iterator<Entry> it = QUEUE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (!e.sticky() && e.expiresAt() < now) it.remove();
        }
    }
}
