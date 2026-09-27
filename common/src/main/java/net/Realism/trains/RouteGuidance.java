package net.Realism.trains;

import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-player manual route guidance ("drive to this station").
 * <p>
 * Sessions only: nothing here is persisted to disk. The destination and the auto-steer flag are
 * owned by the player and cleared when they leave the train controls or disconnect.
 * <p>
 * The transient fields (path, distance, ...) are only meaningful on the logical server, which is
 * where the train simulation, the ETCS scan and the steering hook run.
 */
public class RouteGuidance {

    /** Player-selected station name. Empty means no destination. */
    public String destinationName = "";
    /** Whether the mod should follow the route at junctions (player keeps manual throttle). */
    public boolean autoSteer = false;

    // --- transient, server only ---
    public UUID trainId = null;
    public UUID resolvedStationId = null;
    public List<Couple<TrackNode>> path = new ArrayList<>();
    public double distanceToDestination = 0;
    public boolean destinationBehindTrain = false;
    public long lastPathTick = -1;
    public boolean valid = false;

    public boolean hasDestination() {
        return destinationName != null && !destinationName.isEmpty();
    }

    public void clear() {
        destinationName = "";
        autoSteer = false;
        trainId = null;
        resolvedStationId = null;
        path = new ArrayList<>();
        distanceToDestination = 0;
        destinationBehindTrain = false;
        lastPathTick = -1;
        valid = false;
    }
}
