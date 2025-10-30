package net.hypixel.nerdbot.util.skyblock;


import lombok.Getter;

import java.util.function.BiFunction;

@Getter
public enum Flavor {
    REQUIRE("❣ Requires", MCColor.RED, Flavor::buildPostFlavor),
    SLAYER_REQUIRE("☠ Requires", MCColor.RED, Flavor::buildPostFlavor),
    RECIPE("Right-click to view recipes!", MCColor.YELLOW),
    COOP_SOULBOUND("Co-op Soulbound", MCColor.DARK_GRAY, Flavor::buildSoulboundFlavor),
    SOULBOUND("Soulbound", MCColor.DARK_GRAY, Flavor::buildSoulboundFlavor),
    REFORGABLE("This item can be reforged!", MCColor.DARK_GRAY),
    ABILITY("Ability", MCColor.GOLD, MCColor.YELLOW, Flavor::buildAbilityFlavor),
    MANA_COST("Mana Cost:", MCColor.DARK_GRAY, MCColor.DARK_AQUA, Flavor::buildPostDualColor),
    COOLDOWN("Cooldown:", MCColor.DARK_GRAY, MCColor.GREEN, Flavor::buildPostDualColor),
    HEALTH_COST("Health Cost:", MCColor.DARK_GRAY, MCColor.RED, Flavor::buildPostDualColor),
    UNDEAD_ITEM("This armor piece is undead ༕!", MCColor.DARK_GREEN),
    ARACHNAL("This item is Arachnal Ж!", MCColor.DARK_RED),
    RIFT_TRANSFERABLE("Rift-Transferable", MCColor.DARK_PURPLE),
    HOOK("ථ Hook", MCColor.BLUE),
    LINE("ꨃ Line", MCColor.BLUE),
    SINKER("࿉ Sinker", MCColor.BLUE),
    AIRBORNE("✈ Airborne", MCColor.GRAY),
    ANIMAL("☮ Animal", MCColor.GREEN),
    AQUATIC("⚓ Aquatic", MCColor.BLUE),
    ARCANE("♃ Arcane", MCColor.DARK_PURPLE),
    ARTHROPOD("Ж Arthropod", MCColor.DARK_RED),
    CONSTRUCT("⚙ Construct", MCColor.GRAY),
    CUBIC("⚂ Cubic", MCColor.GREEN),
    ELUSIVE("♣ Elusive", MCColor.LIGHT_PURPLE),
    ENDER("⊙ Ender", MCColor.DARK_PURPLE),
    FROZEN("☃ Frozen", MCColor.WHITE),
    GLACIAL("❄ Glacial", MCColor.AQUA),
    HUMANOID("✰ Humanoid", MCColor.YELLOW),
    INFERNAL("♨ Infernal", MCColor.DARK_RED),
    MAGMATIC("♆ Magmatic", MCColor.RED),
    MYTHOLOGICAL("✿ Mythological", MCColor.DARK_GREEN),
    PEST("ൠ Pest", MCColor.DARK_GREEN),
    SHIELDED("⛨ Shielded", MCColor.YELLOW),
    SKELETAL("🦴 Skeletal", MCColor.WHITE),
    SPOOKY("☽ Spooky", MCColor.GOLD),
    SUBTERRANEAN("⛏ Subterranean", MCColor.GOLD),
    UNDEAD("༕ Undead", MCColor.DARK_GREEN),
    WITHER("☠ Wither", MCColor.DARK_GRAY),
    WOODLAND("⸙ Woodland", MCColor.DARK_GREEN);

    public static Flavor[] VALUES = values();

    private final String text;
    private final MCColor color;
    private final MCColor secondaryColor;
    private final BiFunction<Flavor, String, String> flavorColorParser;

    Flavor(String text, MCColor color, MCColor secondaryColor, BiFunction<Flavor, String, String> flavorColorParser) {
        this.text = text;
        this.color = color;
        this.secondaryColor = secondaryColor;
        this.flavorColorParser = flavorColorParser;
    }

    Flavor(String text, MCColor color, BiFunction<Flavor, String, String> flavorColorParser) {
        this(text, color, null, flavorColorParser);
    }

    Flavor(String text, MCColor color) {
        this(text, color, null, Flavor::buildDefaultFlavor);
    }

    public String getParsedFlavorText(String extraData) {
        return flavorColorParser.apply(this, extraData);
    }

    private static String buildDefaultFlavor(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%" + flavor.getText();
    }

    private static String buildPostFlavor(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%" + flavor.getText() + " " + extraDetails;
    }

    private static String buildPostDualColor(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%" + flavor.getText() + " %%" + flavor.getSecondaryColor() + "%%" + extraDetails;
    }

    private static String buildAbilityFlavor(Flavor flavor, String extraDetails) {
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

    private static String buildSoulboundFlavor(Flavor flavor, String extraDetails) {
        return "%%" + flavor.getColor() + "%%%%BOLD%%* %%" + flavor.getColor() + "%%" + flavor.getText() + " %%BOLD%%*";
    }
}