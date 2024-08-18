package net.hypixel.nerdbot.generator.text.wrapper;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Log4j2
public class LineWrapper {

    private static final String DELIMITER = "\0"; // Null character
    private static final String DELIMITER_REGEX = "(?<=" + DELIMITER + ")|(?=" + DELIMITER + ")";
    private static final String SPLIT_REGEX = "(\\s+|\\S+)";
    private static final String VALID_COLOR_CODES = "0123456789abcdef";
    private static final String VALID_FORMATTING_CODES = "klmnor";

    private final int maxLineLength;
    private final List<List<LineSegment>> lines;
    private final StringBuilder currentLine;
    private String lastColorCode;
    private String lastFormattingCodes;

    public LineWrapper(int maxLineLength) {
        this.maxLineLength = maxLineLength;
        this.lines = new ArrayList<>();
        this.currentLine = new StringBuilder();
        this.lastColorCode = "";
        this.lastFormattingCodes = "";
    }

    public List<List<LineSegment>> wrapText(String text) {
        String[] words = text.split(SPLIT_REGEX);

        log.debug("Split words: {}", Arrays.toString(words));

        for (String word : words) {
            addWord(word);
        }

        // Process remaining text in currentLine
        if (!currentLine.isEmpty()) {
            addCurrentLineToLines();
        }

        handleNoSpaces(text);

        log.debug("Returning lines: {}", lines);
        return lines;
    }

    /**
     * Splits the input array of strings by newline characters represented as \\n
     * and then by spaces, returning the result as an array of strings.
     * <p>
     * It is done this way to preserve empty strings when splitting by user input.
     *
     * @param inputArray the input array of {@link String strings}
     *
     * @return a {@link String} array containing the split results
     */
    public static String[] splitArray(String[] inputArray) {
        List<String> result = new ArrayList<>();

        log.debug("Splitting input array: {}", Arrays.toString(inputArray));

        for (String str : inputArray) {
            log.debug("Processing string: '{}'", str);

            str = str.replace("\\n", DELIMITER);

            log.debug("Replaced \\n with delimiter: '{}'", str);

            // Preserve empty strings when splitting
            String[] parts = str.split(DELIMITER_REGEX);

            log.debug("Split parts: '{}'", Arrays.toString(parts));

            for (String part : parts) {
                log.debug("Processing part: '{}'", part);

                if (part.equals(DELIMITER)) {
                    log.debug("Part equals delimiter, adding empty string to result");
                    result.add("");
                } else {
                    String[] words = part.split(" ");
                    Collections.addAll(result, words);
                    log.debug("Added words to result: '{}'", Arrays.toString(words));
                }
            }
        }

        log.debug("Returning result: '{}'", result);
        return result.toArray(new String[0]);
    }

    private void addWord(String word) {
        log.debug("Adding word: {}", word);

        /*if (word.isBlank()) {
            log.debug("Skipping blank word");
            addCurrentLineToLines();
            return;
        }*/

        if (!currentLine.isEmpty() && currentLine.length() + word.length() + 1 > maxLineLength) {
            log.debug("Current line length + word length + 1 ({}) is greater than maxLineLength ({}), adding current line to lines", currentLine.length() + word.length() + 1, maxLineLength);
            addCurrentLineToLines();
        }

        /*if (!currentLine.isEmpty()) {
            log.debug("Current line is not empty, appending space to current line");
            currentLine.append(" ");
        }*/

        log.debug("Appending word: {} to current line: {}", word, currentLine);
        currentLine.append(word);
    }

    private void addCurrentLineToLines() {
        String lineToAdd = currentLine.toString().trim();

        log.debug("lineToAdd: {}", lineToAdd);

        if (!lineToAdd.startsWith("&")) {
            lineToAdd = lastColorCode + lastFormattingCodes + lineToAdd;
            log.debug("lineToAdd with color code: {}", lineToAdd);
        }

        lines.add(LineSegment.fromLegacy(lineToAdd, '&'));
        log.debug("Added lineToAdd {} to lines: {}", LineSegment.fromLegacy(lineToAdd, '&'), lines);
        updateFormattingCodes(lineToAdd);
        currentLine.setLength(0);
        log.debug("Current line length set to 0");
    }

    private void handleNoSpaces(String text) {
        log.debug("Handling no spaces in text: {}", text);

        if (lines.isEmpty() && !text.contains(" ")) {
            log.debug("No spaces found in text, adding text to lines");

            for (int i = 0; i < text.length(); i += maxLineLength) {
                String part = text.substring(i, Math.min(i + maxLineLength, text.length()));

                log.debug("Part: {}", part);

                if (!part.startsWith("&")) {
                    part = lastColorCode + lastFormattingCodes + part;
                    log.debug("Part does not start with color code, adding it: {}", part);
                }

                lines.add(LineSegment.fromLegacy(part, '&'));
                log.debug("Added part {} to lines: {}", LineSegment.fromLegacy(part, '&'), lines);
                updateFormattingCodes(part);
            }
        }
    }

    private void updateFormattingCodes(String line) {
        lastColorCode = getLastColorCode(line);
        lastFormattingCodes = getLastFormattingCodes(line);

        log.debug("Updated lastColorCode: {} and lastFormattingCodes: {}", lastColorCode, lastFormattingCodes);
    }

    private String getLastColorCode(String text) {
        String colorCode = "";
        for (int i = text.length() - 2; i >= 0; i--) {
            if (text.charAt(i) == '&' && (i + 1 < text.length())) {
                char code = text.charAt(i + 1);

                log.debug("Checking code: {}", code);

                if (Character.isLetterOrDigit(code)) {
                    colorCode = text.substring(i, i + 2);

                    if (VALID_COLOR_CODES.indexOf(code) != -1) {
                        log.debug("Found color code: {}", colorCode);
                        break;
                    }
                }
            }
        }

        log.debug("Returning color code: {}", colorCode);
        return colorCode;
    }

    private String getLastFormattingCodes(String text) {
        StringBuilder formattingCodes = new StringBuilder();
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '&' && (i + 1 < text.length())) {
                char code = text.charAt(i + 1);

                log.debug("Checking code: {}", code);

                if (VALID_FORMATTING_CODES.indexOf(code) != -1) {
                    log.debug("Found formatting code: {}", code);
                    formattingCodes.append(text, i, i + 2);
                }
            }
        }

        log.debug("Returning formatting codes: {}", formattingCodes.toString());
        return formattingCodes.toString();
    }
}
