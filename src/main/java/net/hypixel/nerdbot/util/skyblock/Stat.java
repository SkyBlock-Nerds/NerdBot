package net.hypixel.nerdbot.util.skyblock;

import net.hypixel.nerdbot.generator.StatColorParser;

import java.util.function.BiFunction;
import java.util.function.Function;

public enum Stat {
    STRENGTH("❁", "Strength", MCColor.RED),
    DAMAGE("❁", "Damage", MCColor.RED),
    HEALTH("❤", "Health", MCColor.RED, MCColor.GREEN, StatColorParser::normalStatColorParser, null),
    DEFENSE("❈", "Defense", MCColor.GREEN),
    TRUE_DEFENSE("❂", "True Defense", MCColor.WHITE),
    SPEED("✦", "Speed", MCColor.WHITE, MCColor.GREEN, StatColorParser::normalStatColorParser, null),
    INTELLIGENCE("✎", "Intelligence", MCColor.AQUA),
    CRIT_CHANCE("☣", "Crit Chance", MCColor.BLUE),
    CRIT_DAMAGE("☠", "Crit Damage", MCColor.BLUE),
    ATTACK_SPEED("⚔", "Bonus Attack Speed", MCColor.YELLOW, MCColor.GREEN, StatColorParser::normalStatColorParser, null),
    FEROCITY("⫽", "Ferocity", MCColor.RED),
    MENDING("☄", "Mending", MCColor.GREEN),
    VITALITY("♨", "Vitality", MCColor.DARK_RED),
    HEALTH_REGEN("❣", "Health Regen", MCColor.RED),
    MAGIC_FIND("✯", "Magic Find", MCColor.AQUA),
    PET_LUCK("♣", "Pet Luck", MCColor.LIGHT_PURPLE, MCColor.WHITE, StatColorParser::normalStatColorParser, null),
    SEA_CREATURE_CHANCE("α", "Sea Creature Chance", MCColor.DARK_AQUA, null, StatColorParser::boldedIconColorParser, StatColorParser::boldedIconParser),
    FISHING_SPEED("☂", "Fishing Speed", MCColor.AQUA),
    ABILITY_DAMAGE("๑", "Ability Damage", MCColor.RED),
    MINING_SPEED("⸕", "Mining Speed", MCColor.GOLD),
    BREAKING_POWER("Ⓟ", "Breaking Power", MCColor.DARK_GREEN),
    PRISTINE("✧", "Pristine", MCColor.DARK_PURPLE),
    MINING_FORTUNE("☘", "Mining Fortune", MCColor.GOLD),
    FARMING_FORTUNE("☘", "Farming Fortune", MCColor.GOLD),
    FORAGING_FORTUNE("☘", "Foraging Fortune", MCColor.GOLD),
    SOULFLOW("⸎", "Soulflow", MCColor.DARK_AQUA),
    OVERFLOW_MANA("ʬ", "Overflow Mana", MCColor.DARK_AQUA),
    REQUIRE("❣", "Requires", MCColor.RED, StatColorParser::postStatColorParser),
    RECIPE("", "Right-click to view recipes!", MCColor.YELLOW, StatColorParser::noParsing),
    COOP_SOULBOUND("", "Co-op Soulbound", MCColor.DARK_GRAY, StatColorParser::soulboundColorParsing),
    SOULBOUND("", "Soulbound", MCColor.DARK_GRAY, StatColorParser::soulboundColorParsing),
    REFORGABLE("", "This item can be reforged!", MCColor.DARK_GRAY, StatColorParser::noParsing),
    ITEM_STAT_RED("", "ITEM_STAT_RED", MCColor.GRAY, MCColor.RED, StatColorParser::itemStatColorParser, null),
    ITEM_STAT_GREEN("", "ITEM_STAT_GREEN", MCColor.GRAY, MCColor.GREEN, StatColorParser::itemStatColorParser, null),
    ITEM_STAT_PURPLE("", "ITEM_STAT_PINK", MCColor.GRAY, MCColor.LIGHT_PURPLE, StatColorParser::itemStatColorParser, null),
    COOLDOWN("", "Cooldown:", MCColor.DARK_GRAY, MCColor.GREEN, StatColorParser::postDualColorParser, null),
    ABILITY("", "Ability", MCColor.GOLD, MCColor.YELLOW, StatColorParser::abilityColorParser, null),
    COMBAT_WISDOM("☯", "Combat Wisdom", MCColor.DARK_AQUA),
    MINING_WISDOM("☯", "Mining Wisdom", MCColor.DARK_AQUA),
    FARMING_WISDOM("☯", "Farming Wisdom", MCColor.DARK_AQUA),
    FORAGING_WISDOM("☯", "Foraging Wisdom", MCColor.DARK_AQUA),
    FISHING_WISDOM("☯", "Fishing Wisdom", MCColor.DARK_AQUA),
    ENCHANTING_WISDOM("☯", "Enchanting Wisdom", MCColor.DARK_AQUA),
    ALCHEMY_WISDOM("☯", "Alchemy Wisdom", MCColor.DARK_AQUA),
    CARPENTRY_WISDOM("☯", "Carpentry Wisdom", MCColor.DARK_AQUA),
    RUNECRAFTING_WISDOM("☯", "Runecrafting Wisdom", MCColor.DARK_AQUA),
    SOCIAL_WISDOM("☯", "Social Wisdom", MCColor.DARK_AQUA),
    RIFT_TIME("ф", "Rift Time", MCColor.GREEN),
    RIFT_TRANSFERABLE("", "Rift-Transferable", MCColor.DARK_PURPLE, StatColorParser::riftTransferableColorParsing),
    UNDEAD("༕", "This armor piece is undead ༕!", MCColor.DARK_GREEN, StatColorParser::noParsing);

    public static final Stat[] VALUES = values();
    public static final String[] OTHER_ICONS = {"•", "✪", "᠅"};

    private final String icon;
    private final String stat;
    private final String display;
    private final MCColor color;
    private final BiFunction<Stat, String, String> statColorParser;
    private final Function<Stat, String> iconColorParser;
    // Some stats have special colors which are used in conjunction to the normal color.
    private final MCColor subColor;

    Stat(String icon, String stat, MCColor color, MCColor subColor, BiFunction<Stat, String, String> statColorParser, Function<Stat, String> iconColorParser) {
        this.icon = icon;
        this.stat = stat;
        this.display = icon + " " + stat;
        this.color = color;
        this.subColor = subColor;
        this.statColorParser = statColorParser;
        this.iconColorParser = iconColorParser;
    }

    Stat(String icon, String stat, MCColor color) {
        this(icon, stat, color, null, StatColorParser::normalStatColorParser, null);
    }

    Stat(String icon, String stat, MCColor color, BiFunction<Stat, String, String> statColorParser) {
        this(icon, stat, color, null, statColorParser, null);
    }

    public String getIcon() {
        return icon;
    }

    public String getStat() {
        return stat;
    }

    public String getDisplay() {
        return display;
    }

    public MCColor getColor() {
        return color;
    }

    /**
     * Parses the string into its color and id components
     *
     * @param isIcon    specifying if the stat's icon is meant to be displayed
     * @param extraData extra arguments provided in the section
     *
     * @return returns a color parsed replacement string
     */
    public String getParsedStat(boolean isIcon, String extraData) {
        if (isIcon) {
            if (iconColorParser == null) {
                return "%%" + getColor() + "%%" + getIcon();
            }

            return iconColorParser.apply(this);
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
