package net.Realism.graph;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;

import java.lang.reflect.Field;
import java.util.*;


public class GraphTranslator {
    private Collection<SignalBoundary> allSignals;
    private Collection<GlobalStation> allStations;

    public BlockGraph translate(TrackGraph graph) {
        BlockGraph blockGraph = new BlockGraph(graph);
        Map<Object, BlockNode> nodeMap = new HashMap<>();
        Map<TrackNode, BlockNode> trackNodeMap = new HashMap<>();

        Map<TrackNodeLocation, TrackNode> graphNodes = getGraphNodes(graph);
        if (graphNodes == null) return blockGraph;

        allSignals = graph.getPoints(EdgePointType.SIGNAL);
        allStations = graph.getPoints(EdgePointType.STATION);

        // 1. Identify boundary points
        Set<TrackNode> junctions = new HashSet<>();
        Set<TrackNode> nonJunctions = new HashSet<>();
        for (TrackNode node : graphNodes.values()) {
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
            if (connections.size() != 2) {
                junctions.add(node);
            }
            else {
                nonJunctions.add(node);
            }
        }

        Collection<SignalBoundary> signals = graph.getPoints(EdgePointType.SIGNAL);

        // 2. Create BlockNodes for junctions and signals
        for (TrackNode junction : junctions) {
            BlockNode node = new BlockNode(UUID.randomUUID(), junction.getLocation().getLocation(), BlockNode.NodeType.JUNCTION);
            blockGraph.nodes.add(node);
            nodeMap.put(junction, node);
            trackNodeMap.put(junction, node);
        }

        for (SignalBoundary signal : signals) {
            BlockNode node = new BlockNode(signal.id, getSignalLocation(graph, signal), BlockNode.NodeType.SIGNAL);
            blockGraph.nodes.add(node);
            nodeMap.put(signal, node);
        }

        // 3. Traverse to find edges (blocks)
        Set<Pair<TrackNode, TrackNode>> visitedDirectedEdges = new HashSet<>();
        List<Pair<List<TrackEdge>,Pair<BlockNode,BlockNode>>> EdgesWithSignals = new ArrayList<>();

        // Start from each Node
        List<Map.Entry<Object, BlockNode>> Nodes = new ArrayList<>(nodeMap.entrySet());
        List<SignalBoundary> signalList = new ArrayList<>();
        for (Map.Entry<Object, BlockNode> entry : Nodes) {
            Object boundary = entry.getKey();
            BlockNode startNode = entry.getValue();

            if (boundary instanceof TrackNode node) {
                for (Map.Entry<TrackNode, TrackEdge> entry2 : graph.getConnectionsFrom(node).entrySet()) {
                    TrackNode nextNode = entry2.getKey();
                    if (visitedDirectedEdges.contains(Pair.of(node, nextNode))) continue;
                    Triple<TrackNode, TrackNode, Pair<List<SignalBoundary>, List<TrackEdge>>> signalResult = traverseToNextBoundary(graph, node, nextNode, junctions, signals);
                    if (!signalResult.getRight().getFirst().isEmpty()) {
                        EdgesWithSignals.add(Pair.of(signalResult.getRight().getSecond(),Pair.of(nodeMap.get(signalResult.getLeft()),nodeMap.get(signalResult.getMiddle()))));
                    } else {
                        createJunctionToJunctionEdge(graph, startNode, nodeMap.get(signalResult.getMiddle()), signalResult.getRight().getSecond(), blockGraph);
                    }
                    visitedDirectedEdges.add(Pair.of(node, nextNode));

                }
            }
        }
        for (Pair<List<TrackEdge>, Pair<BlockNode,BlockNode>> ExtendedtrackEdge : EdgesWithSignals) {
            List<TrackEdge> path = ExtendedtrackEdge.getFirst();
            List<SignalBoundary> signalsOnPath = new ArrayList<>();
            TrackNode currentSource = null;
            // We need to re-traverse or find which nodes are node1 and node2 for each edge in the path
            // because signalsOnPath depends on the direction.
            // But wait, the path is already in order from node to middleNode.
            // We just need to ensure getSignalsFromEdge is called with correct node order.
            
            // Re-identify the nodes in the path for signal collection
            TrackNode tempNode = null;
            // Find start node
            for (Map.Entry<TrackNode, BlockNode> entry : trackNodeMap.entrySet()) {
                if (entry.getValue().equals(ExtendedtrackEdge.getSecond().getFirst())) {
                    tempNode = entry.getKey();
                    break;
                }
            }
            Map<SignalBoundary,Boolean> trackSignalMap = new HashMap<>();
            for (TrackEdge trackEdge : path) {
                TrackNode nextTempNode = trackEdge.node1.equals(tempNode) ? trackEdge.node2 : trackEdge.node1;
                TrackNode finalTempNode = tempNode;
                trackSignalMap.putAll(getSignalsFromEdge(graph, tempNode, nextTempNode).stream()
                        .collect(HashMap::new,
                                (m, v) -> {
                                    boolean hasBlockEntities = !(v.blockEntities.get(!v.isPrimary(finalTempNode)).isEmpty());
                                    m.put(v, hasBlockEntities);
                                    if (hasBlockEntities) {
                                signalsOnPath.add(v);
                            }
                        }, 
                        HashMap::putAll));
                tempNode = nextTempNode;
            }
            Pair<BlockNode,BlockNode> nodeBlocks  = ExtendedtrackEdge.getSecond();
            List<BlockNode> signalBlocks = new ArrayList<>();
            for (SignalBoundary signal : signalsOnPath) {
                blockGraph.nodes.forEach(node -> {if(node.id.equals(signal.id)) signalBlocks.add(node);});
            }
            if (signalsOnPath.size() != signalBlocks.size()) {
                throw new IllegalStateException("Mismatch between signals on path and signal blocks.");
            }

            if (signalBlocks.size() == 1) {
                    createBlockEdge(graph, blockGraph, nodeBlocks.getFirst(), signalBlocks.get(0), ExtendedtrackEdge.getFirst(), signalsOnPath.get(0), true);
                    createBlockEdge(graph, blockGraph, signalBlocks.get(0), nodeBlocks.getSecond(), ExtendedtrackEdge.getFirst(), signalsOnPath.get(0), false);
            }
            else {
                for (int i = 0; i < signalBlocks.size(); i++) {
                    if (i == 0) {
                        createBlockEdge(graph,blockGraph,nodeBlocks.getFirst(),signalBlocks.get(i),ExtendedtrackEdge.getFirst(),signalsOnPath.get(i),true);
                    }
                    else {
                        createBlockEdge(graph,blockGraph,signalBlocks.get(i-1),signalBlocks.get(i),ExtendedtrackEdge.getFirst(),signalsOnPath.get(i-1),signalsOnPath.get(i));
                    }

                    if (i == signalBlocks.size() - 1) {
                        createBlockEdge(graph,blockGraph,signalBlocks.get(i),nodeBlocks.getSecond(),ExtendedtrackEdge.getFirst(),signalsOnPath.get(i),false);
                    }
                }
            }
        }

        GraphVisualizer.visualize(blockGraph, "debug_graph_" + blockGraph.id.toString().substring(0,5) + ".svg");

        return blockGraph;
    }



    private Vec3 getSignalLocation(TrackGraph graph, SignalBoundary signal) {
        Map<TrackNodeLocation, TrackNode> nodes = getGraphNodes(graph);
        if (nodes == null) return null;
        TrackNode n1 = nodes.get(signal.edgeLocation.getFirst());
        TrackNode n2 = nodes.get(signal.edgeLocation.getSecond());
        if (n1 == null || n2 == null) {
            for (TrackNode n : nodes.values()) {
                if (n.getLocation().equals(signal.edgeLocation.getFirst())) n1 = n;
                if (n.getLocation().equals(signal.edgeLocation.getSecond())) n2 = n;
            }
        }
        if (n1 == null || n2 == null) return null;
        TrackEdge edge = graph.getConnectionsFrom(n1).get(n2);
        if (edge == null) return null;

        double pos = signal.getLocationOn(edge);
        if (edge.isTurn()) {
            return edge.getTurn().getPosition(pos / edge.getLength());
        } else {
            return VecHelper.lerp((float)(pos / edge.getLength()), n1.getLocation().getLocation(), n2.getLocation().getLocation());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<TrackNodeLocation, TrackNode> getGraphNodes(TrackGraph graph) {
        try {
            Field nodesField = TrackGraph.class.getDeclaredField("nodes");
            nodesField.setAccessible(true);
            return (Map<TrackNodeLocation, TrackNode>) nodesField.get(graph);
        } catch (Exception e) {
            return null;
        }
    }






    private List<SignalBoundary> getSignalsFromEdge(TrackGraph graph, TrackNode node1, TrackNode node2) {
        List<SignalBoundary> signals = new ArrayList<>();
        TrackEdge edge = graph.getConnectionsFrom(node1).get(node2);
        if (edge == null) return signals;
        for (SignalBoundary signal : allSignals) {
            if (isPointOnEdge(signal, edge)) {
                signals.add(signal);
            }
        }
        List<SignalBoundary> sortedSignals = new ArrayList<>(signals);
        sortedSignals.sort(Comparator.comparingDouble(s -> s.getLocationOn(edge)));
        return sortedSignals;
    }

    private boolean isPointOnEdge(TrackEdgePoint point, TrackEdge edge){
        return (edge.getEdgeData().getPoints().contains(point));
    }

    private void createJunctionToJunctionEdge(TrackGraph graph, BlockNode start, BlockNode end, List<TrackEdge> path, BlockGraph blockGraph) {
        createBlockEdge(graph, start, end, path, blockGraph, false);
    }

    private void createBlockEdge(TrackGraph graph, BlockNode start, BlockNode end, List<TrackEdge> path, BlockGraph blockGraph, boolean reverse) {
        double length = 0;
        List<BlockStation> stations = new ArrayList<>();

        if (!reverse) {
            for (int i = 0; i < path.size(); i++) {
                TrackEdge trackEdge = path.get(i);
                length += trackEdge.getLength();
                trackEdge.getEdgeData().getPoints().forEach(point -> {
                    if (point instanceof GlobalStation station && !point.isPrimary(trackEdge.node1)) {
                        stations.add(new BlockStation(null, station, station.id, blockGraph));
                    }
                });
            }
        } else {
            for (int i = path.size() - 1; i >= 0; i--) {
                TrackEdge trackEdge = path.get(i);
                length += trackEdge.getLength();
                // We need to reverse the order of stations on the same edge if we are strictly about order
                // but BlockEdge.stations is just a list.
                List<BlockStation> edgeStations = new ArrayList<>();
                trackEdge.getEdgeData().getPoints().forEach(point -> {
                    if (point instanceof GlobalStation station && !point.isPrimary(trackEdge.node1)) {
                        edgeStations.add(new BlockStation(null, station, station.id, blockGraph));
                    }
                });
                // If multiple stations on same edge, they should be in reverse order for reverse traversal
                Collections.reverse(edgeStations);
                stations.addAll(edgeStations);
            }
        }
        BlockEdge Bedge = new BlockEdge(start, end, stations, (int) length);
        blockGraph.edges.add(Bedge);
        start.connections.add(Bedge);
        end.connections.add(Bedge);
        for (BlockStation station : Bedge.stations) {
            station.setEdge(Bedge);
        }

    }

    private void createBlockEdge(TrackGraph graph,BlockGraph blockGraph,BlockNode start, BlockNode end,List<TrackEdge> edges,SignalBoundary p1,SignalBoundary p2) {
        int p1loc = java.util.stream.IntStream.range(0, edges.size()).filter(i -> edges.get(i).getEdgeData().getPoints().contains(p1)).findFirst().orElseThrow();
        int p2loc = java.util.stream.IntStream.range(0, edges.size()).filter(i -> edges.get(i).getEdgeData().getPoints().contains(p2)).findFirst().orElseThrow();
        List<BlockStation> blockStations = new ArrayList<>();
        if (p1loc <= p2loc) {
            for (int i = p1loc; i <= p2loc; i++) {
                TrackEdge edge = edges.get(i);
                final int currentIndex = i;
                List<BlockStation> edgeStations = new ArrayList<>();
                edge.getEdgeData().getPoints().forEach(point -> {
                    if (point instanceof GlobalStation station && !point.isPrimary(edge.node1)) {
                        double loc = station.getLocationOn(edge);
                        if (currentIndex == p1loc && loc <= p1.getLocationOn(edge)) return;
                        if (currentIndex == p2loc && loc >= p2.getLocationOn(edge)) return;
                        edgeStations.add(new BlockStation(null, station, station.id, blockGraph));
                    }
                });
                // Sort stations by location on edge (assuming p1loc to p2loc is forward)
                edgeStations.sort(Comparator.comparingDouble(s -> s.station.getLocationOn(edge)));
                blockStations.addAll(edgeStations);
            }
        } else {
            // This case might happen if edges list is not in the direction of traversal between p1 and p2
            // But based on traverseToNextBoundary, edges should be in order.
            // If p1loc > p2loc, it's a reverse traversal.
            for (int i = p1loc; i >= p2loc; i--) {
                TrackEdge edge = edges.get(i);
                final int currentIndex = i;
                List<BlockStation> edgeStations = new ArrayList<>();
                edge.getEdgeData().getPoints().forEach(point -> {
                    if (point instanceof GlobalStation station && !point.isPrimary(edge.node1)) {
                        double loc = station.getLocationOn(edge);
                        if (currentIndex == p1loc && loc >= p1.getLocationOn(edge)) return;
                        if (currentIndex == p2loc && loc <= p2.getLocationOn(edge)) return;
                        edgeStations.add(new BlockStation(null, station, station.id, blockGraph));
                    }
                });
                // Sort stations by location on edge (reversed)
                edgeStations.sort(Comparator.comparingDouble((BlockStation s) -> s.station.getLocationOn(edge)).reversed());
                blockStations.addAll(edgeStations);
            }
        }
        BlockEdge Bedge = new BlockEdge(start, end, blockStations, (int) distance(p1,p2,edges));
        blockGraph.edges.add(Bedge);
        start.connections.add(Bedge);
        end.connections.add(Bedge);
        for (BlockStation station : Bedge.stations) {
            station.setEdge(Bedge);
        }
    }
    private void createBlockEdge(TrackGraph graph,BlockGraph blockGraph,BlockNode start, BlockNode end,List<TrackEdge> edges,SignalBoundary p,boolean first) {
        int ploc = java.util.stream.IntStream.range(0, edges.size()).filter(i -> edges.get(i).getEdgeData().getPoints().contains(p)).findFirst().orElseThrow();
        List<BlockStation> blockStations = new ArrayList<>();
        double distance = 0;
        if (first) {
            for (int i = 0; i <= ploc; i++) {
                TrackEdge edge = edges.get(i);
                final int currentIndex = i;
                if (currentIndex < ploc) {
                    distance += edge.getLength();
                }
                List<BlockStation> edgeStations = new ArrayList<>();
                edge.getEdgeData().getPoints().forEach(point -> {
                    if (point instanceof GlobalStation station && !point.isPrimary(edge.node1)) {
                        double loc = station.getLocationOn(edge);
                        if (currentIndex == ploc && loc >= p.getLocationOn(edge)) return;
                        edgeStations.add(new BlockStation(null, station, station.id, blockGraph));
                    }
                });
                edgeStations.sort(Comparator.comparingDouble(s -> s.station.getLocationOn(edge)));
                blockStations.addAll(edgeStations);
            }
            distance += p.getLocationOn(edges.get(ploc));
            BlockEdge Bedge = new BlockEdge(start, end, blockStations, (int) distance);
            blockGraph.edges.add(Bedge);
            start.connections.add(Bedge);
            end.connections.add(Bedge);
            for (BlockStation station : Bedge.stations) {
                station.setEdge(Bedge);
            }
        }
        else {
            for (int i = edges.size()-1; i >= ploc; i--) {
                TrackEdge edge = edges.get(i);
                final int currentIndex = i;
                if (currentIndex > ploc) {
                    distance += edge.getLength();
                }
                List<BlockStation> edgeStations = new ArrayList<>();
                edge.getEdgeData().getPoints().forEach(point -> {
                    if (point instanceof GlobalStation station && !point.isPrimary(edge.node1)) {
                        double loc = station.getLocationOn(edge);
                        if (currentIndex == ploc && loc <= p.getLocationOn(edge)) return;
                        edgeStations.add(new BlockStation(null, station, station.id, blockGraph));
                    }
                });
                edgeStations.sort(Comparator.comparingDouble((BlockStation s) -> s.station.getLocationOn(edge)).reversed());
                blockStations.addAll(edgeStations);
            }
            distance += (edges.get(ploc).getLength() - p.getLocationOn(edges.get(ploc)));
            BlockEdge Bedge = new BlockEdge(start, end, blockStations, (int) distance);
            blockGraph.edges.add(Bedge);
            start.connections.add(Bedge);
            end.connections.add(Bedge);
            for (BlockStation station : Bedge.stations) {
                station.setEdge(Bedge);
            }
        }
    }



    private double distance(SignalBoundary p1, SignalBoundary p2, List<TrackEdge> edge) {
        List<Double> lengths = new ArrayList<>();
        int p1loc = java.util.stream.IntStream.range(0, edge.size()).filter(i -> edge.get(i).getEdgeData().getPoints().contains(p1)).findFirst().orElseThrow();
        int p2loc = java.util.stream.IntStream.range(0, edge.size()).filter(i -> edge.get(i).getEdgeData().getPoints().contains(p2)).findFirst().orElseThrow();

        for (TrackEdge trackEdge : edge) {
            if (lengths.isEmpty()) lengths.add(trackEdge.getLength());
            else
                lengths.add(trackEdge.getLength()+lengths.get(lengths.size()-1));
        }
        return lengths.get(p2loc) + p2.getLocationOn(edge.get(p2loc)) -lengths.get(p1loc) + p1.getLocationOn(edge.get(p1loc));
    }

    private Triple<TrackNode, TrackNode, Pair<List<SignalBoundary>, List<TrackEdge>>> traverseToNextBoundary(TrackGraph graph, TrackNode node, TrackNode nextNode, Set<TrackNode> junctions, Collection<SignalBoundary> signals) {
        List<SignalBoundary> collectedSignals = new ArrayList<>();
        List<TrackEdge> path = new ArrayList<>();
        TrackNode ogNode = node;
        while(true) {
            path.add(graph.getConnectionsFrom(node).get(nextNode));
            collectedSignals.addAll(getSignalsFromEdge(graph, node, nextNode));
            
            if (junctions.contains(nextNode)) {
                break;
            }
            
            TrackNode oldNode = node;
            node = nextNode;
            nextNode = graph.getConnectionsFrom(node).keySet().stream().filter(n -> !n.equals(oldNode)).findFirst().orElse(null);
            if (nextNode == null) break; // Should not happen in a valid graph unless it's a dead end (which should be a junction)
        }
        return Triple.of(ogNode, nextNode, Pair.of(collectedSignals, path));
    }



}

