package net.Realism.content.simulator.core;

import net.Realism.content.simulator.SimDebugExporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The playback debug export: a staged two-train scenario (follower blocked
 * at a signal behind a parked leader → wait windows + a SECTION conflict)
 * rendered to HTML. Also writes {@code build/sim-debug-preview.html} so the
 * viewer can be opened in a browser during development.
 */
class SimDebugExportPreviewTest {

    @Test
    void exportsSelfContainedViewer() throws Exception {
        LineFixture line = new LineFixture().nodes(0, 500, 1000);
        line.station("Warszawa", 100);
        line.station("Kraków", 900);
        line.forwardSignal(1, SimEdge.Signal.ENTRY);
        SimGraph graph = line.build();

        SimTrainSpec leader = LineFixture.train("Leader", line, 50, LineFixture.program(false,
                LineFixture.destination("Kraków", new SimCondition.Delay(100))));
        SimTrainSpec follower = LineFixture.train("Follower", line, 20, LineFixture.program(false,
                LineFixture.destination("Kraków", new SimCondition.Delay(100))));
        SimResult result = LineFixture.engine(graph, List.of(leader, follower), 4000).run();

        String html = SimDebugExporter.buildHtml(graph, result, List.of(leader, follower),
                List.of("minecraft:overworld"), java.util.Map.of(), 6000, 1.0, 20);

        assertTrue(html.contains("\"trains\":["), "train data embedded");
        assertTrue(html.contains("\"path\":["), "traversed edge paths embedded");
        assertTrue(html.contains("\"Leader\""), "train names embedded");
        assertTrue(html.contains("\"w\":[["), "signal-wait windows embedded");
        assertTrue(html.contains("\"conflicts\":[["), "conflicts embedded");
        assertTrue(html.contains("const DATA = {"), "data spliced into the template");

        Path preview = Path.of("build", "sim-debug-preview.html");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, html);
    }
}
