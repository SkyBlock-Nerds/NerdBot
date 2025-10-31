package net.hypixel.nerdbot.core.util.skyblock;

import lombok.Getter;

import java.util.function.BiFunction;
import java.util.function.Function;

public enum Stat {
    STRENGTH("❁", "Strength", MCColor.RED),
    DAMAGE("❁", "Damage", MCColor.RED),
    HEALTH("❤", "Health", MCColor.RED, MCColor.GREEN, Stat::buildNormalStat, null),
    ABSORPTION("❤", "Absorption", MCColor.GOLD, MCColor.GREEN, Stat::buildNormalStat, null),
    DEFENSE("❈", "Defense", MCColor.GREEN),
    TRUE_DEFENSE("❂", "True Defense", MCColor.WHITE),
    SPEED("✦", "Speed", MCColor.WHITE, MCColor.GREEN, Stat::buildNormalStat, null),
    INTELLIGENCE("✎", "Intelligence", MCColor.AQUA),
    CRIT_CHANCE("☣", "Crit Chance", MCColor.BLUE),
    CRIT_DAMAGE("☠", "Crit Damage", MCColor.BLUE),
    ATTACK_SPEED("⚔", "Bonus Attack Speed", MCColor.YELLOW, MCColor.GREEN, Stat::buildNormalStat, null),
    FEROCITY("⫽", "Ferocity", MCColor.RED),
    MENDING("☄", "Mending", MCColor.GREEN),
    VITALITY("♨", "Vitality", MCColor.DARK_RED),
    HEALTH_REGEN("❣", "Health Regen", MCColor.RED),
    MAGIC_FIND("✯", "Magic Find", MCColor.AQUA),
    PET_LUCK("♣", "Pet Luck", MCColor.LIGHT_PURPLE, MCColor.WHITE, Stat::buildNormalStat, null),
    SEA_CREATURE_CHANCE("α", "Sea Creature Chance", MCColor.DARK_AQUA, null, Stat::buildBoldedIconStat, Stat::buildBoldedIcon),
    FISHING_SPEED("☂", "Fishing Speed", MCColor.AQUA),
    DOUBLE_HOOK_CHANCE("⚓", "Double Hook Chance", MCColor.BLUE),
    TREASURE_CHANCE("⛃", "Treasure Chance", MCColor.GOLD),
    ABILITY_DAMAGE("๑", "Ability Damage", MCColor.RED),
    MINING_SPEED("⸕", "Mining Speed", MCColor.GOLD),
    GEMSTONE_SPREAD("▚", "Gemstone Spread", MCColor.YELLOW),
    MINING_SPREAD("▚", "Mining Spread", MCColor.YELLOW),
    BREAKING_POWER("Ⓟ", "Breaking Power", MCColor.DARK_GREEN),
    PRISTINE("✧", "Pristine", MCColor.DARK_PURPLE),
    BONUS_PEST_CHANCE("ൠ", "Bonus Pest Chance", MCColor.DARK_GREEN),
    TROPHY_FISH_CHANCE("♔", "Trophy Fish Chance", MCColor.GOLD),
    MINING_FORTUNE("☘", "Mining Fortune", MCColor.GOLD),
    FARMING_FORTUNE("☘", "Farming Fortune", MCColor.GOLD),
    WHEAT_FORTUNE("☘", "Wheat Fortune", MCColor.GOLD),
    CARROT_FORTUNE("☘", "Carrot Fortune", MCColor.GOLD),
    POTATO_FORTUNE("☘", "Potato Fortune", MCColor.GOLD),
    PUMPKIN_FORTUNE("☘", "Pumpkin Fortune", MCColor.GOLD),
    MELON_FORTUNE("☘", "Melon Slice Fortune", MCColor.GOLD),
    MELON_SLICE_FORTUNE(MELON_FORTUNE.getIcon(), MELON_FORTUNE.getStat(), MELON_FORTUNE.getColor()),
    MUSHROOM_FORTUNE("☘", "Mushroom Fortune", MCColor.GOLD),
    CACTUS_FORTUNE("☘", "Cactus Fortune", MCColor.GOLD),
    SUGAR_CANE_FORTUNE("☘", "Sugar Cane Fortune", MCColor.GOLD),
    NETHER_WART_FORTUNE("☘", "Nether Wart Fortune", MCColor.GOLD),
    COCOA_BEANS_FORTUNE("☘", "Cocoa Beans Fortune", MCColor.GOLD),
    FORAGING_FORTUNE("☘", "Foraging Fortune", MCColor.GOLD),
    ORE_FORTUNE("☘", "Ore Fortune", MCColor.GOLD),
    BLOCK_FORTUNE("☘", "Block Fortune", MCColor.GOLD),
    DWARVEN_METAL_FORTUNE("☘", "Dwarven Metal Fortune", MCColor.GOLD),
    GEMSTONE_FORTUNE("☘", "Gemstone Fortune", MCColor.GOLD),
    FIG_FORTUNE("☘", "Fig Fortune", MCColor.GOLD),
    MANGROVE_FORTUNE("☘", "Mangrove Fortune", MCColor.GOLD),
    HUNTER_FORTUNE("☘", "Hunter Fortune", MCColor.LIGHT_PURPLE),
    SYPHON_LUCK("☘", "Syphon Luck", MCColor.LIGHT_PURPLE),
    COMBAT_WISDOM("☯", "Combat Wisdom", MCColor.DARK_AQUA),
    MINING_WISDOM("☯", "Mining Wisdom", MCColor.DARK_AQUA),
    FARMING_WISDOM("☯", "Farming Wisdom", MCColor.DARK_AQUA),
    FORAGING_WISDOM("☯", "Foraging Wisdom", MCColor.DARK_AQUA),
    FISHING_WISDOM("☯", "Fishing Wisdom", MCColor.DARK_AQUA),
    ENCHANTING_WISDOM("☯", "Enchanting Wisdom", MCColor.DARK_AQUA),
    ALCHEMY_WISDOM("☯", "Alchemy Wisdom", MCColor.DARK_AQUA),
    CARPENTRY_WISDOM("☯", "Carpentry Wisdom", MCColor.DARK_AQUA),
    TAMING_WISDOM("☯", "Taming Wisdom", MCColor.DARK_AQUA),
    RUNECRAFTING_WISDOM("☯", "Runecrafting Wisdom", MCColor.DARK_AQUA),
    SOCIAL_WISDOM("☯", "Social Wisdom", MCColor.DARK_AQUA),
    HUNTING_WISDOM("☯", "Hunting Wisdom", MCColor.DARK_AQUA),
    RIFT_TIME("ф", "Rift Time", MCColor.GREEN),
    RIFT_DAMAGE("❁", "Rift Damage", MCColor.DARK_PURPLE),
    MANA_REGEN("⚡", "Mana Regen", MCColor.AQUA),
    FEAR("", "Fear", MCColor.DARK_PURPLE),
    SOULFLOW("⸎", "Soulflow", MCColor.DARK_AQUA),
    OVERFLOW_MANA("ʬ", "Overflow Mana", MCColor.DARK_AQUA),
    SWING_RANGE("Ⓢ", "Swing Range", MCColor.YELLOW),
    FUEL("♢", "Fuel", MCColor.DARK_GREEN),
    MITHRIL_POWDER("᠅", "Mithril Powder", MCColor.DARK_GREEN),
    GEMSTONE_POWDER("᠅", "Gemstone Powder", MCColor.LIGHT_PURPLE),
    GLACITE_POWDER("᠅", "Glacite Powder", MCColor.AQUA),
    COLD("❄", "Cold", MCColor.AQUA),
    HEAT("♨", "Heat", MCColor.RED),
    COLD_RESISTANCE(COLD.getIcon(), "Cold Resistance", MCColor.AQUA),
    HEAT_RESISTANCE(HEAT.getIcon(), "Heat Resistance", MCColor.RED),
    SWEEP("∮", "Sweep", MCColor.DARK_GREEN),
    RESPIRATION("⚶", "Respiration", MCColor.DARK_AQUA),
    PRESSURE_RESISTANCE("❍", "Pressure Resistance", MCColor.BLUE),
    PULL("ᛷ", "Pull", MCColor.AQUA),
    MAGE_REPUTATION("ቾ", "Mage Reputation", MCColor.DARK_PURPLE),
    BARBARIAN_REPUTATION("⚒", "Barbarian Reputation", MCColor.RED),
    ITEM_STAT_RED("", "ITEM_STAT_RED", MCColor.GRAY, MCColor.RED, Stat::buildItemStat, null),
    ITEM_STAT_GREEN("", "ITEM_STAT_GREEN", MCColor.GRAY, MCColor.GREEN, Stat::buildItemStat, null),
    ITEM_STAT_PURPLE("", "ITEM_STAT_PINK", MCColor.GRAY, MCColor.LIGHT_PURPLE, Stat::buildItemStat, null);

    public static final Stat[] VALUES = values();

    @Getter
    private final String icon;
    @Getter
    private final String stat;
    @Getter
    private final String display;
    @Getter
    private final MCColor color;
    private final BiFunction<Stat, String, String> statColorParser;
    private final Function<Stat, String> iconColorParser;
    private final MCColor subColor;

    Stat(String icon, String stat, MCColor color) {
        this(icon, stat, color, null, Stat::buildNormalStat, null);
    }

    Stat(String icon, String stat, MCColor color, MCColor subColor, BiFunction<Stat, String, String> statColorParser, Function<Stat, String> iconColorParser) {
        this.icon = icon;
        this.stat = stat;
        this.display = icon + " " + stat;
        this.color = color;
        this.subColor = subColor;
        this.statColorParser = statColorParser;
        this.iconColorParser = iconColorParser;
    }

    private static String buildNormalStat(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + extraDetails + stat.getDisplay();
    }

    private static String buildBoldedIconStat(Stat stat, String extraDetails) {
        return "%%" + stat.getColor() + "%%" + extraDetails + "%%BOLD%%" + stat.getIcon() + "%%" + stat.getColor() + "%% " + stat.getStat();
    }

    private static String buildBoldedIcon(Stat stat) {
        return "%%" + stat.getColor() + "%%%%BOLD%%" + stat.getIcon();
    }

    private static String buildItemStat(Stat stat, String extraDetails) {
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

    public String getParsedStat(boolean isIcon, String extraData) {
        if (isIcon) {
            if (iconColorParser == null) {
                return "%%" + getColor() + "%%" + getIcon();
            }
            return iconColorParser.apply(this);
        }

        return statColorParser.apply(this, extraData);
    }

    public MCColor getSecondaryColor() {
        return subColor != null ? subColor : color;
    }
}
