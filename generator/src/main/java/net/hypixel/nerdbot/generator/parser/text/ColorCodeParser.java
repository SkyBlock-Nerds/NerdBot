package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.generator.text.ChatFormat;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ColorCodeParser implements StringParser {

    private static final Map<ChatFormat, Pattern> PATTERNS = Arrays.stream(ChatFormat.VALUES)
        .collect(Collectors.toMap(
            chatFormat -> chatFormat,
            chatFormat -> Pattern.compile("%%" + chatFormat.name() + "%%", Pattern.CASE_INSENSITIVE)
        ));

    @Override
    public String parse(String input) {
        for (ChatFormat value : ChatFormat.VALUES) {
            Pattern pattern = PATTERNS.get(value);
            input = pattern.matcher(input).replaceAll(String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + value.getCode());
        }

        return input;
    }
}