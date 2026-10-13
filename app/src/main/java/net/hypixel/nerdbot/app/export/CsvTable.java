package net.hypixel.nerdbot.app.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rows written out as CSV. Every field is escaped here, so a value holding the delimiter, a quote or
 * a line break stays in its own column; callers pass values as they are.
 */
public class CsvTable {

    private final List<String> headers;
    private final String delimiter;
    private final List<List<String>> rows = new ArrayList<>();

    public CsvTable(List<String> headers, String delimiter) {
        this.headers = List.copyOf(headers);
        this.delimiter = delimiter;
    }

    /**
     * @throws IllegalArgumentException when the row doesn't have one value per header
     */
    public CsvTable addRow(List<String> row) {
        if (row.size() != headers.size()) {
            throw new IllegalArgumentException("Expected " + headers.size() + " values but got " + row.size());
        }
        rows.add(Collections.unmodifiableList(new ArrayList<>(row)));
        return this;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int size() {
        return rows.size();
    }

    public String toCsv() {
        StringBuilder csv = new StringBuilder(line(headers));
        rows.forEach(row -> csv.append(line(row)));
        return csv.toString();
    }

    private String line(List<String> values) {
        return values.stream().map(this::field).collect(Collectors.joining(delimiter)) + "\n";
    }

    String field(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuotes = value.contains(delimiter) || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
