package net.hypixel.nerdbot.util.skyblock;

import lombok.Getter;

@Getter
public enum Stat {
    STRENGTH("❁", "Strength", MCColor.RED),
    DAMAGE("❁", "Damage", MCColor.RED),
    HEALTH("❤", "Health", MCColor.RED, MCColor.GREEN, ParseType.NORMAL),
    DEFENSE("❈", "Defense", MCColor.GREEN),
    TRUE_DEFENSE("❂", "True Defense", MCColor.WHITE),
    SPEED("✦", "Speed", MCColor.WHITE, MCColor.GREEN, ParseType.NORMAL),
    INTELLIGENCE("✎", "Intelligence", MCColor.AQUA),
    CRIT_CHANCE("☣", "Crit Chance", MCColor.BLUE),
    CRIT_DAMAGE("☠", "Crit Damage", MCColor.BLUE),
    ATTACK_SPEED("⚔", "Bonus Attack Speed", MCColor.YELLOW, MCColor.GREEN, ParseType.NORMAL),
    FEROCITY("⫽", "Ferocity", MCColor.RED),
    MENDING("☄", "Mending", MCColor.GREEN),
    VITALITY("♨", "Vitality", MCColor.DARK_RED),
    HEALTH_REGEN("❣", "Health Regen", MCColor.RED),
    MAGIC_FIND("✯", "Magic Find", MCColor.AQUA),
    PET_LUCK("♣", "Pet Luck", MCColor.LIGHT_PURPLE, MCColor.WHITE, ParseType.NORMAL),
    SEA_CREATURE_CHANCE("α", "Sea Creature Chance", MCColor.DARK_AQUA, null, ParseType.BOLD_ICON),
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
    SWING_RANGE("Ⓢ", "Swing Range", MCColor.YELLOW),
    FUEL("♢", "Fuel", MCColor.DARK_GREEN),
    MITHRIL_POWDER("᠅", "Mithril Powder", MCColor.DARK_GREEN),
    GEMSTONE_POWDER("᠅", "Gemstone Powder", MCColor.LIGHT_PURPLE),
    REQUIRE("❣", "Requires", MCColor.RED, ParseType.POST),
    RECIPE("", "Right-click to view recipes!", MCColor.YELLOW, ParseType.NONE),
    COOP_SOULBOUND("", "Co-op Soulbound", MCColor.DARK_GRAY, ParseType.SOULBOUND),
    SOULBOUND("", "Soulbound", MCColor.DARK_GRAY, ParseType.SOULBOUND),
    REFORGABLE("", "This item can be reforged!", MCColor.DARK_GRAY, ParseType.NONE),
    ITEM_STAT_RED("", "ITEM_STAT_RED", MCColor.GRAY, MCColor.RED, ParseType.ITEM_STAT),
    ITEM_STAT_GREEN("", "ITEM_STAT_GREEN", MCColor.GRAY, MCColor.GREEN, ParseType.ITEM_STAT),
    ITEM_STAT_PURPLE("", "ITEM_STAT_PINK", MCColor.GRAY, MCColor.LIGHT_PURPLE, ParseType.ITEM_STAT),
    MANA_COST("", "Mana Cost:", MCColor.DARK_GRAY, MCColor.DARK_AQUA, ParseType.POST_DUAL),
    COOLDOWN("", "Cooldown:", MCColor.DARK_GRAY, MCColor.GREEN, ParseType.POST_DUAL),
    ABILITY("", "Ability", MCColor.GOLD, MCColor.YELLOW, ParseType.ABILITY),
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
    RIFT_DAMAGE("❁", "Rift Damage", MCColor.DARK_PURPLE),
    MANA_REGEN("⚡", "Mana Regen", MCColor.AQUA),
    RIFT_TRANSFERABLE("", "Rift-Transferable", MCColor.DARK_PURPLE, ParseType.NONE),
    UNDEAD("༕", "This armor piece is undead ༕!", MCColor.DARK_GREEN, ParseType.NONE);

    public static final Stat[] VALUES = values();

    private final String icon;
    private final String stat;
    private final String display;
    private final MCColor color;
    private final ParseType parseType;
    // Some stats have special colors which are used in conjunction to the normal color.
    private final MCColor subColor;

    Stat(String icon, String stat, MCColor color, MCColor subColor, ParseType parseType) {
        this.icon = icon;
        this.stat = stat;
        this.display = icon + " " + stat;
        this.color = color;
        this.subColor = subColor;
        this.parseType = parseType;
    }

    Stat(String icon, String stat, MCColor color) {
        this(icon, stat, color, null, ParseType.NORMAL);
    }

    Stat(String icon, String stat, MCColor color, ParseType parseType) {
        this(icon, stat, color, null, parseType);
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

    public enum ParseType {
        NONE,
        NORMAL,
        BOLD,
        BOLD_ICON,
        DUAL,
        SOULBOUND,
        POST,
        POST_DUAL,
        ITEM_STAT,
        ABILITY
    }
}
