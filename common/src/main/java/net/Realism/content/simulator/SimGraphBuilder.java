package net.Realism.content.simulator;

import net.Realism.content.graph.v2.RailCrossing;
import net.Realism.content.graph.v2.RailEdge;
import net.Realism.content.graph.v2.RailGraph;
import net.Realism.content.graph.v2.RailNode;
import net.Realism.content.graph.v2.RailStation;
import net.Realism.content.simulator.core.SimEdge;
import net.Realism.content.simulator.core.SimGraph;
import net.Realism.content.simulator.core.SimNode;
import net.Realism.content.simulator.core.SimVec;
import net.minecraft.world.phys.Vec3;

/** Adapts a translated graph + its server-only topology into the MC-free sim model. */
public class SimGraphBuilder {

    /** km/h → blocks/tick. */
    public static double kmhToBlocksPerTick(double kmh) {
        return kmh / 3.6 / 20;
    }

    public static SimGraph build(RailGraph rail, SimTopology topology) {
        SimGraph graph = new SimGraph();
        for (RailNode node : rail.nodes)
            graph.nodes.add(new SimNode(node.id, node.type == RailNode.NodeType.SIGNAL,
                    node.dimension, toSimVec(node.position)));

        for (RailEdge edge : rail.edges) {
            double[][] turnRanges = new double[edge.capProfile.size()][];
            for (int i = 0; i < edge.capProfile.size(); i++) {
                double[] range = edge.capProfile.get(i);
                turnRanges[i] = new double[] { range[0], range[1] };
            }
            SimEdge simEdge = new SimEdge(edge.id, edge.from, edge.to, edge.oppositeId, edge.length,
                    SimEdge.Signal.values()[edge.entrySignal.toByte()],
                    kmhToBlocksPerTick(topology.signCapKmh(edge.id)), turnRanges,
                    toSimVec(topology.entryTangent(edge.id).normalize()),
                    toSimVec(topology.exitTangent(edge.id).normalize()),
                    edge.interDimensional);
            for (RailStation station : edge.stations)
                simEdge.stations.add(new SimEdge.Station(station.stationId(), station.name(),
                        station.offset(), station.approachable()));
            for (RailCrossing crossing : edge.crossings)
                simEdge.crossings.add(new SimEdge.Crossing(crossing.crossingId(), crossing.offset()));
            graph.edges.add(simEdge);
        }
        graph.computeDerived();
        return graph;
    }

    private static SimVec toSimVec(Vec3 vec) {
        return new SimVec(vec.x, vec.y, vec.z);
    }
}
