package net.hypixel.nerdbot.generator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.data.Flavor;
import net.hypixel.nerdbot.generator.data.ParseType;
import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.generator.text.ChatFormat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FlavorParser implements StringParser {

    private static final Map<String, String> BASE_PLACEHOLDERS = new HashMap<>();

    static {
        try {
            Arrays.stream(ChatFormat.values()).forEach(format -> {
                BASE_PLACEHOLDERS.put(format.name().toLowerCase(), String.valueOf(format.getCode()));
            });
            BASE_PLACEHOLDERS.put("ampersand", String.valueOf(ChatFormat.AMPERSAND_SYMBOL));
            log.info("Initialized FlavorParser with {} placeholders", BASE_PLACEHOLDERS.size());
        } catch (Exception e) {
            log.error("Failed to initialize FlavorParser placeholders", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public String parse(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String flavorName = matcher.group(1);
            String extraData = matcher.group(2);
            Flavor flavor = Flavor.byName(flavorName);

            log.debug("Found flavor text: '{}' with extra data: '{}'", flavorName, extraData);

            if (flavor == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            ParseType parseType = ParseType.byName(flavor.getParseType());
            if (parseType == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String formatted = formatFlavor(flavor, parseType, extraData);
            String replacement = formatted;

            if (!formatted.startsWith("[")) {
                replacement += String.valueOf(ChatFormat.SECTION_SYMBOL) + ChatFormat.RESET.getCode();
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String formatFlavor(Flavor flavor, ParseType parseType, String extraDetails) {
        boolean hasExtra = extraDetails != null && !extraDetails.isEmpty();
        String format = hasExtra ? parseType.getFormatWithDetails() : parseType.getFormatWithoutDetails();

        if (format == null) {
            return "[INVALID FORMAT]";
        }

        Map<String, String> placeholders = new HashMap<>(BASE_PLACEHOLDERS);
        placeholders.put("color", String.valueOf(flavor.getColor().getCode()));
        placeholders.put("subColor", String.valueOf(flavor.getSecondaryColor().getCode()));
        placeholders.put("icon", flavor.getIcon() != null ? flavor.getIcon() : "");
        placeholders.put("stat", flavor.getStat() != null ? flavor.getStat() : "");
        placeholders.put("display", flavor.getDisplay() != null ? flavor.getDisplay() : "");
        placeholders.put("extraDetails", hasExtra ? extraDetails : "");

        String result = format;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replaceAll("\\{" + Pattern.quote(entry.getKey()) + "}", Matcher.quoteReplacement(entry.getValue()));
        }

        return result;
    }
}