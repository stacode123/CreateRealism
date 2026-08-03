package net.Realism.compat;

import de.mrjulsen.crn.data.StationTag;
import de.mrjulsen.crn.data.storage.GlobalSettings;
import net.Realism.RealismExpectPlatform;
import net.Realism.RealismMod;

import java.util.*;

/**
 * Create Railways Navigator integration, guarded like {@link TramwaysCompat}:
 * CRN classes are compile-only, everything is reached through methods that
 * check the mod is loaded and swallow linkage errors.
 */
public class CrnCompat {

    private static final boolean crnLoaded =
            RealismExpectPlatform.isModLoaded("createrailwaysnavigator");

    /**
     * Station name → tag display name from CRN's server-side station tags —
     * the player-curated grouping of platforms ("Radom 1", "Radom 2") under
     * one station ("Radom"). Stations in several tags keep the first tag in
     * name order. Empty when CRN is absent or has no data yet.
     */
    public static Map<String, String> stationTagGroups() {
        if (!crnLoaded)
            return Map.of();
        try {
            return stationTagGroupsImpl();
        } catch (Throwable t) {
            RealismMod.LOGGER.error("Failed to read CRN station tags", t);
            return Map.of();
        }
    }

    /**
     * Raw station names on CRN's station blacklist (exact-match, the same
     * semantics as CRN's own {@code isStationBlacklisted}). Empty when CRN
     * is absent or has no data yet.
     */
    public static Set<String> blacklistedStations() {
        if (!crnLoaded)
            return Set.of();
        try {
            return blacklistedStationsImpl();
        } catch (Throwable t) {
            RealismMod.LOGGER.error("Failed to read CRN station blacklist", t);
            return Set.of();
        }
    }

    private static Set<String> blacklistedStationsImpl() {
        if (!GlobalSettings.hasInstance())
            return Set.of();
        return new HashSet<>(GlobalSettings.getInstance().getAllBlacklistedStations());
    }

    /** Train category id → display name, from CRN's server-side settings. */
    public static Map<String, String> trainCategoryNames() {
        if (!crnLoaded)
            return Map.of();
        try {
            return trainCategoryNamesImpl();
        } catch (Throwable t) {
            RealismMod.LOGGER.error("Failed to read CRN train categories", t);
            return Map.of();
        }
    }

    private static Map<String, String> trainCategoryNamesImpl() {
        if (!GlobalSettings.hasInstance())
            return Map.of();
        Map<String, String> names = new HashMap<>();
        for (de.mrjulsen.crn.data.TrainCategory category
                : GlobalSettings.getInstance().getAllTrainCategories())
            names.put(category.getId().toString(), category.getCategoryName());
        return names;
    }

    private static Map<String, String> stationTagGroupsImpl() {
        if (!GlobalSettings.hasInstance())
            return Map.of();
        List<StationTag> tags = new ArrayList<>(GlobalSettings.getInstance().getAllStationTags());
        tags.sort(Comparator.comparing(tag -> tag.getTagName().get()));
        Map<String, String> groups = new HashMap<>();
        for (StationTag tag : tags) {
            String name = tag.getTagName().get();
            if (name == null || name.isBlank())
                continue;
            for (String station : tag.getAllStationNames())
                groups.putIfAbsent(station, name);
        }
        return groups;
    }
}
