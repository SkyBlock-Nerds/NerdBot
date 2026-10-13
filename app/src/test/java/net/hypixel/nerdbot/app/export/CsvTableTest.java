package net.hypixel.nerdbot.app.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvTableTest {

    @Test
    void plainValuesAreWrittenAsTheyAre() {
        CsvTable table = new CsvTable(List.of("A", "B"), ",").addRow(List.of("one", "two"));

        assertEquals("A,B\none,two\n", table.toCsv());
    }

    @Test
    void aValueHoldingTheDelimiterIsQuoted() {
        assertEquals("\"a,b\"", new CsvTable(List.of("A"), ",").field("a,b"));
        assertEquals("a,b", new CsvTable(List.of("A"), ";").field("a,b"));
        assertEquals("\"a;b\"", new CsvTable(List.of("A"), ";").field("a;b"));
    }

    @Test
    void quotesAreDoubledInsideQuotes() {
        assertEquals("\"say \"\"hi\"\"\"", new CsvTable(List.of("A"), ",").field("say \"hi\""));
    }

    @Test
    void lineBreaksAreKeptInsideQuotes() {
        assertEquals("\"one\ntwo\"", new CsvTable(List.of("A"), ",").field("one\ntwo"));
        assertEquals("\"one\r\ntwo\"", new CsvTable(List.of("A"), ",").field("one\r\ntwo"));
    }

    @Test
    void aMissingValueIsEmpty() {
        CsvTable table = new CsvTable(List.of("A", "B"), ",").addRow(Arrays.asList("one", null));

        assertEquals("A,B\none,\n", table.toCsv());
    }

    @Test
    void aRowMustHaveAValuePerHeader() {
        CsvTable table = new CsvTable(List.of("A", "B"), ",");

        assertThrows(IllegalArgumentException.class, () -> table.addRow(List.of("only one")));
        assertTrue(table.isEmpty());
    }

    @Test
    void exportFileNamesKeepOnlySafeCharacters() {
        ExportFile file = ExportFile.of("archive-#general/old: 2", "csv", Instant.parse("2026-10-13T09:30:00Z"), "a,b");

        assertEquals("archive-_general_old__2-2026-10-13_09-30-00.csv", file.name());
        assertArrayEquals("a,b".getBytes(StandardCharsets.UTF_8), file.data());
    }
}
