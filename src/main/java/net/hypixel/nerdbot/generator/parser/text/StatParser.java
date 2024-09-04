package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.StringParser;
import net.hypixel.nerdbot.generator.placeholder.Stat;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.util.Util;

import java.util.regex.Matcher;

public class StatParser implements StringParser {

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
            Stat statEnum = (Stat) Util.findValue(Stat.VALUES, icon);

            if (statEnum == null) {
                continue;
            }

            switch (statEnum.getParseType()) {
                case NORMAL -> input = input.replace(match, normalStatColorParser(statEnum, extraData));
                case BOLD -> input = input.replace(match, boldedIconParser(statEnum));
                case BOLD_ICON -> input = input.replace(match, boldedIconColorParser(statEnum, extraData));
                case DUAL -> input = input.replace(match, dualStatColorParser(statEnum, extraData));
                case NONE -> input = input.replace(match, noParsing(statEnum, extraData));
                case SOULBOUND -> input = input.replace(match, soulboundColorParsing(statEnum, extraData));
                case POST -> input = input.replace(match, postStatColorParser(statEnum, extraData));
                case POST_DUAL -> input = input.replace(match, postDualColorParser(statEnum, extraData));
                case ITEM_STAT -> input = input.replace(match, itemStatColorParser(statEnum, extraData));
                case ABILITY -> input = input.replace(match, abilityColorParser(statEnum, extraData));
                case DIFFERENT_ICON_COLOR ->
                    input = input.replace(match, differentIconColorParser(statEnum, extraData));
                default -> input = input.replace(match, parseStat(statEnum));
            }
        }

        return input;
    }

    /**
     * Parses the stat with no extra details
     * @param stat the stat selected
     * @return the replacement string
     */
    private String parseStat(Stat stat) {
        return stat.getStat();
    }

    /**
     * Displays the selected stat with its extra details and id in its primary color
     *
     * @param stat         the selected stat
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    private String normalStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails == null || extraDetails.isEmpty()) {
            return "&" + stat.getColor().getCode() + stat.getDisplay();
        }

        return "&" + stat.getColor().getCode() + extraDetails + stat.getDisplay();
    }

    /**
     * Displays the selected stat with its icon bolded extra details and id in its primary color
     *
     * @param stat         the selected stat
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    private String boldedIconColorParser(Stat stat, String extraDetails) {
        return "&" + stat.getColor().getCode() + extraDetails
            + "&" + stat.getColor().getCode()
            + "&" + ChatFormat.BOLD.getCode() + stat.getIcon()
            + "&" + stat.getColor().getCode() + " " + stat.getStat();
    }

    /**
     * Displays the selected stat with its icon bolded
     *
     * @param stat the selected stat
     *
     * @return the color parsed replacement string
     */
    private String boldedIconParser(Stat stat) {
        return "&" + stat.getColor().getCode() + "&" + ChatFormat.BOLD.getCode() + stat.getIcon();
    }

    /**
     * Displays the selected stat with numbers in the secondary color and remaining text in primary color
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    private String dualStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return normalStatColorParser(stat, extraDetails);
        }

        return "&" + stat.getSecondaryColor().getCode() + extraDetails + "&" + stat.getColor().getCode() + stat.getDisplay();
    }

    /**
     * Displays the stat with no extra details added on
     *
     * @param stat the stat selected
     *
     * @return returns the color parsed replacement string
     */
    private String noParsing(Stat stat, String e) {
        return "&" + stat.getColor().getCode() + stat.getStat();
    }


    /**
     * Displays the stat with bolded asterisk around it
     *
     * @param stat the stat selected
     *
     * @return the color parsed replacement string
     */
    private String soulboundColorParsing(Stat stat, String e) {
        return "&" + stat.getColor().getCode() + "&" + ChatFormat.BOLD.getCode() + "* &" + stat.getColor().getCode() + stat.getStat() + " &" + stat.getColor().getCode() + "&" + ChatFormat.BOLD.getCode() + "*";
    }

    /**
     * Displays the selected stat with extra data after the id.
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    private String postStatColorParser(Stat stat, String extraDetails) {
        // TODO expand to other methods
        if (extraDetails == null) {
            return "&" + stat.getColor().getCode() + stat.getDisplay();
        }

        return "&" + stat.getColor().getCode() + stat.getDisplay() + " " + extraDetails;
    }

    /**
     * Displays the selected stat with the text after it changed color
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    private String postDualColorParser(Stat stat, String extraDetails) {
        return "&" + stat.getColor().getCode() + stat.getStat() + " &" + stat.getSecondaryColor().getCode() + extraDetails;
    }

    /**
     * Displays the selected stat with an Item Stat and amount
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacements string
     */
    private String itemStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return "ITEM_STAT_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");
        if (separator == -1) {
            return "ITEM_STAT_MISSING_SEPARATOR";
        }

        String itemStat = extraDetails.substring(0, separator);
        String amount = extraDetails.substring(separator + 1);

        return "&" + ChatFormat.GRAY.getCode() + itemStat + ": &" + stat.getSecondaryColor().getCode() + amount;
    }

    /**
     * Displays the selected stat with an Ability name and amount
     *
     * @param stat         the stat selected (ABILITY)
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacements string
     */
    private String abilityColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return "ABILITY_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");
        if (separator == -1) {
            return "ABILITY_MISSING_SEPARATOR";
        }

        String abilityName = extraDetails.substring(0, separator);
        String abilityType = extraDetails.substring(separator + 1);

        return "&" + stat.getColor().getCode() + stat.getStat() + ": " + abilityName + " &" + stat.getSecondaryColor().getCode() + "&" + ChatFormat.BOLD.getCode() + abilityType;
    }

    /**
     * Displays the selected stat with the icon in the primary color and the text in the secondary color
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    private String differentIconColorParser(Stat stat, String extraDetails) {
        if (extraDetails == null) {
            return "&" + stat.getColor().getCode() + stat.getIcon() + " &" + stat.getSecondaryColor().getCode() + stat.getStat();
        }

        return "&" + stat.getColor().getCode() + stat.getIcon() + " &" + stat.getSecondaryColor().getCode() + stat.getStat() + " " + extraDetails;
    }
}
