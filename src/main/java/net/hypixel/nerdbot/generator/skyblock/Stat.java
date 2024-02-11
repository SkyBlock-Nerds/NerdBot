package net.hypixel.nerdbot.generator.skyblock;

import lombok.Getter;
import net.hypixel.nerdbot.util.ChatFormat;

@Getter
public enum Stat {
    STRENGTH("❁", "Strength", ChatFormat.RED),
    DAMAGE("❁", "Damage", ChatFormat.RED),
    HEALTH("❤", "Health", ChatFormat.RED, ChatFormat.GREEN, ParseType.NORMAL),
    DEFENSE("❈", "Defense", ChatFormat.GREEN),
    TRUE_DEFENSE("❂", "True Defense", ChatFormat.WHITE),
    SPEED("✦", "Speed", ChatFormat.WHITE, ChatFormat.GREEN, ParseType.NORMAL),
    INTELLIGENCE("✎", "Intelligence", ChatFormat.AQUA),
    CRIT_CHANCE("☣", "Crit Chance", ChatFormat.BLUE),
    CRIT_DAMAGE("☠", "Crit Damage", ChatFormat.BLUE),
    ATTACK_SPEED("⚔", "Bonus Attack Speed", ChatFormat.YELLOW, ChatFormat.GREEN, ParseType.NORMAL),
    FEROCITY("⫽", "Ferocity", ChatFormat.RED),
    MENDING("☄", "Mending", ChatFormat.GREEN),
    VITALITY("♨", "Vitality", ChatFormat.DARK_RED),
    HEALTH_REGEN("❣", "Health Regen", ChatFormat.RED),
    MAGIC_FIND("✯", "Magic Find", ChatFormat.AQUA),
    PET_LUCK("♣", "Pet Luck", ChatFormat.LIGHT_PURPLE, ChatFormat.WHITE, ParseType.NORMAL),
    SEA_CREATURE_CHANCE("α", "Sea Creature Chance", ChatFormat.DARK_AQUA, null, ParseType.BOLD_ICON),
    FISHING_SPEED("☂", "Fishing Speed", ChatFormat.AQUA),
    ABILITY_DAMAGE("๑", "Ability Damage", ChatFormat.RED),
    MINING_SPEED("⸕", "Mining Speed", ChatFormat.GOLD),
    BREAKING_POWER("Ⓟ", "Breaking Power", ChatFormat.DARK_GREEN),
    PRISTINE("✧", "Pristine", ChatFormat.DARK_PURPLE),
    MINING_FORTUNE("☘", "Mining Fortune", ChatFormat.GOLD),
    FARMING_FORTUNE("☘", "Farming Fortune", ChatFormat.GOLD),
    FORAGING_FORTUNE("☘", "Foraging Fortune", ChatFormat.GOLD),
    SOULFLOW("⸎", "Soulflow", ChatFormat.DARK_AQUA),
    OVERFLOW_MANA("ʬ", "Overflow Mana", ChatFormat.DARK_AQUA),
    SWING_RANGE("Ⓢ", "Swing Range", ChatFormat.YELLOW),
    FUEL("♢", "Fuel", ChatFormat.DARK_GREEN),
    MITHRIL_POWDER("᠅", "Mithril Powder", ChatFormat.DARK_GREEN),
    GEMSTONE_POWDER("᠅", "Gemstone Powder", ChatFormat.LIGHT_PURPLE),
    REQUIRE("❣", "Requires", ChatFormat.RED, ParseType.POST),
    RECIPE("", "Right-click to view recipes!", ChatFormat.YELLOW, ParseType.NONE),
    COOP_SOULBOUND("", "Co-op Soulbound", ChatFormat.DARK_GRAY, ParseType.SOULBOUND),
    SOULBOUND("", "Soulbound", ChatFormat.DARK_GRAY, ParseType.SOULBOUND),
    REFORGABLE("", "This item can be reforged!", ChatFormat.DARK_GRAY, ParseType.NONE),
    ITEM_STAT_RED("", "ITEM_STAT_RED", ChatFormat.GRAY, ChatFormat.RED, ParseType.ITEM_STAT),
    ITEM_STAT_GREEN("", "ITEM_STAT_GREEN", ChatFormat.GRAY, ChatFormat.GREEN, ParseType.ITEM_STAT),
    ITEM_STAT_PURPLE("", "ITEM_STAT_PINK", ChatFormat.GRAY, ChatFormat.LIGHT_PURPLE, ParseType.ITEM_STAT),
    MANA_COST("", "Mana Cost:", ChatFormat.DARK_GRAY, ChatFormat.DARK_AQUA, ParseType.POST_DUAL),
    COOLDOWN("", "Cooldown:", ChatFormat.DARK_GRAY, ChatFormat.GREEN, ParseType.POST_DUAL),
    ABILITY("", "Ability", ChatFormat.GOLD, ChatFormat.YELLOW, ParseType.ABILITY),
    COMBAT_WISDOM("☯", "Combat Wisdom", ChatFormat.DARK_AQUA),
    MINING_WISDOM("☯", "Mining Wisdom", ChatFormat.DARK_AQUA),
    FARMING_WISDOM("☯", "Farming Wisdom", ChatFormat.DARK_AQUA),
    FORAGING_WISDOM("☯", "Foraging Wisdom", ChatFormat.DARK_AQUA),
    FISHING_WISDOM("☯", "Fishing Wisdom", ChatFormat.DARK_AQUA),
    ENCHANTING_WISDOM("☯", "Enchanting Wisdom", ChatFormat.DARK_AQUA),
    ALCHEMY_WISDOM("☯", "Alchemy Wisdom", ChatFormat.DARK_AQUA),
    CARPENTRY_WISDOM("☯", "Carpentry Wisdom", ChatFormat.DARK_AQUA),
    TAMING_WISDOM("☯", "Taming Wisdom", ChatFormat.DARK_AQUA),
    RUNECRAFTING_WISDOM("☯", "Runecrafting Wisdom", ChatFormat.DARK_AQUA),
    SOCIAL_WISDOM("☯", "Social Wisdom", ChatFormat.DARK_AQUA),
    RIFT_TIME("ф", "Rift Time", ChatFormat.GREEN),
    RIFT_DAMAGE("❁", "Rift Damage", ChatFormat.DARK_PURPLE),
    MANA_REGEN("⚡", "Mana Regen", ChatFormat.AQUA),
    RIFT_TRANSFERABLE("", "Rift-Transferable", ChatFormat.DARK_PURPLE, ParseType.NONE),
    UNDEAD("༕", "This armor piece is undead ༕!", ChatFormat.DARK_GREEN, ParseType.NONE);

    public static final Stat[] VALUES = values();

    private final String icon;
    private final String stat;
    private final String display;
    private final ChatFormat color;
    private final ParseType parseType;
    // Some stats have special colors which are used in conjunction to the normal color.
    private final ChatFormat subColor;

    Stat(String icon, String stat, ChatFormat color, ChatFormat subColor, ParseType parseType) {
        this.icon = icon;
        this.stat = stat;
        this.display = icon + " " + stat;
        this.color = color;
        this.subColor = subColor;
        this.parseType = parseType;
    }

    Stat(String icon, String stat, ChatFormat color) {
        this(icon, stat, color, null, ParseType.NORMAL);
    }

    Stat(String icon, String stat, ChatFormat color, ParseType parseType) {
        this(icon, stat, color, null, parseType);
    }

    /**
     * In some cases, stats can have multiple colors.
     * One for the number and another for the stat
     *
     * @return Secondary {@link ChatFormat} of the stat
     */
    public ChatFormat getSecondaryColor() {
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
