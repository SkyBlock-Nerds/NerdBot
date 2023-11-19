package net.hypixel.nerdbot.generator.parser;

import net.hypixel.nerdbot.util.skyblock.Stat;

public class StatColorParser {

    private StatColorParser() {
    }

    /**
     * Displays the selected stat with its extra details and id in its primary color
     *
     * @param stat         the selected stat
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    public static String normalStatColorParser(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + extraDetails + stat.getDisplay();
    }

    /**
     * Displays the selected stat with its icon bolded extra details and id in its primary color
     *
     * @param stat         the selected stat
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    public static String boldedIconColorParser(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + extraDetails + "%%BOLD%%" + stat.getIcon() + "%%" + stat.getColor() + "%% " + stat.getStat();
    }

    /**
     * Displays the selected stat with its icon bolded
     *
     * @param stat the selected stat
     *
     * @return the color parsed replacement string
     */
    public static String boldedIconParser(Stat stat) {
        return "%%" + stat.getColor() + "%%%%BOLD%%" + stat.getIcon();
    }

    /**
     * Displays the selected stat with numbers in the secondary color and remaining text in primary color
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    public static String dualStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.length() == 0) {
            return normalStatColorParser(stat, extraDetails);
        }

        return "%%" + stat.getSecondaryColor() + "%%" + extraDetails + "%%" + stat.getColor() + "%%" + stat.getDisplay();
    }

    /**
     * Displays the stat with no extra details added on
     *
     * @param stat the stat selected
     *
     * @return returns the color parsed replacement string
     */
    public static String noParsing(Stat stat, String e) {
        return "%%" + stat.getColor() + "%%" + stat.getStat();
    }


    /**
     * Displays the stat with bolded asterisk around it
     *
     * @param stat the stat selected
     *
     * @return the color parsed replacement string
     */
    public static String soulboundColorParsing(Stat stat, String e) {
        return "%%" + stat.getColor() + "%%%%BOLD%%* %%" + stat.getColor() + "%%" + stat.getStat() + " %%BOLD%%*";
    }

    /**
     * Displays the selected stat with extra data after the id.
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    public static String postStatColorParser(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + stat.getDisplay() + " " + extraDetails;
    }

    /**
     * Displays the selected stat with the text after it changed color
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    public static String postDualColorParser(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + stat.getStat() + " %%" + stat.getSecondaryColor() + "%%" + extraDetails;
    }

    /**
     * Displays the selected stat with an Item Stat and amount
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacements string
     */
    public static String itemStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.length() == 0) {
            return "ITEM_STAT_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");
        if (separator == -1) {
            return "ITEM_STAT_MISSING_SEPARATOR";
        }

        String itemStat = extraDetails.substring(0, separator);
        String amount = extraDetails.substring(separator + 1);

        return "%%GRAY%%" + itemStat + ": %%" + stat.getSecondaryColor() + "%%" + amount;
    }

    /**
     * Displays the selected stat with an Ability name and amount
     *
     * @param stat         the stat selected (ABILITY)
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacements string
     */
    public static String abilityColorParser(Stat stat, String extraDetails) {
        if (extraDetails.length() == 0) {
            return "ABILITY_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");
        if (separator == -1) {
            return "ABILITY_MISSING_SEPARATOR";
        }

        String abilityName = extraDetails.substring(0, separator);
        String abilityType = extraDetails.substring(separator + 1);

        return "%%" + stat.getColor() + "%%" + stat.getStat() + ": " + abilityName + " %%" + stat.getSecondaryColor() + "%%%%BOLD%%" + abilityType;
    }
}
