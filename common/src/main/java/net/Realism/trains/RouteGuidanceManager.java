package net.Realism.trains;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.Realism.RNetworking;
import net.Realism.network.RouteGuidanceUpdatePacket;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every player's {@link RouteGuidance}. The server map is authoritative and drives the ETCS
 * scan and the auto-steer selector; the client map backs the settings GUI for the local player.
 */
public final class RouteGuidanceManager {

    /** How often (in game ticks) the route is recomputed for the ETCS scan. */
    private static final long REFRESH_TICKS = 10;

    private static final Map<UUID, RouteGuidance> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, RouteGuidance> CLIENT = new ConcurrentHashMap<>();

    private RouteGuidanceManager() {}

    // ------------------------------------------------------------------ storage

    public static RouteGuidance getServer(UUID player) {
        return SERVER.computeIfAbsent(player, k -> new RouteGuidance());
    }

    public static RouteGuidance getClient(UUID player) {
        return CLIENT.computeIfAbsent(player, k -> new RouteGuidance());
    }

    public static void setClient(UUID player, String destinationName, boolean autoSteer) {
        RouteGuidance g = getClient(player);
        g.destinationName = destinationName == null ? "" : destinationName;
        g.autoSteer = autoSteer;
    }

    public static void clearClient(UUID player) {
        RouteGuidance g = CLIENT.get(player);
        if (g != null) g.clear();
    }

    public static void clearServer(UUID player) {
        SERVER.remove(player);
    }

    // ------------------------------------------------------------------ resolution

    /** The player currently at the controls of this train, if any. */
    public static UUID controllingPlayer(Train train) {
        if (train == null) return null;
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity == null) continue;
            var controlling = entity.getControllingPlayer();
            if (controlling.isPresent()) return controlling.get();
        }
        return null;
    }

    /** The train the given server player is currently controlling, if any. */
    public static Train controllingTrain(ServerPlayer player) {
        if (player.getVehicle() instanceof CarriageContraptionEntity cce) {
            Carriage carriage = cce.getCarriage();
            if (carriage != null) return carriage.train;
        }
        return null;
    }

    /**
     * The guidance a train should act on right now, or null if there is none.
     * Only the controlling player's guidance is considered.
     */
    public static RouteGuidance getForTrain(Train train) {
        UUID player = controllingPlayer(train);
        if (player == null) return null;
        RouteGuidance g = SERVER.get(player);
        if (g == null || !g.hasDestination()) return null;
        if (g.trainId != null && !g.trainId.equals(train.id)) return null;
        return g;
    }

    // ------------------------------------------------------------------ path computation

    /**
     * Recomputes the route using Create's own {@code Navigation.findPathTo}. Returns false (and
     * marks the guidance invalid) when the station cannot be found or no drivable path exists.
     */
    public static boolean recomputePath(Train train, RouteGuidance g) {
        if (g == null) return false;
        g.valid = false;
        // Stamp even a failed attempt so an unreachable destination is not searched every tick.
        if (train != null) g.lastPathTick = gameTime(train);
        if (train == null || train.graph == null || !g.hasDestination()) return false;

        GlobalStation station = findStationByName(train, g.destinationName);
        if (station == null) return false;

        // Same entry point base Create uses for schedules and the "approach station" prompt:
        // it searches both directions and returns whichever is drivable (conductor rules apply).
        DiscoveredPath found = train.navigation.findPathTo(station, -1);
        if (found == null || found.path == null) return false;

        g.trainId = train.id;
        g.resolvedStationId = station.id;
        g.path = new ArrayList<>(found.path);
        g.distanceToDestination = found.distance;
        g.destinationBehindTrain = found.distance < 0;
        g.valid = true;
        return true;
    }

    /** Recomputes only when the cached route is missing or older than {@link #REFRESH_TICKS}. */
    public static boolean ensurePath(Train train, RouteGuidance g) {
        if (g == null || !g.hasDestination()) return false;
        long now = gameTime(train);
        if (g.lastPathTick >= 0 && now - g.lastPathTick < REFRESH_TICKS) return g.valid;
        return recomputePath(train, g);
    }

    private static GlobalStation findStationByName(Train train, String name) {
        if (train.graph == null || name == null) return null;
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
            if (station.name != null && station.name.equals(name)) return station;
        }
        return null;
    }

    private static long gameTime(Train train) {
        CarriageContraptionEntity entity = train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
        if (entity != null && entity.level() != null) return entity.level().getGameTime();
        return System.currentTimeMillis() / 50L;
    }

    // ------------------------------------------------------------------ steering

    /**
     * Selector used by {@code Navigation.control} when auto-steer is enabled. Follows the route
     * edge at junctions and falls back to the player's manual steer otherwise.
     */
    public static TravellingPoint.ITrackSelector createSteeringSelector(Train train, RouteGuidance g,
                                                                       TravellingPoint.ITrackSelector fallback) {
        return (graph, pair) -> {
            List<Map.Entry<TrackNode, TrackEdge>> valid = pair.getSecond();

            Map.Entry<TrackNode, TrackEdge> match = matchHead(graph, g.path, valid);
            if (match != null) {
                g.path.remove(0);
                return match;
            }

            if (recomputePath(train, g)) {
                match = matchHead(graph, g.path, valid);
                if (match != null) {
                    g.path.remove(0);
                    return match;
                }
            }

            return fallback.apply(graph, pair);
        };
    }

    private static Map.Entry<TrackNode, TrackEdge> matchHead(TrackGraph graph,
                                                             List<Couple<TrackNode>> path,
                                                             List<Map.Entry<TrackNode, TrackEdge>> valid) {
        if (path.isEmpty()) return null;
        TrackEdge target = graph.getConnection(path.get(0));
        if (target == null) return null;
        for (Map.Entry<TrackNode, TrackEdge> entry : valid) {
            if (entry.getValue() == target) return entry;
        }
        return null;
    }

    // ------------------------------------------------------------------ entry point from the packet

    /**
     * Applies a client request. Runs on the server, validated against the sender's controlling train.
     */
    public static void apply(ServerPlayer player, String destinationName, boolean autoSteer) {
        UUID id = player.getUUID();

        if (destinationName == null || destinationName.isBlank()) {
            getServer(id).clear();
            RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket("", false, true), player);
            return;
        }

        Train train = controllingTrain(player);
        RouteGuidance g = getServer(id);
        g.clear();
        g.destinationName = destinationName;
        g.autoSteer = autoSteer;

        if (train != null && recomputePath(train, g)) {
            RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket(g.destinationName, g.autoSteer, true), player);
            return;
        }

        // Failed: keep the map clean and tell the player.
        g.clear();
        RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket(destinationName, autoSteer, false), player);
    }
}
