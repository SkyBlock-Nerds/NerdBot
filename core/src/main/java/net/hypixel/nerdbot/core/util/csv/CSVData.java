package net.hypixel.nerdbot.core.util.csv;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class CSVData {
    private List<String> headers;
    private List<List<String>> content;
    private String delimiter;

    /**
     * Create a new CSV data object
     *
     * @param headers   Headers for the CSV data
     * @param content   The content of the CSV data, excluding headers
     * @param delimiter The delimiter to use for the CSV data
     */
    public CSVData(List<String> headers, List<List<String>> content, String delimiter) {
        this.headers = headers;
        this.content = content;
        this.delimiter = delimiter;
    }

    /**
     * Create a new CSV data object
     *
     * @param headers Headers for the CSV data
     * @param content The content of the CSV data, excluding headers
     */
    public CSVData(List<String> headers, List<List<String>> content) {
        this(headers, content, ",");
    }

    /**
     * Create a new CSV data object
     *
     * @param headers   Headers for the CSV data
     * @param delimiter The delimiter to use for the CSV data
     */
    public CSVData(List<String> headers, String delimiter) {
        this(headers, new ArrayList<>(), delimiter);
    }

    /**
     * Create a new CSV data object
     *
     * @param headers Headers for the CSV data
     */
    public CSVData(List<String> headers) {
        this(headers, new ArrayList<>(), ",");
    }

    /**
     * Add a row to the CSV data
     *
     * @param row The row to add
     */
    public void addRow(List<String> row) {
        content.add(row);
    }

    /**
     * Remove a row from the CSV data
     *
     * @param index The index of the row to remove
     */
    public void removeRow(int index) {
        content.remove(index);
    }

    /**
     * Add a column to the CSV data
     *
     * @param header The header for the column
     * @param column The data for the column
     */
    public void addColumn(String header, List<String> column) {
        this.headers.add(header);

        for (int i = 0; i < column.size(); i++) {
            content.get(i).add(column.get(i));
        }
    }

    /**
     * Remove a column from the CSV data
     *
     * @param index The index of the column to remove
     */
    public void removeColumn(int index) {
        headers.remove(index);

        for (List<String> row : content) {
            row.remove(index);
        }
    }

    /**
     * Set the value of a cell in the CSV data
     *
     * @param row    The row of the cell
     * @param column The column of the cell
     * @param value  The value to set
     */
    public void setCell(int row, int column, String value) {
        content.get(row).set(column, value);
    }

    /**
     * Get the value of a cell in the CSV data
     *
     * @param row    The row of the cell
     * @param column The column of the cell
     *
     * @return The value of the cell
     */
    public String getCell(int row, int column) {
        return content.get(row).get(column);
    }

    /**
     * Get a row from the CSV data
     *
     * @param index The index of the row to get
     *
     * @return The row
     */
    public List<String> getRow(int index) {
        return content.get(index);
    }

    /**
     * Set a row in the CSV data
     *
     * @param index The index of the row to set
     * @param row   The row to set
     */
    public void setRow(int index, List<String> row) {
        content.set(index, row);
    }

    /**
     * Get a column from the CSV data
     *
     * @param index The index of the column to get
     *
     * @return The column
     */
    public List<String> getColumn(int index) {
        return content.stream().map(row -> row.get(index)).toList();
    }

    /**
     * Set a column in the CSV data
     *
     * @param index  The index of the column to set
     * @param column The column to set
     */
    public void setColumn(int index, List<String> column) {
        for (int i = 0; i < column.size(); i++) {
            content.get(i).set(index, column.get(i));
        }
    }

    /**
     * Get the number of rows in the CSV data
     *
     * @return The number of rows
     */
    public int getRowCount() {
        return content.size();
    }

    /**
     * Get the number of columns in the CSV data
     *
     * @return The number of columns
     */
    public int getColumnCount() {
        return headers.size();
    }

    /**
     * Clear the CSV data
     */
    public void clear() {
        headers.clear();
        content.clear();
    }

    /**
     * Check if the CSV data is empty
     *
     * @return Whether the CSV data is empty
     */
    public boolean isEmpty() {
        return headers.isEmpty() && content.isEmpty();
    }

    /**
     * Check if the CSV data has content
     *
     * @return Whether the CSV data has content
     */
    public boolean hasContent() {
        return !content.isEmpty();
    }

    /**
     * Convert the CSV data to a string
     *
     * @return The CSV data as a string
     */
    public String toCSV() {
        StringBuilder csv = new StringBuilder();

        csv.append(String.join(delimiter, headers)).append("\n");

        for (List<String> row : content) {
            csv.append(String.join(delimiter, row)).append("\n");
        }

        return csv.toString();
    }
}