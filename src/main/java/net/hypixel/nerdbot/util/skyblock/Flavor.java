package net.hypixel.nerdbot.util.skyblock;


import lombok.Getter;
import net.hypixel.nerdbot.generator.parser.FlavorParser;

import java.util.function.BiFunction;

@Getter
public enum Flavor {
    REQUIRE("❣ Requires", MCColor.RED, FlavorParser::postFlavorColorParser),
    RECIPE("Right-click to view recipes!", MCColor.YELLOW),
    COOP_SOULBOUND("Co-op Soulbound", MCColor.DARK_GRAY, FlavorParser::soulboundColorParsing),
    SOULBOUND("Soulbound", MCColor.DARK_GRAY, FlavorParser::soulboundColorParsing),
    REFORGABLE("This item can be reforged!", MCColor.DARK_GRAY),
    ABILITY("Ability", MCColor.GOLD, MCColor.YELLOW, FlavorParser::abilityColorParser),
    MANA_COST("Mana Cost:", MCColor.DARK_GRAY, MCColor.DARK_AQUA, FlavorParser::postDualColorParser),
    COOLDOWN("Cooldown:", MCColor.DARK_GRAY, MCColor.GREEN, FlavorParser::postDualColorParser),
    HEALTH_COST("Health Cost:", MCColor.DARK_GRAY, MCColor.RED, FlavorParser::postDualColorParser),
    UNDEAD("This armor piece is undead ༕!", MCColor.DARK_GREEN),
    RIFT_TRANSFERABLE("Rift-Transferable", MCColor.DARK_PURPLE);

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
        this(text, color, null, FlavorParser::defaultFlavorParser);
    }

    /**
     * Parses the flavor text with the extra data provided
     *
     * @param extraData extra arguments provided in the section
     *
     * @return returns a color parsed replacement string
     */
    public String getParsedFlavorText(String extraData) {
        return flavorColorParser.apply(this, extraData);
    }
}
