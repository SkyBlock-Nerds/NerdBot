package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.generator.skyblock.Gemstone;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

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
            Gemstone gemstoneEnum = (Gemstone) Util.findValue(Gemstone.VALUES, icon);

            if (gemstoneEnum == null) {
                continue;
            }

            input = input.replace(match, parseAsTier(gemstoneEnum, extraData));
        }

        return input;
    }

    /**
     * Parses a gemstone into a formatted string.
     *
     * @param gemstone the {@link Gemstone} to parse
     * @param extra    the type of {@link Gemstone} to parse
     *
     * @return the formatted string
     */
    private String parseAsTier(Gemstone gemstone, @Nullable String extra) {
        if (extra == null) {
            return "&8[" + gemstone.getIcon() + "]&r";
        }

        return switch (extra.toLowerCase()) {
            case "unlocked" -> "&8[&7" + gemstone.getIcon() + "&8]&r";
            case "rough" -> "&f[" + gemstone.getFormattedIcon() + "&f]&r";
            case "flawed" -> "&a[" + gemstone.getFormattedIcon() + "&a]&r";
            case "fine" -> "&9[" + gemstone.getFormattedIcon() + "&9]&r";
            case "flawless" -> "&5[" + gemstone.getFormattedIcon() + "&5]&r";
            case "perfect" -> "&6[" + gemstone.getFormattedIcon() + "&6]&r";
            default -> "&8[" + gemstone.getIcon() + "]&r";
        };
    }
}
