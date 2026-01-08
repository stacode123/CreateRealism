package net.Realism.content.simulator;


import net.Realism.content.graph.BlockEdge;
import net.Realism.content.graph.BlockGraph;
import net.Realism.content.graph.BlockNode;
import net.Realism.content.graph.BlockStation;
import net.Realism.content.simulator.schedule.ScheduleEntry;

import java.util.ArrayList;
import java.util.List;

public class SimulatedTrain {
    public java.util.UUID id = java.util.UUID.randomUUID();
    public BlockGraph graph;
    public BlockEdge edge;
    public Double edgeLocation;
    public SimulatedSchedule runtime;

    public int waitTimer = 0;
    public List<BlockEdge> path = new ArrayList<>();
    public double currentSpeed = 0.0;
    public double maxSpeedFactor = 1.11;
    public double acceleration = 0.1;
    public double targetSpeed = 0.0;

    // For data collection
    public long lastStopTick = 0;
    public long currentTick = 0;
    public List<Long> timeBetweenStops = new ArrayList<>();

    public SimulatedTrain(BlockGraph graph, BlockEdge edge, Double edgeLocation){
        this.graph = graph;
        this.edge = edge;
        this.edgeLocation = edgeLocation;
    }

    public void tick(){
        currentTick++;
        
        // Ensure current edge and its opposite are reserved by us
        if (edge != null) {
            if (edge.reservedBy == null || !edge.reservedBy.equals(id)) {
                edge.reservedBy = id;
            }
            if (edge.opposite != null && (edge.opposite.reservedBy == null || !edge.opposite.reservedBy.equals(id))) {
                edge.opposite.reservedBy = id;
            }
        }
        
        // Note: Chains of edges reserved by look-ahead in StationArrivalEntry 
        // are maintained because nothing else clears them except the train moving past them
        // or the SimulationManager clearing orphaned reservations.

        updateSpeed();
        if (runtime == null) return;

        ScheduleEntry entry = runtime.getCurrentEntry();
        if (entry == null) return;

        if (!entry.isFinished(this)) {
            entry.tick(this);
        } else {
            if (entry.allConditionsMet(this)) {
                runtime.nextEntry();
            } else {
                entry.tickConditions(this);
            }
        }
    }

    public void updateSpeed() {
        if (currentSpeed < targetSpeed) {
            currentSpeed = Math.min(currentSpeed + acceleration, targetSpeed);
        } else if (currentSpeed > targetSpeed) {
            currentSpeed = Math.max(currentSpeed - acceleration, targetSpeed);
        }
    }

    public List<BlockEdge> findPath(BlockEdge start, BlockEdge end) {
        // Simple BFS for pathfinding on edges
        // We assume we are moving from start.start to start.end
        java.util.Queue<List<BlockEdge>> queue = new java.util.LinkedList<>();
        java.util.Set<BlockEdge> visited = new java.util.HashSet<>();
        
        List<BlockEdge> startPath = new ArrayList<>();
        startPath.add(start);
        queue.add(startPath);
        visited.add(start);

        while (!queue.isEmpty()) {
            List<BlockEdge> currentPath = queue.poll();
            BlockEdge lastEdge = currentPath.get(currentPath.size() - 1);

            if (lastEdge == end) {
                return currentPath;
            }

            // In our simplified model, we always exit from 'end' node of the edge
            BlockNode currentNode = lastEdge.end;

            for (BlockEdge nextEdge : currentNode.connections) {
                if (!visited.contains(nextEdge)) {
                    // Ensure we are entering the next edge from one of its nodes
                    if (nextEdge.start == currentNode || nextEdge.end == currentNode) {
                        visited.add(nextEdge);
                        List<BlockEdge> nextPath = new ArrayList<>(currentPath);
                        nextPath.add(nextEdge);
                        queue.add(nextPath);
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    public void moveTrain(BlockStation targetStation) {
        edgeLocation += currentSpeed;

        if (edgeLocation >= edge.length) {
            double overflow = edgeLocation - edge.length;

            if (path.isEmpty()) {
                path = findPath(edge, targetStation.edge);
            }

            int currentIndex = path.indexOf(edge);
            if (currentIndex != -1 && currentIndex < path.size() - 1) {
                BlockEdge nextEdge = path.get(currentIndex + 1);

                // Identify the chain of edges that need to be reserved
                List<BlockEdge> chainToReserve = new ArrayList<>();
                chainToReserve.add(nextEdge);
                if (nextEdge.type == BlockEdge.EdgeType.CHAIN) {
                    for (int i = currentIndex + 2; i < path.size(); i++) {
                        BlockEdge e = path.get(i);
                        chainToReserve.add(e);
                        if (e.type != BlockEdge.EdgeType.CHAIN) break;
                    }
                }

                // Check if the entire chain can be reserved
                boolean canEnter = true;
                for (BlockEdge e : chainToReserve) {
                    if ((e.reservedBy != null && !e.reservedBy.equals(id)) ||
                        (e.opposite != null && e.opposite.reservedBy != null && !e.opposite.reservedBy.equals(id))) {
                        canEnter = false;
                        break;
                    }
                }

                if (canEnter) {
                    // Clear reservation of current edge and its opposite before moving to next
                    if (edge.reservedBy != null && edge.reservedBy.equals(id)) {
                        edge.reservedBy = null;
                        if (edge.opposite != null) {
                            edge.opposite.reservedBy = null;
                        }
                    }

                    // Reserve the entire chain
                    for (BlockEdge e : chainToReserve) {
                        e.reservedBy = id;
                        if (e.opposite != null) {
                            e.opposite.reservedBy = id;
                        }
                    }

                    edge = nextEdge;
                    edgeLocation = overflow;
                } else {
                    // Cannot enter next edge
                    edgeLocation = (double) edge.length;
                    currentSpeed = 0;
                    targetSpeed = 0;
                }
            } else {
                edgeLocation = (double) edge.length;
                if (edge != targetStation.edge) {
                    currentSpeed = 0;
                }
            }
        }
    }

    public double getDistanceToNextSignal() {
        if (path.isEmpty()) return Double.MAX_VALUE;

        int currentIndex = path.indexOf(edge);
        if (currentIndex == -1) return Double.MAX_VALUE;

        double distance = (edge.length - edgeLocation);

        // Look ahead for blocked edges
        for (int i = currentIndex + 1; i < path.size(); i++) {
            BlockEdge e = path.get(i);

            // If we are approaching a chain, we must check if the WHOLE chain (plus exit) is free.
            if (e.type == BlockEdge.EdgeType.CHAIN || (i > currentIndex + 1 && path.get(i-1).type == BlockEdge.EdgeType.CHAIN)) {
                // We need to look ahead from the start of the chain (which is 'e' if i == currentIndex + 1,
                // or already in a chain if i > currentIndex + 1)

                // For simplicity, if ANY edge in the path ahead is reserved by someone else,
                // we treat the START of that reservation as the signal.
                if ((e.reservedBy != null && !e.reservedBy.equals(id)) ||
                    (e.opposite != null && e.opposite.reservedBy != null && !e.opposite.reservedBy.equals(id))) {

                    // If 'e' is a chain edge, or we are in a chain, the "signal" is at the start of the chain.
                    // Let's find where the chain started.
                    double distanceToChainStart = (edge.length - edgeLocation);
                    for (int j = currentIndex + 1; j < i; j++) {
                        if (path.get(j).type == BlockEdge.EdgeType.CHAIN) {
                            // The signal is at the start of the FIRST chain edge in this sequence.
                            return distanceToChainStart;
                        }
                        distanceToChainStart += path.get(j).length;
                    }
                    return distanceToChainStart;
                }
            } else {
                // Normal edge lookahead
                if ((e.reservedBy != null && !e.reservedBy.equals(id)) ||
                    (e.opposite != null && e.opposite.reservedBy != null && !e.opposite.reservedBy.equals(id))) {
                    return distance;
                }
            }

            distance += e.length;
            if (distance > 500) break;
        }

        return Double.MAX_VALUE;
    }
}
