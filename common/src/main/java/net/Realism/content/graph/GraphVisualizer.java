package net.Realism.content.graph;

import java.io.FileWriter;
import java.io.IOException;

public class GraphVisualizer {

    public static void visualize(BlockGraph graph, String filename) {
        if (graph.nodes.isEmpty()) return;

        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxZ = Double.MIN_VALUE;

        for (BlockNode node : graph.nodes) {
            if (node.location == null) continue;
            minX = Math.min(minX, node.location.x);
            minZ = Math.min(minZ, node.location.z);
            maxX = Math.max(maxX, node.location.x);
            maxZ = Math.max(maxZ, node.location.z);
        }

        // Add some margin
        minX -= 10;
        minZ -= 10;
        maxX += 10;
        maxZ += 10;

        double width = maxX - minX;
        double height = maxZ - minZ;

        StringBuilder svg = new StringBuilder();
        svg.append(String.format("<svg viewBox=\"%f %f %f %f\" xmlns=\"http://www.w3.org/2000/svg\">\n", minX, minZ, width, height));
        svg.append("  <rect x=\"").append(minX).append("\" y=\"").append(minZ)
           .append("\" width=\"").append(width).append("\" height=\"").append(height)
           .append("\" fill=\"white\" />\n");

        // Draw grid
        double gridSpacing = 10.0;
        svg.append("  <g id=\"grid\" stroke=\"#e0e0e0\" stroke-width=\"0.1\">\n");
        for (double x = Math.floor(minX / gridSpacing) * gridSpacing; x <= maxX; x += gridSpacing) {
            svg.append(String.format("    <line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" />\n", x, minZ, x, maxZ));
        }
        for (double z = Math.floor(minZ / gridSpacing) * gridSpacing; z <= maxZ; z += gridSpacing) {
            svg.append(String.format("    <line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" />\n", minX, z, maxX, z));
        }
        svg.append("  </g>\n");

        // Draw edges
        svg.append("  <defs>\n");
        svg.append("    <marker id=\"arrowhead_black\" markerWidth=\"5\" markerHeight=\"3.5\" refX=\"5\" refY=\"1.75\" orient=\"auto\">\n");
        svg.append("      <polygon points=\"0 0, 5 1.75, 0 3.5\" fill=\"black\" />\n");
        svg.append("    </marker>\n");
        svg.append("    <marker id=\"arrowhead_orange\" markerWidth=\"5\" markerHeight=\"3.5\" refX=\"5\" refY=\"1.75\" orient=\"auto\">\n");
        svg.append("      <polygon points=\"0 0, 5 1.75, 0 3.5\" fill=\"orange\" />\n");
        svg.append("    </marker>\n");
        svg.append("  </defs>\n");

        for (BlockEdge edge : graph.edges) {
            if (edge.start.location == null || edge.end.location == null) continue;
            
            double x1 = edge.start.location.x;
            double z1 = edge.start.location.z;
            double x2 = edge.end.location.x;
            double z2 = edge.end.location.z;

            // Calculate offset for parallel lines
            double dx = x2 - x1;
            double dz = z2 - z1;
            double len = Math.sqrt(dx * dx + dz * dz);
            double offsetX = 0;
            double offsetZ = 0;

            if (len > 0) {
                double offsetAmount = 1.0;
                offsetX = -dz / len * offsetAmount;
                offsetZ = dx / len * offsetAmount;
            }

            String color = edge.type == BlockEdge.EdgeType.CHAIN ? "orange" : "black";
            String markerId = edge.type == BlockEdge.EdgeType.CHAIN ? "arrowhead_orange" : "arrowhead_black";

            svg.append(String.format("  <line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"%s\" stroke-width=\"0.3\" marker-end=\"url(#%s)\" />\n",
                    x1 + offsetX, z1 + offsetZ, x2 + offsetX, z2 + offsetZ, color, markerId));

            // Draw station markers if present
            if (edge.stations != null && !edge.stations.isEmpty()) {
                for (BlockStation station : edge.stations) {
                    double ratio = station.location / (double) edge.length;
                    double sx = x1 + (x2 - x1) * ratio + offsetX;
                    double sz = z1 + (z2 - z1) * ratio + offsetZ;
                    svg.append(String.format("  <rect x=\"%f\" y=\"%f\" width=\"1.5\" height=\"1.5\" fill=\"green\" stroke=\"black\" stroke-width=\"0.1\" transform=\"translate(-0.75, -0.75)\" />\n",
                            sx, sz));
                }
            }
        }

        // Draw nodes
        for (BlockNode node : graph.nodes) {
            if (node.location == null) continue;
            String color = (node.type == BlockNode.NodeType.JUNCTION) ? "blue" : "red";
            svg.append(String.format("  <circle cx=\"%f\" cy=\"%f\" r=\"0.8\" fill=\"%s\" stroke=\"black\" stroke-width=\"0.15\" />\n",
                    node.location.x, node.location.z, color));
            
            // Draw node identifier (first 5 chars of UUID)
            String label = node.id.toString().substring(0, 5);
            svg.append(String.format("  <text x=\"%f\" y=\"%f\" font-family=\"Arial\" font-size=\"1.2\" fill=\"black\" text-anchor=\"middle\">%s</text>\n",
                    node.location.x, node.location.z + 2.0, label));
        }

        svg.append("</svg>");

        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(svg.toString());
            System.out.println("Graph visualization saved to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
