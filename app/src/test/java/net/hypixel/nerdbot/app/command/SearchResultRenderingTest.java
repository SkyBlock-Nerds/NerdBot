package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering the {@code /gen} search reply: empty results append nothing, the header reports the full
 * total, and only the capped number of result lines render.
 */
class SearchResultRenderingTest {

    @Test
    void ignoresEmptyResults() {
        StringBuilder message = new StringBuilder("existing");

        GeneratorCommands.appendSearchResults(message, "Header", List.of());

        assertEquals("existing", message.toString());
    }

    @Test
    void appendsHeaderWithTotalAndEachResult() {
        StringBuilder message = new StringBuilder();

        GeneratorCommands.appendSearchResults(message, "Top", List.of("a", "b"));

        assertEquals("Top (2 total):\n - `a`\n - `b`\n", message.toString());
    }

    @Test
    void capsRenderedLinesButReportsFullTotal() {
        List<String> results = IntStream.range(0, 15).mapToObj(i -> "item" + i).toList();
        StringBuilder message = new StringBuilder();

        GeneratorCommands.appendSearchResults(message, "Results", results);

        String output = message.toString();
        assertTrue(output.startsWith("Results (15 total):\n"), "header should report the full total");
        assertEquals(10, output.lines().filter(line -> line.startsWith(" - ")).count(), "only 10 results should render");
    }
}
