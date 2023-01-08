package net.hypixel.nerdbot.util.skyblock;

public enum Stat {
    STRENGTH("❁ Strength", MCColor.RED),
    DAMAGE("❁ Damage", MCColor.RED),
    HEALTH("❤ Health", MCColor.RED, MCColor.GREEN),
    DEFENSE("❈ Defense", MCColor.GREEN),
    TRUE_DEFENSE("❂ True Defense", MCColor.WHITE),
    SPEED("✦ Speed", MCColor.WHITE, MCColor.GREEN),
    INTELLIGENCE("✎ Intelligence", MCColor.AQUA),
    CRIT_CHANCE("☣ Critical Chance", MCColor.BLUE),
    CRIT_DAMAGE("☠ Critical Damage", MCColor.BLUE),
    ATTACK_SPEED("⚔ Bonus Attack Speed", MCColor.YELLOW, MCColor.GREEN),
    FEROCITY("⫽ Ferocity", MCColor.RED),
    MAGIC_FIND("✯ Magic Find", MCColor.AQUA),
    PET_LUCK("♣ Pet Luck", MCColor.LIGHT_PURPLE, MCColor.WHITE),
    SEA_CREATURE_CHANCE("α Sea Creature Chance", MCColor.DARK_AQUA),
    ABILITY_DAMAGE("๑ Ability Damage", MCColor.RED),
    MINING_SPEED("⸕ Mining Speed", MCColor.GOLD),
    PRISTINE("✧ Pristine", MCColor.DARK_PURPLE),
    MINING_FORTUNE("☘ Mining Fortune", MCColor.GOLD),
    FARMING_FORTUNE("☘ Farming Fortune", MCColor.GOLD),
    FORAGING_FORTUNE("☘ Foraging Fortune", MCColor.GOLD),
    SOULFLOW("⸎ Soulflow", MCColor.DARK_AQUA),
    RECIPE("Right-click to view recipes!", MCColor.YELLOW),
    REQUIRE("❣ Requires", MCColor.RED);

    public static final Stat[] VALUES = values();

    private final String stat;
    private final MCColor color;
    // Some stats have special colors which are used in conjunction to the normal color.
    private final MCColor subColor;

    Stat(String stat, MCColor color) {
        this.stat = stat;
        this.color = color;
        this.subColor = null;
    }

    Stat(String stat, MCColor color, MCColor subColor) {
        this.stat = stat;
        this.color = color;
        this.subColor = subColor;
    }

    public String getId() {
        return stat;
    }

    public MCColor getColor() {
        return color;
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
