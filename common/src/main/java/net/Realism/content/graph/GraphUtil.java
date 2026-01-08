package net.Realism.content.graph;

import com.simibubi.create.content.trains.signal.SignalBlock;

import static com.simibubi.create.content.trains.signal.SignalBlock.SignalType.ENTRY_SIGNAL;

public class GraphUtil {
    public static BlockEdge.EdgeType SignaltoEdgeType(SignalBlock.SignalType type) {
        if (type == ENTRY_SIGNAL){
            return BlockEdge.EdgeType.NORMAL;
        }
        else {
            return BlockEdge.EdgeType.CHAIN;
        }
    }
}
