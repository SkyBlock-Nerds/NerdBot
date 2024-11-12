package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.generator.text.ChatFormat;

import java.util.regex.Pattern;

public class ColorCodeParser implements StringParser {

    @Override
    public String parse(String input) {
        for (ChatFormat value : ChatFormat.VALUES) {
            Pattern pattern = Pattern.compile("%%" + value.name() + "%%", Pattern.CASE_INSENSITIVE);
            input = pattern.matcher(input).replaceAll(String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + value.getCode());
        }

        return input;
    }
}