package net.hypixel.nerdbot.generator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.data.ParseType;
import net.hypixel.nerdbot.generator.data.Stat;
import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.generator.text.ChatFormat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class StatParser implements StringParser {

    private static final Map<String, String> BASE_PLACEHOLDERS = new HashMap<>();

    static {
        try {
            Arrays.stream(ChatFormat.values()).forEach(format -> {
                BASE_PLACEHOLDERS.put(format.name().toLowerCase(), String.valueOf(format.getCode()));
            });
            BASE_PLACEHOLDERS.put("ampersand", String.valueOf(ChatFormat.AMPERSAND_SYMBOL));
            log.info("Initialized StatParser with {} placeholders", BASE_PLACEHOLDERS.size());
        } catch (Exception e) {
            log.error("Failed to initialize StatParser placeholders", e);
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
            String statName = matcher.group(1); // Group 1: Stat Name (e.g., HEALTH)
            String extraData = matcher.group(2); // Group 2: Optional extra data (e.g., 100) - can be null
            Stat stat = Stat.byName(statName);

            log.debug("Found stat: '" + statName + "' with extra data: '" + extraData + "'");

            if (stat == null) {
                log.warn("Could not find stat by name: '" + statName + "' in input: '" + input + "'");
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            ParseType parseType = ParseType.byName(stat.getParseType());
            if (parseType == null) {
                log.warn("Could not find parse type by name: '" + stat.getParseType() + "' for stat: '" + stat.getName() + "'");
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            log.debug("Using parse type: '" + parseType.getName() + "' for stat: '" + stat.getName() + "' with extra data: '" + extraData + "'");

            String formattedStat = formatStat(stat, parseType, extraData);
            String replacement = formattedStat;

            log.debug("Replacement before formatting: " + replacement + " for stat: " + stat.getName() + " with extra data: " + extraData + " and parse type: " + parseType.getName());
            log.debug("Formatted stat: " + formattedStat);

            if (!formattedStat.startsWith("[")) {
                replacement += String.valueOf(ChatFormat.SECTION_SYMBOL) + ChatFormat.RESET.getCode();
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Formats a {@link Stat} using the given {@link ParseType} and extra details (if any)
     *
     * @param stat         The {@link Stat} to format
     * @param parseType    The {@link ParseType} to use for formatting
     * @param extraDetails The extra details to include in the formatting (e.g., "100" for HEALTH)
     *
     * @return The formatted string
     */
    private String formatStat(Stat stat, ParseType parseType, String extraDetails) {
        boolean hasExtraDetails = extraDetails != null && !extraDetails.isEmpty();
        String format = hasExtraDetails ? parseType.getFormatWithDetails() : parseType.getFormatWithoutDetails();

        if (format == null) {
            log.warn("Format string is null for parse type: " + parseType.getName());
            return "[INVALID FORMAT]";
        }

        log.debug("Using format: '" + format + "' for stat: '" + stat.getName() + "' with extra details: '" + extraDetails + "'");

        Map<String, String> placeholders = new HashMap<>(BASE_PLACEHOLDERS);
        placeholders.put("color", String.valueOf(stat.getColor().getCode()));
        placeholders.put("subColor", String.valueOf(stat.getSecondaryColor().getCode()));
        placeholders.put("icon", stat.getIcon() != null ? stat.getIcon() : "");
        placeholders.put("stat", stat.getStat() != null ? stat.getStat() : "");
        placeholders.put("display", stat.getDisplay() != null ? stat.getDisplay() : "");
        placeholders.put("extraDetails", hasExtraDetails ? extraDetails : "");

        // Handle specific parsing logic for ITEM_STAT and ABILITY
        if (parseType.getName().equalsIgnoreCase("ITEM_STAT")) {
            if (!hasExtraDetails) {
                log.warn("Missing extra details for ITEM_STAT: " + stat.getName());
                return "[ITEM_STAT_MISSING_DETAILS]";
            }

            int separator = extraDetails.indexOf(":");
            if (separator == -1) {
                log.warn("Missing separator ':' in extra details for ITEM_STAT: " + extraDetails);
                return "[ITEM_STAT_MISSING_SEPARATOR]";
            }

            placeholders.put("itemStat", extraDetails.substring(0, separator));
            placeholders.put("amount", extraDetails.substring(separator + 1));
        } else if (parseType.getName().equalsIgnoreCase("ABILITY")) {
            if (!hasExtraDetails) {
                log.warn("Missing extra details for ABILITY: " + stat.getName());
                return "[ABILITY_MISSING_DETAILS]";
            }

            int separator = extraDetails.indexOf(":");
            if (separator == -1) {
                log.warn("Missing separator ':' in extra details for ABILITY: " + extraDetails);
                return "[ABILITY_MISSING_SEPARATOR]";
            }

            placeholders.put("abilityName", extraDetails.substring(0, separator));
            placeholders.put("abilityType", extraDetails.substring(separator + 1));
        }

        String result = format;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replaceAll("\\{" + Pattern.quote(entry.getKey()) + "\\}", Matcher.quoteReplacement(entry.getValue()));
            log.debug("Replacing " + entry.getKey() + " with " + entry.getValue());
        }

        log.debug("Final formatted result: " + result);
        return result;
    }
}