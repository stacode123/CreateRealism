package net.Realism.trains;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.Realism.RNetworking;
import net.Realism.network.RouteGuidanceUpdatePacket;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every player's {@link RouteGuidance}. The server map is authoritative and drives the ETCS
 * scan and the steering selector; the client map backs the settings GUI for the local player.
 */
public final class RouteGuidanceManager {

    /** How often (in game ticks) the route is recomputed for the ETCS scan. */
    private static final long REFRESH_TICKS = 40;
    private static final long REQUEST_COOLDOWN_MS = 1000;

    /** Minimum |speed| (blocks/tick) for the speed sign to count as observed travel direction. */
    private static final double DIRECTION_EPSILON = 0.0001;

    private static final Map<UUID, RouteGuidance> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, RouteGuidance> CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PATH_REQUEST = new ConcurrentHashMap<>();

    /**
     * Client-side echo tracking: set when the player sends a request, cleared when the server
     * echo (or a rejection) arrives. Lets the display lamp show a pending state in between.
     */
    private static final Map<UUID, Long> CLIENT_PENDING = new ConcurrentHashMap<>();
    private static final long PENDING_TIMEOUT_MS = 5000;

    private RouteGuidanceManager() {}

    // ------------------------------------------------------------------ storage

    public static RouteGuidance getServer(UUID player) {
        return SERVER.computeIfAbsent(player, k -> new RouteGuidance());
    }

    public static RouteGuidance getClient(UUID player) {
        return CLIENT.computeIfAbsent(player, k -> new RouteGuidance());
    }

    public static void setClient(UUID player, String destinationName) {
        RouteGuidance g = getClient(player);
        g.destinationName = destinationName == null ? "" : destinationName;
        // A server echo confirms the request.
        CLIENT_PENDING.remove(player);
    }

    public static void markClientPending(UUID player) {
        CLIENT_PENDING.put(player, System.currentTimeMillis());
    }

    public static boolean isClientPending(UUID player) {
        Long sent = CLIENT_PENDING.get(player);
        if (sent == null) return false;
        if (System.currentTimeMillis() - sent > PENDING_TIMEOUT_MS) {
            CLIENT_PENDING.remove(player);
            return false;
        }
        return true;
    }

    public static void clearClient(UUID player) {
        RouteGuidance g = CLIENT.get(player);
        if (g != null) g.clear();
        CLIENT_PENDING.remove(player);
    }

    public static void clearServer(UUID player) {
        SERVER.remove(player);
        LAST_PATH_REQUEST.remove(player);
    }

    // ------------------------------------------------------------------ resolution

    private static boolean isControllingPlayer(Train train, UUID player) {
        if (train == null || player == null) return false;
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity == null) continue;
            if (player.equals(entity.getControllingPlayer().orElse(null))) return true;
        }
        return false;
    }

    /** The train the given server player is currently controlling, if any. */
    public static Train controllingTrain(ServerPlayer player) {
        if (!(player.getVehicle() instanceof CarriageContraptionEntity cce)) return null;
        if (!player.getUUID().equals(cce.getControllingPlayer().orElse(null))) return null;
        Carriage carriage = cce.getCarriage();
        return carriage == null ? null : carriage.train;
    }

    /**
     * The guidance a train should act on right now, or null if its owner is not at the controls.
     * Only one route is accepted per train; a newer controller request replaces the previous one.
     */
    public static RouteGuidance getForTrain(Train train) {
        if (train == null) return null;
        for (RouteGuidance g : SERVER.values()) {
            if (!g.hasDestination() || !train.id.equals(g.trainId)) continue;
            if (isControllingPlayer(train, g.ownerId)) return g;
        }
        return null;
    }

    // ------------------------------------------------------------------ path computation

    /**
     * Recomputes the route using Create's own {@code Navigation.findPathTo}. Returns false (and
     * marks the guidance invalid) when the station cannot be found or no drivable path exists.
     */
    public static boolean recomputePath(Train train, RouteGuidance g) {
        if (g == null) return false;
        boolean wasValid = g.valid;
        g.invalidate();
        // Stamp even a failed attempt so an unreachable destination is not searched every tick.
        if (train != null) g.lastPathTick = gameTime(train);
        if (!g.hasDestination()) return false;
        if (train == null || train.graph == null) return failRoute(train, g, wasValid);

        ArrayList<GlobalStation> stations = findStationsByName(train, g.destinationName);
        if (stations.isEmpty()) return failRoute(train, g, wasValid);

        updateDrivingDirection(train, g);

        // Match Create's DestinationInstruction: navigation chooses the best reachable station
        // when several stations share the requested name.
        DiscoveredPath found = train.navigation.findPathTo(stations, Double.MAX_VALUE);
        if (found == null || found.path == null || found.destination == null)
            return failRoute(train, g, wasValid);

        g.trainId = train.id;
        g.resolvedStationId = found.destination.id;
        g.path = new ArrayList<>(found.path);
        g.distanceToDestination = found.distance;
        // Create's search yields a signed distance: negative when the destination lies opposite the
        // train's structural front. Translate that into "behind the driver" using the latched travel
        // direction, so a driver at the rear controls heading forwards sees the same labels as a
        // front driver would.
        g.destinationBehindTrain = (found.distance < 0) == g.drivingTowardFront;
        g.valid = true;
        return true;
    }

    private static boolean failRoute(Train train, RouteGuidance g, boolean notifyOwner) {
        if (!notifyOwner) return false;
        String destinationName = g.destinationName;
        sendToOwner(train, g.ownerId, new RouteGuidanceUpdatePacket(destinationName, false));
        g.clear();
        return false;
    }

    /**
     * Latches the travel direction onto the guidance. The latch exists so the flag can only ever
     * change when the driver visibly picks a new direction: while {@code train.speed} is moving it
     * is authoritative, while the train stands still the last latched value is kept. The very first
     * evaluation (route just selected, train not moving yet) falls back to the controlling seat's
     * facing, once -- mirroring how Create resolves the same ambiguity in {@code CarriageContraptionEntity}.
     */
    private static void updateDrivingDirection(Train train, RouteGuidance g) {
        if (train != null) {
            if (train.speed > DIRECTION_EPSILON) {
                g.drivingTowardFront = true;
                g.drivingDirectionKnown = true;
            } else if (train.speed < -DIRECTION_EPSILON) {
                g.drivingTowardFront = false;
                g.drivingDirectionKnown = true;
            }
        }
        if (!g.drivingDirectionKnown) {
            g.drivingTowardFront = driverSeatFacesFront(train, g.ownerId);
            g.drivingDirectionKnown = true;
        }
    }

    /**
     * Whether the route owner's controls face the train's front. Mirrors Create's own
     * handling of the controls facing in {@code CarriageContraptionEntity.control}.
     */
    private static boolean driverSeatFacesFront(Train train, UUID ownerId) {
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity == null) continue;
            UUID controlling = entity.getControllingPlayer().orElse(null);
            if (controlling == null || !controlling.equals(ownerId)) continue;
            if (!(entity.getContraption() instanceof CarriageContraption contraption)) continue;

            BlockPos seatPos = contraption.getSeatOf(controlling);
            if (seatPos == null) continue;
            StructureBlockInfo info = contraption.getBlocks().get(seatPos);
            // Create stores the controls facing relative to assembly direction; a mismatch means
            // the driver controls the train "backwards" (rear controls of a double-ended train).
            Direction expected = entity.getInitialOrientation().getCounterClockWise();
            boolean inverted = info != null && info.state().hasProperty(ControlsBlock.FACING)
                    && !info.state().getValue(ControlsBlock.FACING).equals(expected);
            return !inverted;
        }
        return true;
    }

    /** Recomputes only when the cached route is missing or older than {@link #REFRESH_TICKS}. */
    public static boolean ensurePath(Train train, RouteGuidance g) {
        if (g == null || !g.hasDestination()) return false;
        long now = gameTime(train);
        long refreshTicks = REFRESH_TICKS + Math.floorMod(g.ownerId == null ? 0 : g.ownerId.hashCode(), REFRESH_TICKS);
        if (g.lastPathTick >= 0 && now - g.lastPathTick < refreshTicks) return g.valid;
        return recomputePath(train, g);
    }

    private static ArrayList<GlobalStation> findStationsByName(Train train, String name) {
        ArrayList<GlobalStation> stations = new ArrayList<>();
        if (train.graph == null || name == null) return stations;
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
            if (station.name != null && station.name.equals(name)) stations.add(station);
        }
        return stations;
    }

    private static long gameTime(Train train) {
        CarriageContraptionEntity entity = train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
        if (entity != null && entity.level() != null) return entity.level().getGameTime();
        return System.currentTimeMillis() / 50L;
    }

    private static void sendToOwner(Train train, UUID ownerId, RouteGuidanceUpdatePacket packet) {
        if (train == null || ownerId == null) return;
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity == null || entity.level() == null || entity.level().getServer() == null) continue;
            ServerPlayer player = entity.level().getServer().getPlayerList().getPlayer(ownerId);
            if (player != null) RNetworking.sendToPlayer(packet, player);
            return;
        }
    }

    // ------------------------------------------------------------------ steering

    /**
     * Selector used by {@code Navigation.control} while route guidance is active. Follows the route
     * edge at junctions and falls back to the player's manual steer otherwise.
     */
    public static TravellingPoint.ITrackSelector createSteeringSelector(Train train, RouteGuidance g,
                                                                       TravellingPoint.ITrackSelector fallback) {
        return (graph, pair) -> {
            List<Map.Entry<TrackNode, TrackEdge>> valid = pair.getSecond();
            if (!g.valid) return fallback.apply(graph, pair);

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
    public static void apply(ServerPlayer player, String destinationName) {
        UUID id = player.getUUID();
        String requestedName = destinationName == null ? "" : destinationName.trim();

        if (requestedName.isEmpty()) {
            getServer(id).clear();
            RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket("", true), player);
            return;
        }

        Train train = controllingTrain(player);
        RouteGuidance g = getServer(id);
        if (train == null) {
            RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket(requestedName, false), player);
            if (g.hasDestination()) {
                RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket(g.destinationName, true), player);
            }
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRequest = LAST_PATH_REQUEST.get(id);
        if (lastRequest != null && now - lastRequest < REQUEST_COOLDOWN_MS) {
            RNetworking.sendToPlayer(RouteGuidanceUpdatePacket.throttled(g.destinationName), player);
            return;
        }
        LAST_PATH_REQUEST.put(id, now);

        RouteGuidance candidate = new RouteGuidance();
        candidate.ownerId = id;
        candidate.trainId = train.id;
        candidate.destinationName = requestedName;

        if (recomputePath(train, candidate)) {
            // A train has one active guidance owner. Publish the new route only after pathfinding
            // succeeds, so a failed request cannot destroy the previously accepted route.
            for (RouteGuidance other : SERVER.values()) {
                if (other == g || other == candidate || !train.id.equals(other.trainId)) continue;
                sendToOwner(train, other.ownerId, new RouteGuidanceUpdatePacket("", true));
                other.clear();
            }
            SERVER.put(id, candidate);
            RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket(candidate.destinationName, true), player);
            return;
        }

        RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket(requestedName, false), player);
        if (g.hasDestination()) {
            RNetworking.sendToPlayer(new RouteGuidanceUpdatePacket(g.destinationName, true), player);
        }
    }
}
