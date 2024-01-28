package net.hypixel.nerdbot.generator.parser.text;

import net.hypixel.nerdbot.generator.parser.TextParser;
import net.hypixel.nerdbot.util.ChatFormat;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.Stat;

import java.util.regex.Matcher;

public class StatParser implements TextParser {

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
            Stat statEnum = (Stat) Util.findValueOrNull(Stat.VALUES, icon);

            if (statEnum == null) {
                continue;
            }
            
            switch (statEnum.getParseType()) {
                case NORMAL:
                    input = input.replace(match, normalStatColorParser(statEnum, extraData));
                    break;
                case BOLD_ICON:
                    input = input.replace(match, boldedIconColorParser(statEnum, extraData));
                    break;
                case DUAL:
                    input = input.replace(match, dualStatColorParser(statEnum, extraData));
                    break;
                case NONE:
                    input = input.replace(match, noParsing(statEnum, extraData));
                    break;
                case SOULBOUND:
                    input = input.replace(match, soulboundColorParsing(statEnum, extraData));
                    break;
                case POST:
                    input = input.replace(match, postStatColorParser(statEnum, extraData));
                    break;
                case POST_DUAL:
                    input = input.replace(match, postDualColorParser(statEnum, extraData));
                    break;
                case ITEM_STAT:
                    input = input.replace(match, itemStatColorParser(statEnum, extraData));
                    break;
                case ABILITY:
                    input = input.replace(match, abilityColorParser(statEnum, extraData));
                    break;
                default:
                    input = input.replace(match, parseStat(statEnum, extraData));
                    break;
            }
        }

        return input;
    }

    private String parseStat(Stat stat, String extra) {
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
    public String normalStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails == null || extraDetails.isEmpty()) {
            return "&" + stat.getColor().getColorCode() + stat.getDisplay();
        }

        return "&" + stat.getColor().getColorCode() + extraDetails + stat.getDisplay();
    }

    /**
     * Displays the selected stat with its icon bolded extra details and id in its primary color
     *
     * @param stat         the selected stat
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    public String boldedIconColorParser(Stat stat, String extraDetails) {
        return "&" + stat.getColor().getColorCode() + extraDetails + "&" + ChatFormat.BOLD.getCode() + stat.getIcon() + "&" + stat.getColor().getColorCode() + " " + stat.getStat();
    }

    /**
     * Displays the selected stat with its icon bolded
     *
     * @param stat the selected stat
     *
     * @return the color parsed replacement string
     */
    public String boldedIconParser(Stat stat) {
        return "&" + stat.getColor().getColorCode() + "&" + ChatFormat.BOLD.getCode() + stat.getIcon();
    }

    /**
     * Displays the selected stat with numbers in the secondary color and remaining text in primary color
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    public String dualStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return normalStatColorParser(stat, extraDetails);
        }

        return "&" + stat.getSecondaryColor().getColorCode() + extraDetails + "&" + stat.getColor().getColorCode() + stat.getDisplay();
    }

    /**
     * Displays the stat with no extra details added on
     *
     * @param stat the stat selected
     *
     * @return returns the color parsed replacement string
     */
    public String noParsing(Stat stat, String e) {
        return "&" + stat.getColor().getColorCode() + stat.getStat();
    }


    /**
     * Displays the stat with bolded asterisk around it
     *
     * @param stat the stat selected
     *
     * @return the color parsed replacement string
     */
    public String soulboundColorParsing(Stat stat, String e) {
        return "&" + stat.getColor().getColorCode() + "&" + ChatFormat.BOLD.getCode() + "* &" + stat.getColor().getColorCode() + stat.getStat() + " &" + stat.getColor().getColorCode() + "&" + ChatFormat.BOLD.getCode() + "*";
    }

    /**
     * Displays the selected stat with extra data after the id.
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    public String postStatColorParser(Stat stat, String extraDetails) {
        return "&" + stat.getColor().getColorCode() + stat.getDisplay() + " " + extraDetails;
    }

    /**
     * Displays the selected stat with the text after it changed color
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    public String postDualColorParser(Stat stat, String extraDetails) {
        return "&" + stat.getColor().getColorCode() + stat.getStat() + " &" + stat.getSecondaryColor().getColorCode() + extraDetails;
    }

    /**
     * Displays the selected stat with an Item Stat and amount
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacements string
     */
    public String itemStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return "ITEM_STAT_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");
        if (separator == -1) {
            return "ITEM_STAT_MISSING_SEPARATOR";
        }

        String itemStat = extraDetails.substring(0, separator);
        String amount = extraDetails.substring(separator + 1);
        
        return "&" + ChatFormat.GRAY.getCode() + itemStat + ": &" + stat.getSecondaryColor().getColorCode() + amount;
    }

    /**
     * Displays the selected stat with an Ability name and amount
     *
     * @param stat         the stat selected (ABILITY)
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacements string
     */
    public String abilityColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return "ABILITY_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");
        if (separator == -1) {
            return "ABILITY_MISSING_SEPARATOR";
        }

        String abilityName = extraDetails.substring(0, separator);
        String abilityType = extraDetails.substring(separator + 1);
        
        return "&" + stat.getColor().getColorCode() + stat.getStat() + ": " + abilityName + " &" + stat.getSecondaryColor().getColorCode() + "&" + ChatFormat.BOLD.getCode() + abilityType;
    }
}
