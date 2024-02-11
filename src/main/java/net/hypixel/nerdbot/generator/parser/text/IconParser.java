package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.generator.skyblock.Icon;

import java.util.regex.Matcher;

public class IconParser implements StringParser {

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

            Icon iconEnum = (Icon) Util.findValueOrNull(Icon.VALUES, icon);

            if (iconEnum == null) {
                continue;
            }

            input = input.replace(match, parseIcon(iconEnum, extraData));
        }

        return input;
    }

    private String parseIcon(Icon icon, String extra) {
        if (extra == null) {
            return icon.getIcon();
        }

        try {
            int amount = Integer.parseInt(extra);
            return icon.getIcon().repeat(amount);
        } catch (NumberFormatException e) {
            return icon.getIcon();
        }
    }
}
