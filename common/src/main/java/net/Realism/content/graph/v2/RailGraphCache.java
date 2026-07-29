package net.Realism.content.graph.v2;

import com.simibubi.create.content.trains.graph.TrackGraph;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side cache of translated graphs. Entries are reused while Create's
 * node checksum is unchanged and the entry is younger than the staleness
 * timeout (the checksum only tracks nodes, so signal/station edits are picked
 * up by the timeout).
 */
public class RailGraphCache {

    private record Entry(RailGraphTranslator.Result result, int checksum, long timestamp) {}

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
    private static final long MAX_AGE_MS = 10_000;

    /** Must be called on the server thread (translates on miss). */
    public static RailGraphTranslator.Result get(TrackGraph graph, int nodeCap) {
        long now = System.currentTimeMillis();
        int checksum = graph.getChecksum();
        Entry entry = CACHE.get(graph.id);
        if (entry != null && entry.checksum() == checksum && now - entry.timestamp() < MAX_AGE_MS
                && entry.result().graph() != null)
            return entry.result();
        RailGraphTranslator.Result result = RailGraphTranslator.translate(graph, nodeCap);
        CACHE.put(graph.id, new Entry(result, checksum, now));
        return result;
    }

    public static void clear() {
        CACHE.clear();
    }
}
