package net.hypixel.nerdbot.generator.text.wrapper;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.generator.parser.text.ColorCodeParser;
import net.hypixel.nerdbot.generator.parser.text.FlavorParser;
import net.hypixel.nerdbot.generator.parser.text.GemstoneParser;
import net.hypixel.nerdbot.generator.parser.text.IconParser;
import net.hypixel.nerdbot.generator.parser.text.StatParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class TextWrapper {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("[&§][0-9a-fA-FK-ORk-or]");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("(?:\n|\\\\n)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+|\\s+");

    private static final List<Parser<String>> PARSERS = List.of(
        new ColorCodeParser(),
        new IconParser(),
        new FlavorParser(),
        new StatParser(),
        new GemstoneParser()
    );

    /**
     * Holds the last color code and active formatting codes to carry over between lines/segments.
     */
    private record FormatState(String lastColor, String formattingCodes) {
        // Represents the initial state with no formatting.
        private static final FormatState EMPTY = new FormatState("", "");

        /**
         * Creates the formatting prefix string (e.g., "&c&l") to prepend to a new line/segment.
         */
        public String prefix() {
            return lastColor + formattingCodes;
        }

        /**
         * Calculates the formatting state at the end of a given segment
         * based on the state at the beginning of it
         *
         * @param segment      The text segment to analyze.
         * @param initialState The {@link FormatState} before this segment.
         *
         * @return The {@link FormatState} after processing this segment.
         */
        public static FormatState deriveStateFromSegment(String segment, FormatState initialState) {
            String lastColor = initialState.lastColor();
            StringBuilder formatting = new StringBuilder(initialState.formattingCodes());
            boolean colorFoundInSegment = false;

            for (int i = 0; i < segment.length(); i++) {
                if ((segment.charAt(i) == '&' || segment.charAt(i) == '§') && i + 1 < segment.length()) {
                    char code = Character.toLowerCase(segment.charAt(i + 1));
                    String codeStr = segment.substring(i, i + 2);

                    if ("0123456789abcdef".indexOf(code) != -1) {
                        lastColor = codeStr;
                        formatting = new StringBuilder(); // We want to reset the formatting when changing color
                        colorFoundInSegment = true;
                        i++; // Skip the code character
                        log.debug("Found color code: '{}'", codeStr);
                    } else if ("klmnor".indexOf(code) != -1) {
                        // Append formatting codes to the formatting string
                        if (formatting.indexOf(codeStr) == -1) {
                            formatting.append(codeStr);
                            log.debug("Found formatting code: '{}'", codeStr);
                        }
                        i++;
                    }
                }
            }
            return new FormatState(lastColor, formatting.toString());
        }
    }

    /**
     * Wraps a string to a specified maximum line length, preserving Minecraft formatting codes.
     *
     * @param input         The input string, potentially containing placeholders and formatting.
     * @param maxLineLength The maximum visible length of each line (must be > 0).
     *
     * @return A list of strings, representing the wrapped lines.
     */
    public static List<String> wrapString(String input, int maxLineLength) {
        List<String> lines = new ArrayList<>();

        if (input == null || input.isEmpty()) {
            return lines;
        }

        if (maxLineLength <= 0) {
            lines.add(input);
            return lines;
        }

        String parsedInput = normalizeNewlines(parseLine(input));
        String[] rawLines = NEWLINE_PATTERN.split(parsedInput, -1);
        FormatState currentFormatState = FormatState.EMPTY;

        for (String rawLine : rawLines) {
            log.debug("Processing raw line: '{}'", rawLine);

            if (rawLine.isEmpty()) {
                lines.add(""); // Preserve empty lines between paragraphs
                currentFormatState = FormatState.EMPTY;
                log.debug("Adding empty line");
                continue;
            }

            StringBuilder currentLineBuilder = new StringBuilder();
            int currentVisibleLength = 0;

            // Process each token individually
            Matcher tokenMatcher = TOKEN_PATTERN.matcher(rawLine);
            while (tokenMatcher.find()) {
                String token = tokenMatcher.group();

                if (token.trim().isEmpty()) {
                    int whitespaceLength = token.length();

                    // If whitespace alone would exceed the line, wrap before adding more spaces
                    if (currentVisibleLength + whitespaceLength > maxLineLength && !currentLineBuilder.isEmpty()) {
                        String finishedLine = currentLineBuilder.toString();
                        lines.add(currentFormatState.prefix() + finishedLine);
                        log.debug("Adding line before whitespace overflow: '{}'", lines.getLast());

                        currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);
                        currentLineBuilder = new StringBuilder();
                        currentVisibleLength = 0;
                    }

                    boolean manualIndent = tokenMatcher.start() == 0;
                    if (currentLineBuilder.isEmpty() && !manualIndent) {
                        log.debug("Skipping leading whitespace originating from automatic wrap");
                        continue;
                    }

                    currentLineBuilder.append(token);
                    currentVisibleLength += whitespaceLength;
                    log.debug("Appended whitespace token: '{}' (visible length now {})", token.replace("\t", "\\t"), currentVisibleLength);
                    continue;
                }

                String strippedWord = stripColorCodes(token);
                int wordVisibleLength = strippedWord.length();

                // Handle words that are longer than the max line length
                if (wordVisibleLength > maxLineLength) {
                    if (!currentLineBuilder.isEmpty()) {
                        String finishedLine = currentLineBuilder.toString();
                        lines.add(currentFormatState.prefix() + finishedLine);
                        log.debug("Adding line before splitting long word: '{}'", lines.get(lines.size() - 1));

                        currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);
                        currentLineBuilder = new StringBuilder();
                        currentVisibleLength = 0;
                    }

                    currentFormatState = splitLongWord(token, maxLineLength, lines, currentFormatState);
                    currentVisibleLength = 0;
                } else if (currentVisibleLength + wordVisibleLength <= maxLineLength) {
                    currentLineBuilder.append(token);
                    currentVisibleLength += wordVisibleLength;
                    log.debug("Added word token to current line: '{}' (visible length now {})", token, currentVisibleLength);
                } else {
                    String finishedLine = currentLineBuilder.toString();
                    lines.add(currentFormatState.prefix() + finishedLine);
                    log.debug("Adding line due to length: '{}' (" + "currentVisibleLength: {}, wordVisibleLength: {})",
                        lines.getLast(), currentVisibleLength, wordVisibleLength
                    );

                    currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);

                    currentLineBuilder = new StringBuilder(token);
                    currentVisibleLength = wordVisibleLength;
                }
            }

            // Add any remaining text
            if (!currentLineBuilder.isEmpty()) {
                String finishedLine = currentLineBuilder.toString();

                lines.add(currentFormatState.prefix() + finishedLine);
                log.debug("Adding last line: '{}'", lines.get(lines.size() - 1));

                // Update format state for the next paragraph
                currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);
            }
        }

        return lines;
    }

    /**
     * Splits a single word that is longer than maxLineLength into multiple lines,
     * attempting to preserve formatting codes across each line.
     *
     * @param word          The word to split.
     * @param maxLineLength The maximum visible length per line.
     * @param lines         The list to add the split segments to.
     * @param initialState  The {@link FormatState} entering this word.
     *
     * @return The {@link FormatState} at the end of the split word.
     */
    private static FormatState splitLongWord(String word, int maxLineLength, List<String> lines, FormatState initialState) {
        log.debug("Splitting long word: '{}'", word);

        FormatState currentWordFormatState = initialState;
        int currentActualIndex = 0; // Position in the original string

        while (currentActualIndex < word.length()) {
            int currentVisibleLength = 0;
            int segmentEndActualIndex = currentActualIndex; // End position in the original string

            // Iterate through the word to find where to cut based on visible characters
            for (int i = currentActualIndex; i < word.length(); ) {
                char currentChar = word.charAt(i);

                // Check for formatting codes (& or § followed by a valid code character)
                if ((currentChar == '&' || currentChar == '§') && i + 1 < word.length()) {
                    char code = Character.toLowerCase(word.charAt(i + 1));

                    if ("0123456789abcdefklmnor".indexOf(code) != -1) {
                        i += 2; // Skip formatting codes since they don't count towards visible length
                    } else {
                        currentVisibleLength++; // Treat first character as a visible character
                        i++;
                    }
                } else {
                    currentVisibleLength++; // Regular visible character
                    i++;
                }

                segmentEndActualIndex = i; // Mark the position after the processed character

                // Stop if this segment reaches the max visible length
                if (currentVisibleLength >= maxLineLength) {
                    log.debug("Reached max visible length: {} (currentVisibleLength: {})", maxLineLength, currentVisibleLength);
                    break;
                }
            }

            // Extract the substring for this line segment
            String lineSegment = word.substring(currentActualIndex, segmentEndActualIndex);

            // Add the segment to the list of lines, prepending existing formatting
            lines.add(currentWordFormatState.prefix() + lineSegment);
            log.debug("Added split word segment: '{}'", lines.get(lines.size() - 1));

            // Determine the formatting state to be used for the next segment
            currentWordFormatState = FormatState.deriveStateFromSegment(lineSegment, currentWordFormatState);
            // Move the starting point for the next segment
            currentActualIndex = segmentEndActualIndex;
        }

        // Return the final state after processing the entire word
        return currentWordFormatState;
    }

    /**
     * Normalizes newline handling so that combinations like actual newlines
     * plus literal "\n" markers are treated as a single line break.
     *
     * @param input Parsed input string
     *
     * @return Normalized string with redundant newline markers removed
     */
    public static String normalizeNewlines(String input) {
        if (input.indexOf('\r') != -1) {
            input = input.replace("\r\n", "\n").replace('\r', '\n');
        }

        StringBuilder normalized = new StringBuilder(input.length());

        for (int i = 0; i < input.length(); ) {
            char current = input.charAt(i);

            if (current == '\n') {
                normalized.append('\n');
                i++;

                while (i + 1 < input.length() && input.charAt(i) == '\\' && input.charAt(i + 1) == 'n') {
                    i += 2;
                }
                continue;
            }

            if (current == '\\' && i + 1 < input.length() && input.charAt(i + 1) == 'n') {
                if (i + 2 < input.length() && input.charAt(i + 2) == '\n') {
                    i += 2;
                    continue;
                }

                normalized.append('\n');
                i += 2;
                continue;
            }

            normalized.append(current);
            i++;
        }

        return normalized.toString();
    }

    /**
     * Strips all known Minecraft color and formatting codes from a string.
     *
     * @param string The string to strip codes from.
     *
     * @return A plain string with codes removed, or an empty string if input is null/empty.
     */
    public static String stripColorCodes(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }

        return STRIP_COLOR_PATTERN.matcher(string).replaceAll("");
    }

    /**
     * Applies a list of {@link Parser parsers} to a line of text. Parsers are
     * executed in the order they are defined in the {@link #PARSERS} array.
     *
     * @param line The line of text to parse.
     *
     * @return A string with all applicable parsers applied.
     */
    public static String parseLine(String line) {
        if (line == null) {
            return "";
        }

        return Parser.parseString(line, PARSERS);
    }
}
