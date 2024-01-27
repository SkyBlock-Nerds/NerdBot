package net.hypixel.nerdbot.generator.parser;

import java.util.List;
import java.util.regex.Pattern;

public interface TextParser {

    Pattern VARIABLE_PATTERN = Pattern.compile("%%([a-zA-Z_]+)(?::(\\d+))?%%");

    String parse(String input);

    static String parseString(String input, List<TextParser> parsers) {
        String result = input;

        for (TextParser parser : parsers) {
            result = parser.parse(result);
        }

        return result;
    }
}
