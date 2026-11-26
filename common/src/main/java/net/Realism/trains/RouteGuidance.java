package net.Realism.trains;

import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;

import java.util.*;

/**
 * Per-player manual route guidance ("drive to this station").
 * <p>
 * Sessions only: nothing here is persisted to disk. The destination is owned by the player,
 * active only while they control the train, and cleared when they disconnect.
 * <p>
 * The transient fields (path, distance, ...) are only meaningful on the logical server, which is
 * where the train simulation, the ETCS scan and the steering hook run.
 */
public class RouteGuidance {

    /** Player-selected station name. Empty means no destination. */
    public String destinationName = "";

    /* --- latched travel direction (server only) --- */
    /** True while the driver heads towards the train's structural front. */
    public boolean drivingTowardFront = true;
    /** False until the direction was seen from actual movement or derived from the seat once. */
    public boolean drivingDirectionKnown = false;

    // --- transient, server only ---
    public UUID ownerId = null;
    public UUID trainId = null;
    public UUID resolvedStationId = null;
    public List<Couple<TrackNode>> path = new ArrayList<>();
    public double distanceToDestination = 0;
    public boolean destinationBehindTrain = false;
    public long lastPathTick = -1;
    public boolean valid = false;
    /**
     * Signal groups claimed by the guidance for this tick. Rebuilt by
     * {@link RouteGuidanceManager#tickSignalReservation} every tick; used by the ETCS scan to
     * treat our own reservation as cleared instead of occupied.
     */
    public final Set<UUID> reservedGroups = new HashSet<>();

    public boolean hasDestination() {
        return destinationName != null && !destinationName.isEmpty();
    }

    /** Stops the current path from being consumed while retaining the requested destination. */
    public void invalidate() {
        resolvedStationId = null;
        path = new ArrayList<>();
        distanceToDestination = 0;
        destinationBehindTrain = false;
        valid = false;
        // reservedGroups is intentionally left alone: a mid-tick path recompute must not make the
        // ETCS forget about reservations that are still active until the next reservation pass.
    }

    public void clear() {
        destinationName = "";
        drivingTowardFront = true;
        drivingDirectionKnown = false;
        ownerId = null;
        trainId = null;
        resolvedStationId = null;
        path = new ArrayList<>();
        distanceToDestination = 0;
        destinationBehindTrain = false;
        lastPathTick = -1;
        valid = false;
        reservedGroups.clear();
    }
}
