package net.hypixel.nerdbot.generator.parser;

import net.hypixel.nerdbot.util.skyblock.Flavor;

public class FlavorParser {

    private FlavorParser() {
    }

    /**
     * Displays the selected flavor text without the extra data
     *
     * @param flavor       the flavor selected
     * @param extraDetails the extra arguments provided
     *
     * @return the flavor text without the extra data
     */
    public static String defaultFlavorParser(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%" + flavor.getText();
    }

    /**
     * Displays the selected flavor text with extra data after the id.
     *
     * @param flavor       the flavor selected
     * @param extraDetails the extra arguments provided
     *
     * @return returns the color parsed replacement string
     */
    public static String postFlavorColorParser(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%" + flavor.getText() + " " + extraDetails;
    }

    /**
     * Displays the selected flavor text after it changed color
     *
     * @param flavor       the flavor selected
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacement string
     */
    public static String postDualColorParser(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%" + flavor.getText() + " %%" + flavor.getSecondaryColor() + "%%" + extraDetails;
    }

    /**
     * Displays the selected flavor text with an Ability name and amount
     *
     * @param flavor       the flavor selected (ABILITY)
     * @param extraDetails the extra arguments provided
     *
     * @return the color parsed replacements string
     */
    public static String abilityColorParser(Flavor flavor, String extraDetails) {
        if (extraDetails.isEmpty()) {
            return "ABILITY_MISSING_DETAILS";
        }

        int separator = extraDetails.indexOf(":");
        if (separator == -1) {
            return "ABILITY_MISSING_SEPARATOR";
        }

        String abilityName = extraDetails.substring(0, separator);
        String abilityType = extraDetails.substring(separator + 1);

        return "%%" + flavor.getColor() + "%%" + flavor.getText() + ": " + abilityName + " %%" + flavor.getSecondaryColor() + "%%%%BOLD%%" + abilityType;
    }

    /**
     * Displays the flavor text with bolded asterisk around it
     *
     * @param flavor the flavor selected
     *
     * @return the color parsed replacement string
     */
    public static String soulboundColorParsing(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%%%BOLD%%* %%" + flavor.getColor() + "%%" + flavor.getText() + " %%BOLD%%*";
    }
}
