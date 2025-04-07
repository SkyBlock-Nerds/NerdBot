package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.data.Stat;
import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.generator.text.ChatFormat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;

public class StatParser implements StringParser {

    private static final Map<Stat.ParseType, BiFunction<Stat, String, String>> PARSERS = new HashMap<>();

    static {
        PARSERS.put(Stat.ParseType.NORMAL, StatParser::normalStatColorParser);
        PARSERS.put(Stat.ParseType.BOLD, (stat, extra) -> boldedIconParser(stat));
        PARSERS.put(Stat.ParseType.BOLD_ICON, StatParser::boldedIconColorParser);
        PARSERS.put(Stat.ParseType.OUTSIDE_MAGIC, (stat, s) -> outsideMagicColorParser(stat));
        PARSERS.put(Stat.ParseType.DUAL, StatParser::dualStatColorParser);
        PARSERS.put(Stat.ParseType.NONE, (stat, extra) -> noParsing(stat));
        PARSERS.put(Stat.ParseType.SOULBOUND, StatParser::soulboundColorParsing);
        PARSERS.put(Stat.ParseType.POST, StatParser::postStatColorParser);
        PARSERS.put(Stat.ParseType.POST_DUAL, StatParser::postDualColorParser);
        PARSERS.put(Stat.ParseType.ITEM_STAT, StatParser::itemStatColorParser);
        PARSERS.put(Stat.ParseType.ABILITY, StatParser::abilityColorParser);
        PARSERS.put(Stat.ParseType.DIFFERENT_ICON_COLOR, StatParser::differentIconColorParser);
    }

    @Override
    public String parse(String input) {
        if (input.isBlank()) return input;

        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String icon = matcher.group(1);
            String extraData = matcher.group(2);
            Stat stat = Stat.byName(icon);

            if (stat != null) {
                matcher.appendReplacement(result, parseStatWithType(stat, extraData) + ChatFormat.SECTION_SYMBOL + ChatFormat.RESET.getCode());
            }
        }

        matcher.appendTail(result);

        return result.toString();
    }

    private String parseStatWithType(Stat stat, String extraData) {
        return PARSERS.getOrDefault(stat.getParseType(), (s, e) -> parseStat(s))
            .apply(stat, extraData);
    }

    private static String parseStat(Stat stat) {
        return stat.getStat();
    }

    private static String normalStatColorParser(Stat stat, String extraDetails) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + (extraDetails == null || extraDetails.isEmpty() ? stat.getDisplay() : extraDetails + stat.getDisplay());
    }

    private static String outsideMagicColorParser(Stat stat) {
        // Nothing else uses this format yet so we can just hardcode the obfuscated character as X
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + ChatFormat.OBFUSCATED + "X"
            + ChatFormat.RESET + " " + ChatFormat.AMPERSAND_SYMBOL + stat.getColor().getCode() + stat.getStat() + " " +
            ChatFormat.AMPERSAND_SYMBOL + stat.getColor().getCode() + ChatFormat.OBFUSCATED + "X";
    }

    private static String boldedIconColorParser(Stat stat, String extraDetails) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + extraDetails +
            ChatFormat.AMPERSAND_SYMBOL + stat.getColor().getCode() + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.BOLD.getCode() + stat.getIcon() +
            ChatFormat.AMPERSAND_SYMBOL + stat.getColor().getCode() + " " + stat.getStat();
    }

    private static String boldedIconParser(Stat stat) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.BOLD.getCode() + stat.getIcon();
    }

    private static String dualStatColorParser(Stat stat, String extraDetails) {
        return extraDetails.isEmpty()
            ? normalStatColorParser(stat, extraDetails)
            : ChatFormat.AMPERSAND_SYMBOL + stat.getSecondaryColor().getCode() + extraDetails + ChatFormat.AMPERSAND_SYMBOL + stat.getColor().getCode() + stat.getDisplay();
    }

    private static String noParsing(Stat stat) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + stat.getStat();
    }

    private static String soulboundColorParsing(Stat stat, String e) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.BOLD.getCode() + "* " + ChatFormat.AMPERSAND_SYMBOL +
            stat.getColor().getCode() + stat.getStat() + " " + ChatFormat.AMPERSAND_SYMBOL +
            stat.getColor().getCode() + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.BOLD.getCode() + "*";
    }

    private static String postStatColorParser(Stat stat, String extraDetails) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + stat.getDisplay() +
            (extraDetails != null ? " " + extraDetails : "");
    }

    private static String postDualColorParser(Stat stat, String extraDetails) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + stat.getStat() +
            " " + ChatFormat.AMPERSAND_SYMBOL + stat.getSecondaryColor().getCode() + extraDetails;
    }

    private static String itemStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return "ITEM_STAT_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");

        if (separator == -1) {
            return "ITEM_STAT_MISSING_SEPARATOR";
        }

        String itemStat = extraDetails.substring(0, separator);
        String amount = extraDetails.substring(separator + 1);

        return ChatFormat.AMPERSAND_SYMBOL + ChatFormat.GRAY.getCode() + itemStat + ": " + ChatFormat.AMPERSAND_SYMBOL + stat.getSecondaryColor().getCode() + amount;
    }

    private static String abilityColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return "ABILITY_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");

        if (separator == -1) {
            return "ABILITY_MISSING_SEPARATOR";
        }

        String abilityName = extraDetails.substring(0, separator);
        String abilityType = extraDetails.substring(separator + 1);

        return ChatFormat.AMPERSAND_SYMBOL + stat.getColor().getCode() + stat.getStat() + ": " + abilityName +
            " " + ChatFormat.AMPERSAND_SYMBOL + stat.getSecondaryColor().getCode() + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.BOLD.getCode() + abilityType;
    }

    private static String differentIconColorParser(Stat stat, String extraDetails) {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + stat.getColor().getCode() + stat.getIcon() +
            " " + ChatFormat.AMPERSAND_SYMBOL + stat.getSecondaryColor().getCode() + stat.getStat() +
            (extraDetails != null ? " " + extraDetails : "");
    }
}