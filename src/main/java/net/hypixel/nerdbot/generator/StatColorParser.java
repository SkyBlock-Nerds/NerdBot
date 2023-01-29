package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.util.skyblock.Stat;

public class StatColorParser {

    /**
     * Displays the selected stat with its extra details and id in its primary color
     * @param stat the selected stat
     * @param extraDetails the extra arguments provided
     * @return returns the color parsed replacement string
     */
    public static String normalStatColorParser(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + extraDetails + stat.getId();
    }

    /**
     * Displays the selected stat with numbers in the secondary color and remaining text in primary color
     * @param stat the stat selected
     * @param extraDetails the extra arguments provided
     * @return returns the color parsed replacement string
     */
    public static String dualStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.length() == 0) {
            return normalStatColorParser(stat, extraDetails);
        }

        return "%%" + stat.getSecondaryColor() + "%%" + extraDetails + "%%" + stat.getColor() + "%%" + stat.getId();
    }

    /**
     * Displays the selected stat with extra data after the id.
     * @param stat the stat selected
     * @param extraDetails the extra arguments provided
     * @return returns the color parsed replacement string
     */
    public static String postStatColorParser(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + stat.getId() + " " + extraDetails;
    }

    /**
     * Displays the selected stat with an Item Stat and amount
     * @param stat the stat selected
     * @param extraDetails the extra arguments provided
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
}
