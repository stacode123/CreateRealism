package net.Realism.content.simulator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.Realism.content.graph.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class SimulationStateExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void export(BlockGraphManager graphManager, List<SimulatedTrain> trains, String filename) {
        JsonObject root = new JsonObject();
        
        // Export Graphs
        JsonArray graphsArray = new JsonArray();
        for (UUID graphId : graphManager.getGraphIds()) {
            BlockGraph graph = graphManager.getGraph(graphId);
            if (graph == null) continue;
            
            JsonObject graphJson = new JsonObject();
            graphJson.addProperty("id", graph.id.toString());
            
            // Nodes
            JsonArray nodesArray = new JsonArray();
            for (BlockNode node : graph.nodes) {
                JsonObject nodeJson = new JsonObject();
                nodeJson.addProperty("id", node.id.toString());
                nodeJson.addProperty("type", node.type.name());
                if (node.location != null) {
                    nodeJson.addProperty("x", node.location.x);
                    nodeJson.addProperty("y", node.location.y);
                    nodeJson.addProperty("z", node.location.z);
                }
                nodesArray.add(nodeJson);
            }
            graphJson.add("nodes", nodesArray);
            
            // Edges
            JsonArray edgesArray = new JsonArray();
            for (BlockEdge edge : graph.edges) {
                JsonObject edgeJson = new JsonObject();
                edgeJson.addProperty("start", edge.start.id.toString());
                edgeJson.addProperty("end", edge.end.id.toString());
                edgeJson.addProperty("length", edge.length);
                edgeJson.addProperty("type", edge.type.name());
                if (edge.reservedBy != null) {
                    edgeJson.addProperty("reservedBy", edge.reservedBy.toString());
                }
                
                JsonArray stationsArray = new JsonArray();
                for (BlockStation station : edge.stations) {
                    JsonObject stationJson = new JsonObject();
                    stationJson.addProperty("id", station.id.toString());
                    stationJson.addProperty("name", station.station != null ? station.station.name : "Unknown");
                    stationJson.addProperty("location", station.location);
                    stationsArray.add(stationJson);
                }
                edgeJson.add("stations", stationsArray);
                edgesArray.add(edgeJson);
            }
            graphJson.add("edges", edgesArray);
            
            graphsArray.add(graphJson);
        }
        root.add("graphs", graphsArray);
        
        // Export Trains
        JsonArray trainsArray = new JsonArray();
        for (SimulatedTrain train : trains) {
            JsonObject trainJson = new JsonObject();
            trainJson.addProperty("id", train.id.toString());
            trainJson.addProperty("graphId", train.graph.id.toString());
            trainJson.addProperty("edgeStart", train.edge.start.id.toString());
            trainJson.addProperty("edgeEnd", train.edge.end.id.toString());
            trainJson.addProperty("location", train.edgeLocation);
            trainJson.addProperty("speed", train.currentSpeed);
            trainJson.addProperty("targetSpeed", train.targetSpeed);
            
            JsonArray resultsArray = new JsonArray();
            for (Long time : train.timeBetweenStops) {
                resultsArray.add(time);
            }
            trainJson.add("timeBetweenStops", resultsArray);
            
            trainsArray.add(trainJson);
        }
        root.add("trains", trainsArray);

        try (FileWriter writer = new FileWriter(filename)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
