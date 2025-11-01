package net.hypixel.nerdbot.generator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.data.Gemstone;
import net.hypixel.nerdbot.generator.parser.StringParser;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;

@Slf4j
public class GemstoneParser implements StringParser {

    @Override
    public String parse(String input) {
        if (input.isBlank()) {
            return input;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(input);

        while (matcher.find()) {
            String match = matcher.group(0);
            String icon = matcher.group(1);
            String extraData = matcher.group(2);
            Gemstone gemstone = Gemstone.byName(icon);

            if (gemstone == null) {
                continue;
            }

            input = input.replace(match, parseAsTier(gemstone, extraData));
        }

        return input;
    }

    /**
     * Parses a gemstone into a formatted string.
     *
     * @param gemstone the {@link Gemstone} to parse
     * @param extra    the type of {@link Gemstone} to parse (tier)
     *
     * @return the formatted string
     */
    private String parseAsTier(Gemstone gemstone, @Nullable String extra) {
        Map<String, String> formattedTiers = gemstone.getFormattedTiers();

        if (extra == null) {
            return "&8[" + gemstone.getIcon() + "]&r";
        }

        String tierFormat = formattedTiers.get(extra.toLowerCase());

        if (tierFormat != null) {
            // Don't use the formatted icon when the placeholder is already preceded by a color code
            // This should only apply to the "unlocked" gemstone variant at this time
            int indexOfPlaceholder = tierFormat.indexOf("%s");
            boolean hasValidColorCode = (indexOfPlaceholder >= 2) &&
                tierFormat.charAt(indexOfPlaceholder - 2) == '&' &&
                isValidColorCode(tierFormat.charAt(indexOfPlaceholder - 1));

            String icon = hasValidColorCode ? gemstone.getIcon() : gemstone.getFormattedIcon();

            return tierFormat.formatted(icon);
        }

        return "&8[" + gemstone.getIcon() + "]&r";  // Default fallback
    }

    /**
     * Determines if the character is a valid color code (0-9, a-f).
     *
     * @param character the character to check
     *
     * @return true if the character is a valid color code, false otherwise
     */
    private boolean isValidColorCode(char character) {
        return (character >= '0' && character <= '9')
            || (character >= 'a' && character <= 'f')
            || (character >= 'A' && character <= 'F');
    }
}
