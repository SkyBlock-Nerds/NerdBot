package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.TextParser;
import net.hypixel.nerdbot.util.ChatFormat;

public class ColorCodeParser implements TextParser {

    @Override
    public String parse(String input) {
        for (ChatFormat value : ChatFormat.VALUES) {
            input = input.replaceAll("%%" + value.name() + "%%", "&" + value.getCode());
        }

        return input;
    }
}