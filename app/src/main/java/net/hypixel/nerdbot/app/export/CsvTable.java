package net.hypixel.nerdbot.app.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Rows written out as CSV. Every field is escaped here, so a value holding the delimiter, a quote or
 * a line break stays in its own column; callers pass values as they are.
 *
 * <p>Exports are opened in spreadsheets, and most values come from members (message text, thread
 * titles, names). A value a spreadsheet would run as a formula is written as text instead, except in
 * the columns named with {@link #formulaColumns}, which hold formulas the bot builds itself.
 */
public class CsvTable {

    private final List<String> headers;
    private final String delimiter;
    private final List<List<String>> rows = new ArrayList<>();
    private final Set<Integer> formulaColumns = new HashSet<>();

    // A spreadsheet treats a cell starting with one of these as a formula
    private static final String FORMULA_STARTS = "=+-@\t\r";
    // Plain numbers such as -5 or +1.5 are harmless and read better left alone
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+([.,]\\d+)?%?");

    public CsvTable(List<String> headers, String delimiter) {
        this.headers = List.copyOf(headers);
        this.delimiter = delimiter;
    }

    /**
     * Marks the columns under these headers as holding formulas written by the bot, which are left
     * to run. Never name a column whose whole value comes from a member.
     *
     * @throws IllegalArgumentException when a name isn't one of the headers
     */
    public CsvTable formulaColumns(String... columnHeaders) {
        for (String header : columnHeaders) {
            int index = headers.indexOf(header);
            if (index < 0) {
                throw new IllegalArgumentException("No column named " + header);
            }
            formulaColumns.add(index);
        }
        return this;
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
        StringBuilder csv = new StringBuilder(line(headers, Set.of()));
        rows.forEach(row -> csv.append(line(row, formulaColumns)));
        return csv.toString();
    }

    private String line(List<String> values, Set<Integer> formulas) {
        return IntStream.range(0, values.size())
            .mapToObj(index -> formulas.contains(index) ? escape(values.get(index)) : field(values.get(index)))
            .collect(Collectors.joining(delimiter)) + "\n";
    }

    /** A value that isn't trusted to be a formula: one that would run as a formula gets a leading apostrophe, which spreadsheets show as text. */
    String field(String value) {
        if (value == null) {
            return "";
        }

        boolean formula = !value.isEmpty() && FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 && !NUMBER.matcher(value).matches();
        return escape(formula ? "'" + value : value);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuotes = value.contains(delimiter) || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
