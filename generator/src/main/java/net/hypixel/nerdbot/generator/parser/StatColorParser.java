package net.hypixel.nerdbot.generator.parser;

import net.hypixel.nerdbot.core.util.skyblock.Stat;

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
     * Displays the selected stat with an Item Stat and amount
     *
     * @param stat         the stat selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacements string
     */
    public static String itemStatColorParser(Stat stat, String extraDetails) {
        if (extraDetails.isEmpty()) {
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
