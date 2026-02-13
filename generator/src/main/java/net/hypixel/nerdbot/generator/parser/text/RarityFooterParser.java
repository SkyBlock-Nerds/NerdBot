package net.hypixel.nerdbot.generator.parser.text;

import lombok.experimental.UtilityClass;
import net.hypixel.nerdbot.core.Tuple;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Utility to strip the rarity/type footer line from lore and surface its metadata.
 */
@UtilityClass
public class RarityFooterParser {

    /**
     * Attempts to remove the rarity footer line (e.g., "EPIC SWORD") from lore and return structured data.
     *
     * @param lore raw lore string in legacy formatting
     *
     * @return tuple where:
     * <ul>
     *     <li>value1 = lore without the footer line</li>
     *     <li>value2 = detected {@link Rarity} (or null if none)</li>
     *     <li>value3 = type text that followed the rarity (e.g., "DUNGEON SWORD"), or null</li>
     * </ul>
     */
    public static Tuple<String, Rarity, String> extract(String lore) {
        if (lore == null || lore.isBlank()) {
            return new Tuple<>(lore, null, null);
        }

        List<String> lines = new ArrayList<>(Arrays.asList(TextWrapper.normalizeNewlines(lore).split("\n", -1)));
        int footerIndex = findLastContentLine(lines);
        if (footerIndex == -1) {
            return new Tuple<>(lore, null, null);
        }

        String footerLine = lines.get(footerIndex);
        Tuple<Rarity, String, Boolean> match = parseFooterLine(footerLine);
        Boolean matched = match.value3();
        if (matched == null || !matched.equals(Boolean.TRUE)) {
            return new Tuple<>(lore, null, null);
        }

        boolean preserveBlankSeparator = footerIndex > 0 && isBlankLine(lines.get(footerIndex - 1));
        lines.remove(footerIndex);
        trimTrailingEmptyLines(lines, preserveBlankSeparator);
        String cleanedLore = lines.isEmpty() ? "" : String.join("\\n", lines);

        return new Tuple<>(cleanedLore, match.value1(), match.value2());
    }

    /**
     * Finds the final lore line that still has visible characters after stripping formatting codes.
     *
     * @param lines normalized lore lines
     *
     * @return index of the last non-empty line, or -1 if there are none
     */
    private static int findLastContentLine(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String stripped = TextWrapper.stripColorCodes(lines.get(i)).trim();
            if (!stripped.isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Attempts to parse a lore line as "RARITY [TYPE]".
     *
     * @param rawLine footer candidate with formatting
     *
     * @return tuple where:
     * <ul>
     *     <li>value1 = detected rarity (or null if none)</li>
     *     <li>value2 = detected type text following the rarity (or null)</li>
     *     <li>value3 = whether the line was recognized as a rarity footer</li>
     * </ul>
     */
    private static Tuple<Rarity, String, Boolean> parseFooterLine(String rawLine) {
        String normalized = normalizeWhitespace(TextWrapper.stripColorCodes(rawLine));
        if (normalized.isEmpty()) {
            return new Tuple<>(null, null, false);
        }

        String normalizedUpper = normalized.toUpperCase(Locale.ROOT);

        for (Rarity rarity : Rarity.getAllRarities()) {
            String display = rarity.getDisplay();
            if (display == null || display.isBlank()) {
                continue;
            }

            String normalizedDisplay = normalizeWhitespace(display);
            if (normalizedDisplay.isEmpty()) {
                continue;
            }

            String displayUpper = normalizedDisplay.toUpperCase(Locale.ROOT);
            if (normalizedUpper.equals(displayUpper)) {
                return new Tuple<>(rarity, null, true);
            }

            if (normalizedUpper.startsWith(displayUpper + " ")) {
                String remainder = normalized.substring(normalizedDisplay.length()).trim();
                return new Tuple<>(rarity, remainder.isEmpty() ? null : remainder, true);
            }
        }

        return new Tuple<>(null, null, false);
    }

    /**
     * Collapses repeated whitespace and trims leading/trailing spaces.
     */
    private static String normalizeWhitespace(String input) {
        if (input == null) {
            return "";
        }

        return input.replaceAll("\\s+", " ").trim();
    }

    /**
     * Removes trailing empty lines from the lore list so we don't leave extra blank lines.
     */
    private static boolean isBlankLine(String line) {
        if (line == null) {
            return true;
        }

        String stripped = TextWrapper.stripColorCodes(line);
        return stripped == null || stripped.trim().isEmpty();
    }

    private static void trimTrailingEmptyLines(List<String> lines, boolean preserveSingleBlank) {
        String preservedBlank = null;

        while (!lines.isEmpty()) {
            String last = lines.getLast();
            if (!isBlankLine(last)) {
                break;
            }

            if (preserveSingleBlank && preservedBlank == null) {
                preservedBlank = last;
            }

            lines.removeLast();
        }

        if (preserveSingleBlank && preservedBlank != null) {
            lines.add(preservedBlank);
        }
    }
}
