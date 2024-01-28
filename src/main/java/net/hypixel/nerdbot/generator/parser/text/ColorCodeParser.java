package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.TextParser;
import net.hypixel.nerdbot.util.ChatFormat;

import java.util.regex.Pattern;

public class ColorCodeParser implements TextParser {

    @Override
    public String parse(String input) {
        for (ChatFormat value : ChatFormat.VALUES) {
            Pattern pattern = Pattern.compile("%%" + value.name() + "%%", Pattern.CASE_INSENSITIVE);
            input = pattern.matcher(input).replaceAll("&" + value.getCode());
        }

        return input;
    }
}