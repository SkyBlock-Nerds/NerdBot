package net.hypixel.nerdbot.generator.parser.old;

import net.hypixel.nerdbot.util.skyblock.Icon;

public class IconParser {

    private IconParser() {
    }

    /**
     * Returns the icon with no extra data
     *
     * @param icon      the selected icon
     * @param extraData the extra arguments provided
     *
     * @return the icon with no extra data
     */
    public static String defaultIconParser(Icon icon, String extraData) {
        return icon.getIcon();
    }

    /**
     * Returns the icon repeated the amount of times specified in the extra data
     *
     * @param icon      the selected icon
     * @param extraData the extra arguments provided
     *
     * @return the icon repeated the amount of times specified in the extra data
     */
    public static String repeatingIconParser(Icon icon, String extraData) {
        String text = defaultIconParser(icon, extraData);
        try {
            int amount = Integer.parseInt(extraData);
            if (amount < 1) {
                return text;
            }
            return text.repeat(amount);
        } catch (NumberFormatException e) {
            return text;
        }
    }
}
