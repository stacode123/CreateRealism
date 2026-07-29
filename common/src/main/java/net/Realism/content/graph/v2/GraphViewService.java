package net.Realism.content.graph.v2;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.core.Direction.AxisDirection;
import net.Realism.RNetworking;
import net.Realism.config.RealismConfig;
import net.Realism.foundation.network.GraphViewPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side handling of "open the rail map here" requests: resolves the
 * clicked block's own track network (crossing lines are separate networks in
 * Create), translates it (cached) and sends it along with nearby networks for
 * context rendering.
 */
public class GraphViewService {

    private static final double MAX_DISTANCE = 64;
    /** Other networks within this range of the click are sent as context. */
    private static final double CONTEXT_DISTANCE = 256;

    public static void request(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        Vec3 clicked = Vec3.atCenterOf(pos);

        TrackGraph primary = graphAt(level, pos);
        if (primary == null) {
            float best = Float.MAX_VALUE;
            for (TrackGraph graph : Create.RAILWAYS.sided(level).trackNetworks.values()) {
                float distanceSqr = graph.distanceToLocationSqr(level, clicked);
                if (distanceSqr < best) {
                    best = distanceSqr;
                    primary = graph;
                }
            }
            if (primary == null || best > MAX_DISTANCE * MAX_DISTANCE) {
                player.displayClientMessage(Component.translatable("realism.graph.no_network"), true);
                return;
            }
        }

        int nodeCap = RealismConfig.COMMON.GraphNodeCap.get();
        RailGraphTranslator.Result result = RailGraphCache.get(primary, nodeCap);
        if (result.graph() == null) {
            player.displayClientMessage(
                    Component.translatable("realism.graph.too_large", result.microNodeCount(), nodeCap), true);
            return;
        }

        List<RailGraph> context = new ArrayList<>();
        for (TrackGraph other : Create.RAILWAYS.sided(level).trackNetworks.values()) {
            if (other == primary)
                continue;
            if (other.distanceToLocationSqr(level, clicked) > CONTEXT_DISTANCE * CONTEXT_DISTANCE)
                continue;
            RailGraphTranslator.Result translated = RailGraphCache.get(other, nodeCap);
            if (translated.graph() != null)
                context.add(translated.graph());
        }
        context.sort(Comparator.comparing(g -> g.trackGraphId));

        RNetworking.sendToPlayer(new GraphViewPacket(result.graph(), pos,
                level.dimension().location().toString(), clickedAxis(level, pos), context), player);
    }

    /**
     * The track network the clicked track block itself belongs to, if any —
     * resolved by walking along the block's own track axes, so a crossing
     * line's nearby nodes can't hijack the result.
     */
    private static TrackGraph graphAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ITrackBlock track))
            return null;
        for (Vec3 axis : track.getTrackAxes(level, pos, state)) {
            for (AxisDirection direction : AxisDirection.values()) {
                TrackGraphLocation location =
                        TrackGraphHelper.getGraphLocationAt(level, pos, direction, axis);
                if (location != null)
                    return location.graph;
            }
        }
        return null;
    }

    /**
     * Horizontal direction of the clicked track block, if unambiguous — lets
     * the map pick the clicked line over one crossing it at the same spot.
     */
    private static Vec3 clickedAxis(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ITrackBlock track))
            return null;
        List<Vec3> axes = track.getTrackAxes(level, pos, state);
        if (axes.size() != 1)
            return null;
        Vec3 axis = axes.get(0);
        double length = Math.sqrt(axis.x * axis.x + axis.z * axis.z);
        if (length < 1e-4)
            return null;
        return new Vec3(axis.x / length, 0, axis.z / length);
    }
}
