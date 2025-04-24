package net.hypixel.nerdbot.generator.text.wrapper;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.generator.parser.text.ColorCodeParser;
import net.hypixel.nerdbot.generator.parser.text.GemstoneParser;
import net.hypixel.nerdbot.generator.parser.text.IconParser;
import net.hypixel.nerdbot.generator.parser.text.StatParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Log4j2
public class TextWrapper {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("[&§][0-9a-fA-FK-ORk-or]");
    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile(" ");

    private static final List<Parser<String>> PARSERS = List.of(
        new ColorCodeParser(),
        new IconParser(),
        new StatParser(),
        new GemstoneParser()
    );

    /**
     * Represents a word segment with its stripped form and visible character count.
     */
    private record WordInfo(String original, String stripped, int visibleLength) {
        static WordInfo from(String text) {
            String stripped = stripColorCodes(text);
            return new WordInfo(text, stripped, stripped.length());
        }
    }

    /**
     * Holds the last color code and active formatting codes to carry over between lines/segments.
     */
    private record FormatState(String lastColor, String formattingCodes) {
        static final FormatState EMPTY = new FormatState("", "");

        /**
         * Creates the formatting prefix string (e.g., "&c&l") to prepend to a new line/segment.
         */
        String prefix() {
            return lastColor + formattingCodes;
        }

        /**
         * Calculates the formatting state at the end of a given segment
         * based on the state at the beginning of it
         *
         * @param segment  The text segment to analyse.
         * @param previous The {@link FormatState} before this segment.
         *
         * @return The {@link FormatState}  at the end of the segment.
         */
        static FormatState deriveFromSegment(String segment, FormatState previous) {
            String lastColor = previous.lastColor();
            StringBuilder formatting = new StringBuilder(previous.formattingCodes());
            boolean colorFoundInSegment = false;

            for (int i = 0; i < segment.length(); i++) {
                if ((segment.charAt(i) == '&' || segment.charAt(i) == '§') && i + 1 < segment.length()) {
                    char code = Character.toLowerCase(segment.charAt(i + 1));
                    String codeStr = segment.substring(i, i + 2);
                    if ("0123456789abcdef".indexOf(code) >= 0) {
                        lastColor = codeStr;
                        formatting = new StringBuilder();
                        colorFoundInSegment = true;
                        i++;
                        log.debug("Found color code: '{}'", codeStr);
                    } else if ("klmnor".indexOf(code) >= 0) {
                        if (colorFoundInSegment || previous.lastColor().equals(lastColor)) {
                            if (formatting.indexOf(codeStr) < 0) {
                                formatting.append(codeStr);
                                log.debug("Found formatting code: '{}'", codeStr);
                            }
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

        String parsedInput = parseLine(input);
        List<String> rawLines = parsedInput.lines().toList();
        FormatState currentFormatState = FormatState.EMPTY;

        for (String rawLine : rawLines) {
            log.debug("Processing raw line: '{}'", rawLine);

            if (rawLine.isBlank()) {
                lines.add(""); // Preserve empty lines between paragraphs
                currentFormatState = FormatState.EMPTY;
            } else {
                WrappedLineResult result = wrapSingleRawLine(rawLine, currentFormatState, maxLineLength);
                lines.addAll(result.wrappedLines());
                currentFormatState = result.endState();
            }
        }

        return lines;
    }

    /**
     * Holds the wrapped lines for one raw line plus the final {@link FormatState}
     */
    private record WrappedLineResult(List<String> wrappedLines, FormatState endState) {
    }

    /**
     * Wraps one raw line (including spaces) into wrapped lines.
     *
     * @param rawLine       The original line with leading spaces.
     * @param initialState  The {@link FormatState} at line start.
     * @param maxLineLength The maximum visible line length.
     *
     * @return Wrapped lines and final {@link FormatState}.
     */
    private static WrappedLineResult wrapSingleRawLine(String rawLine, FormatState initialState, int maxLineLength) {
        String withoutIndent = rawLine.stripLeading();
        String indent = rawLine.substring(0, rawLine.length() - withoutIndent.length());
        String linePrefix = initialState.prefix() + indent;

        List<String> lines = new ArrayList<>();
        FormatState state = initialState;
        StringBuilder currentLineBuilder = new StringBuilder(indent);
        int currentVisibleLength = indent.length();
        boolean isFirstWord = true;

        // Split the line into words
        for (String rawWord : WORD_SPLIT_PATTERN.split(withoutIndent)) {
            if (rawWord.isEmpty()) {
                continue;
            }

            WordInfo info = WordInfo.from(rawWord);
            int visibleLength = info.visibleLength();

            if (visibleLength > maxLineLength) {
                // Write out current line content before splitting the long word
                if (currentLineBuilder.length() > indent.length()) {
                    String completedLine = currentLineBuilder.toString().stripTrailing();
                    lines.add(linePrefix + completedLine);
                    state = FormatState.deriveFromSegment(completedLine, state);
                    currentLineBuilder = new StringBuilder(indent);
                    currentVisibleLength = indent.length();
                    isFirstWord = true;
                }

                state = splitLongWord(rawWord, maxLineLength, lines, state, indent);
                continue;
            }

            int spaceNeeded = isFirstWord ? 0 : 1;
            // Start a new wrapped line if there isn't enough space
            if (currentVisibleLength + spaceNeeded + visibleLength > maxLineLength) {
                String completedLine = currentLineBuilder.toString().stripTrailing();
                lines.add(linePrefix + completedLine);
                state = FormatState.deriveFromSegment(completedLine, state);
                currentLineBuilder = new StringBuilder(indent);
                currentVisibleLength = indent.length();
                spaceNeeded = 0;
            }

            if (spaceNeeded > 0) {
                currentLineBuilder.append(' ');
                currentVisibleLength++;
            }

            currentLineBuilder.append(rawWord);
            currentVisibleLength += visibleLength;
            isFirstWord = false;
        }

        // Add any remaining content to the current line
        if (currentLineBuilder.length() > indent.length()) {
            String completedLine = currentLineBuilder.toString().stripTrailing();
            lines.add(linePrefix + completedLine);
            state = FormatState.deriveFromSegment(completedLine, state);
        }

        return new WrappedLineResult(lines, state);
    }

    /**
     * Splits a long word into segments that fit within maxLineLength,
     * preserving formatting codes and indent.
     *
     * @param word          The word to split.
     * @param maxLineLength The maximum visible length per segment.
     * @param lines         The list to add the split segments into.
     * @param initialState  The {@link FormatState} entering this word.
     * @param indent        Leading spaces to preserve
     *
     * @return the {@link FormatState} after all segments
     */
    private static FormatState splitLongWord(String word, int maxLineLength, List<String> lines, FormatState initialState, String indent) {
        FormatState currentWordFormatState = initialState;
        String prefix = initialState.prefix() + indent;
        int currentActualIndex = 0;

        while (currentActualIndex < word.length()) {
            int currentVisibleLength = 0;
            int segmentEndActualIndex = currentActualIndex;

            for (int i = currentActualIndex; i < word.length(); ) {
                if (isFormattingCodeAt(word, i)) {
                    // Skip over the formatting code
                    i += 2;
                } else {
                    currentVisibleLength++;
                    i++;
                }

                segmentEndActualIndex = i;

                if (currentVisibleLength >= maxLineLength - indent.length()) {
                    break;
                }
            }

            String lineSegment = word.substring(currentActualIndex, segmentEndActualIndex);

            lines.add(prefix + lineSegment);
            log.debug("Added split word segment: '{}'", lines.getLast());

            currentWordFormatState = FormatState.deriveFromSegment(lineSegment, currentWordFormatState);
            currentActualIndex = segmentEndActualIndex;
        }

        return currentWordFormatState;
    }

    /**
     * Strips all known Minecraft color and formatting codes from a string.
     *
     * @param string The string to strip codes from.
     *
     * @return A plain string with codes removed, or an empty string if input is null/empty.
     */
    private static String stripColorCodes(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }

        return STRIP_COLOR_PATTERN.matcher(string).replaceAll("");
    }

    /**
     * Returns true if at position in text there's a Minecraft formatting
     * code ('&' or '§' followed by a valid color character)
     */
    private static boolean isFormattingCodeAt(CharSequence text, int position) {
        if (position + 1 >= text.length()) {
            return false;
        }

        char prefix = text.charAt(position);
        char codeChar = Character.toLowerCase(text.charAt(position + 1));

        if (prefix != '&' && prefix != '§') {
            return false;
        }

        return "0123456789abcdefklmnor".indexOf(codeChar) >= 0;
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
