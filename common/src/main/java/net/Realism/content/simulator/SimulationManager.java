package net.Realism.content.simulator;

import net.Realism.content.graph.BlockEdge;
import net.Realism.content.graph.BlockGraph;
import net.Realism.content.graph.BlockGraphManager;
import net.Realism.content.graph.BlockStation;
import net.Realism.content.simulator.schedule.StationArrivalEntry;
import net.Realism.content.simulator.schedule.TimedWaitCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SimulationManager {
    private final List<SimulatedTrain> trains = new ArrayList<>();
    private final BlockGraphManager graphManager;
    private boolean autoExport = false;
    private int exportInterval = 20; // Default to 1 second (20 ticks)
    private int tickCounter = 0;

    public SimulationManager(BlockGraphManager graphManager) {
        this.graphManager = graphManager;
    }

    public void addTrain(SimulatedTrain train) {
        trains.add(train);
    }

    public void tick() {
        // Clear all reservations before ticking trains
        // Alternatively, let trains keep their reservations and only clear when they move.
        // But if a train is removed, its reservation should be cleared.
        // For simplicity, let's just clear and let them re-reserve every tick for now,
        // or just let them hold them until they move.
        
        // Actually, if we clear every tick, it might cause race conditions between trains.
        // Let's NOT clear every tick. Instead, let's clear reservations of edges not occupied/reserved by current trains.
        java.util.Set<java.util.UUID> activeTrainIds = new java.util.HashSet<>();
        for (SimulatedTrain train : trains) {
            activeTrainIds.add(train.id);
        }

        for (java.util.UUID graphId : graphManager.getGraphIds()) {
            net.Realism.content.graph.BlockGraph graph = graphManager.getGraph(graphId);
            if (graph != null) {
                for (net.Realism.content.graph.BlockEdge edge : graph.edges) {
                    if (edge.reservedBy != null && !activeTrainIds.contains(edge.reservedBy)) {
                        edge.reservedBy = null;
                    }
                }
            }
        }

        for (SimulatedTrain train : trains) {
            train.tick();
        }

        if (autoExport) {
            tickCounter++;
            if (tickCounter >= exportInterval) {
                exportState("simulation_state.json");
                tickCounter = 0;
            }
        }
    }

    public void setAutoExport(boolean autoExport) {
        this.autoExport = autoExport;
    }

    public boolean isAutoExport() {
        return autoExport;
    }

    public void setExportInterval(int interval) {
        this.exportInterval = interval;
    }

    public List<SimulatedTrain> getTrains() {
        return trains;
    }

    public void exportState(String filename) {
        SimulationStateExporter.export(graphManager, trains, filename);
    }

    public void spawnTrainAtStation(UUID graphId, UUID stationId) {
        BlockGraph graph = graphManager.getGraph(graphId);
        if (graph == null) return;

        for (BlockEdge edge : graph.edges) {
            for (BlockStation station : edge.stations) {
                if (station.id.equals(stationId)) {
                    SimulatedTrain train = new SimulatedTrain(graph, edge, station.location);
                    addTrain(train);
                    return;
                }
            }
        }
    }

    public boolean spawnTrainAtStation(String stationName) {
        for (UUID graphId : graphManager.getGraphIds()) {
            BlockGraph graph = graphManager.getGraph(graphId);
            if (graph == null) continue;

            for (BlockEdge edge : graph.edges) {
                for (BlockStation station : edge.stations) {
                    if (station.station != null && station.station.name.equals(stationName)) {
                        SimulatedTrain train = new SimulatedTrain(graph, edge, station.location);
                        
                        SimulatedSchedule schedule = new SimulatedSchedule();
                        schedule.train = train;
                        StationArrivalEntry entry = new StationArrivalEntry(stationName);
                        entry.addCondition(new TimedWaitCondition(20));
                        StationArrivalEntry nextEntry = new StationArrivalEntry("Zakopane 1");
                        nextEntry.addCondition(new TimedWaitCondition(20));
                        schedule.addEntry(entry);
                        schedule.addEntry(nextEntry);
                        train.runtime = schedule;
                        
                        addTrain(train);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void spawnTrain(UUID graphId, UUID edgeStartNodeId, UUID edgeEndNodeId, Double location) {
        BlockGraph graph = graphManager.getGraph(graphId);
        if (graph == null) return;

        for (BlockEdge edge : graph.edges) {
            if (edge.start.id.equals(edgeStartNodeId) && edge.end.id.equals(edgeEndNodeId)) {
                SimulatedTrain train = new SimulatedTrain(graph, edge, location);
                addTrain(train);
                return;
            }
        }
    }
}
