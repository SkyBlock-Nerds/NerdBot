package net.hypixel.nerdbot.generator.text.wrapper;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.generator.parser.text.ColorCodeParser;
import net.hypixel.nerdbot.generator.parser.text.GemstoneParser;
import net.hypixel.nerdbot.generator.parser.text.IconParser;
import net.hypixel.nerdbot.generator.parser.text.StatParser;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
public class TextWrapper {

    public static List<String> wrapString(String string, int maxLineLength) {
        List<String> lines = new CopyOnWriteArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        String lastColorCode = "";
        StringBuilder lastFormattingCodes = new StringBuilder();
        int currentLength = 0;

        if (!string.contains(" ")) {
            String parsedLine = parseLine(string);
            String strippedString = stripColorCodes(parsedLine);

            int lineStartIndex = 0;
            if (lineStartIndex < strippedString.length()) {
                do {
                    int lineEndIndex = Math.min(lineStartIndex + maxLineLength, strippedString.length());
                    String line = parsedLine.substring(lineStartIndex, lineEndIndex);
                    lines.add(lastColorCode + lastFormattingCodes + line);
                    String[] codes = extractFormattingCodes(line);

                    if (!codes[0].isEmpty()) {
                        lastColorCode = codes[0];
                        lastFormattingCodes = new StringBuilder();
                        log.debug("Last color code: '{}', resetting formatting codes", lastColorCode);
                    }

                    if (!codes[1].isEmpty()) {
                        lastFormattingCodes = new StringBuilder(codes[1]);
                        log.debug("Last formatting codes: '{}'", lastFormattingCodes.toString());
                    }

                    log.debug("Last color code: '{}', last formatting codes: '{}'", lastColorCode, lastFormattingCodes.toString());

                    lineStartIndex = lineEndIndex;
                } while (lineStartIndex < strippedString.length());
            }

            return lines;
        }

        String[] splitLines = string.split("(\n|\\\\n)");

        String[] endCodesLastLine = new String[]{"", ""};

        for (String splitLine : splitLines) {
            String[] words = splitLine.split(" ");

            for (String word : words) {
                log.debug("Processing word: '{}'", word);

                String parsedWord = parseLine(word);
                String strippedWord = stripColorCodes(parsedWord);

                if (currentLength + strippedWord.length() >= maxLineLength) {
                    log.debug("Adding line due to length ({} >= {}): '{}'", currentLength + strippedWord.length(), maxLineLength, currentLine.toString());
                    addLine(lines, currentLine, endCodesLastLine[0], endCodesLastLine[1]);
                    endCodesLastLine = new String[]{lastColorCode, lastFormattingCodes.toString()};

                    currentLine = new StringBuilder();
                    currentLength = 0;
                }

                currentLine.append(parsedWord).append(" ");
                currentLength += strippedWord.length();
                String[] codes = extractFormattingCodes(parsedWord);

                if (!codes[0].isEmpty()) {
                    lastColorCode = codes[0];
                    lastFormattingCodes = new StringBuilder();
                    log.debug("Last color code: '{}', resetting formatting codes", lastColorCode);
                }

                if (!codes[1].isEmpty()) {
                    lastFormattingCodes = new StringBuilder(codes[1]);
                    log.debug("Last formatting codes: '{}'", lastFormattingCodes.toString());
                }
            }

            addLine(lines, currentLine, endCodesLastLine[0], endCodesLastLine[1]);
            endCodesLastLine = new String[]{lastColorCode, lastFormattingCodes.toString()};
            currentLine = new StringBuilder();
            currentLength = 0;

            log.debug("Added line: '{}'", lines.get(lines.size() - 1));
        }

        return lines;
    }

    /**
     * Strips all color codes from a {@link String string}.
     *
     * @param string The {@link String string} to strip color codes from.
     *
     * @return A plain {@link String string}.
     */
    private static String stripColorCodes(String string) {
        return string.replaceAll("[&§][0-9a-fA-FK-ORk-or]", "");
    }

    /**
     * Adds a string to the {@link List list} of lines, with the given color and formatting codes.
     *
     * @param lines           The {@link List list} of lines to add to.
     * @param currentLine     The current line to add.
     * @param colorCode       The color code to add.
     * @param formattingCodes The formatting codes to add.
     */
    private static void addLine(List<String> lines, StringBuilder currentLine, String colorCode, String formattingCodes) {
        lines.add(colorCode + formattingCodes + currentLine.toString().trim());
    }

    /**
     * Apply a series of {@link Parser parsers} to a line of text.
     *
     * @param line The line of text to apply {@link Parser parsers} to.
     *
     * @return A string with all applicable {@link Parser parsers} applied.
     */
    public static String parseLine(String line) {
        return Parser.parseString(line, List.of(
            new ColorCodeParser(),
            new IconParser(),
            new StatParser(),
            new GemstoneParser()
        ));
    }

    /**
     * Extracts the last and all applicable formatting codes from a line of text.
     *
     * @param line The string to extract codes from.
     *
     * @return An array containing the last color code and all formatting codes that come after.
     */
    private static String[] extractFormattingCodes(String line) {
        String lastColorCode = "";
        StringBuilder formattingCodes = new StringBuilder();

        for (int i = 0; i < line.length() - 1; i++) {
            if ((line.charAt(i) == '&' || line.charAt(i) == '§') && i + 1 < line.length()) {
                char code = Character.toLowerCase(line.charAt(i + 1));

                if ("0123456789abcdef".indexOf(code) != -1) {
                    lastColorCode = line.substring(i, i + 2);
                    formattingCodes = new StringBuilder();

                    log.debug("Found color code: '{}' in string: '{}'", line.substring(i, i + 2), line);
                } else if ("klmnor".indexOf(code) != -1) {
                    formattingCodes.append(line, i, i + 2);

                    log.debug("Found formatting code: '{}' in string: '{}'", line.substring(i, i + 2), line);
                }
            }
        }

        log.debug("Found last color code: '{}', formatting codes: '{}'", lastColorCode, formattingCodes.toString());
        return new String[]{lastColorCode, formattingCodes.toString()};
    }
}
