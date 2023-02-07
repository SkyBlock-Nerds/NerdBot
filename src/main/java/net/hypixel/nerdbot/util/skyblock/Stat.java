package net.hypixel.nerdbot.util.skyblock;

import net.hypixel.nerdbot.generator.StatColorParser;

import java.util.function.BiFunction;

public enum Stat {
    STRENGTH("❁", "Strength", MCColor.RED),
    DAMAGE("❁", "Damage", MCColor.RED),
    HEALTH("❤", "Health", MCColor.RED, MCColor.GREEN, StatColorParser::normalStatColorParser),
    DEFENSE("❈", "Defense", MCColor.GREEN),
    TRUE_DEFENSE("❂", "True Defense", MCColor.WHITE),
    SPEED("✦", "Speed", MCColor.WHITE, MCColor.GREEN, StatColorParser::normalStatColorParser),
    INTELLIGENCE("✎", "Intelligence", MCColor.AQUA),
    CRIT_CHANCE("☣", "Critical Chance", MCColor.BLUE),
    CRIT_DAMAGE("☠", "Critical Damage", MCColor.BLUE),
    ATTACK_SPEED("⚔", "Bonus Attack Speed", MCColor.YELLOW, MCColor.GREEN, StatColorParser::normalStatColorParser),
    FEROCITY("⫽", "Ferocity", MCColor.RED),
    MAGIC_FIND("✯", "Magic Find", MCColor.AQUA),
    PET_LUCK("♣", "Pet Luck", MCColor.LIGHT_PURPLE, MCColor.WHITE, StatColorParser::normalStatColorParser),
    SEA_CREATURE_CHANCE("α", "Sea Creature Chance", MCColor.DARK_AQUA),
    ABILITY_DAMAGE("๑", "Ability Damage", MCColor.RED),
    MINING_SPEED("⸕", "Mining Speed", MCColor.GOLD),
    PRISTINE("✧", "Pristine", MCColor.DARK_PURPLE),
    MINING_FORTUNE("☘", "Mining Fortune", MCColor.GOLD),
    FARMING_FORTUNE("☘", "Farming Fortune", MCColor.GOLD),
    FORAGING_FORTUNE("☘", "Foraging Fortune", MCColor.GOLD),
    SOULFLOW("⸎", "Soulflow", MCColor.DARK_AQUA),
    RECIPE("", "Right-click to view recipes!", MCColor.YELLOW),
    REQUIRE("❣",  "Requires", MCColor.RED, StatColorParser::postStatColorParser),
    REFORGABLE("", "This item can be reforged!", MCColor.DARK_GRAY),
    ITEM_STAT_RED("", "ITEM_STAT_RED", MCColor.GRAY, MCColor.RED, StatColorParser::itemStatColorParser),
    ITEM_STAT_GREEN("", "ITEM_STAT_GREEN", MCColor.GRAY, MCColor.GREEN, StatColorParser::itemStatColorParser),
    ITEM_STAT_PURPLE("", "ITEM_STAT_PINK", MCColor.GRAY, MCColor.LIGHT_PURPLE, StatColorParser::itemStatColorParser);

    public static final Stat[] VALUES = values();

    private final String icon;
    private final String stat;
    private final MCColor color;
    private final BiFunction<Stat, String, String> statColorParser;
    // Some stats have special colors which are used in conjunction to the normal color.
    private final MCColor subColor;

    Stat(String icon, String stat, MCColor color, MCColor subColor, BiFunction<Stat, String, String> statColorParser) {
        this.icon = icon;
        this.stat = icon + " " + stat;
        this.color = color;
        this.subColor = subColor;
        this.statColorParser = statColorParser;
    }

    Stat(String icon, String stat, MCColor color) {
        this(icon, stat, color, null, StatColorParser::normalStatColorParser);
    }

    Stat(String icon, String stat, MCColor color, BiFunction<Stat, String, String> statColorParser) {
        this(icon, stat, color, null, statColorParser);
    }

    Stat(String icon, String stat, MCColor color, MCColor subColor) {
        this(icon, stat, color, subColor, StatColorParser::dualStatColorParser);
    }

    public String getIcon() { return icon; }

    public String getId() {
        return stat;
    }

    public MCColor getColor() {
        return color;
    }

    /**
     * Parses the string into its color and id components
     * @param isIcon specifying if the stat's icon is meant to be displayed
     * @param extraData extra arguments provided in the section
     * @return returns a color parsed replacement string
     */
    public String getParsedStat(boolean isIcon, String extraData) {
        if (isIcon) {
            return "%%" + getColor() + "%%" + getIcon();
        }

        return statColorParser.apply(this, extraData);
    }

    /**
     * In some cases, stats can have multiple colors.
     * One for the number and another for the stat
     *
     * @return Secondary {@link MCColor} of the stat
     */
    public MCColor getSecondaryColor() {
        if (subColor != null) {
            return subColor;
        } else {
            return color;
        }
    }
}
