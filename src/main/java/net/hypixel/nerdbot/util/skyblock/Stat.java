package net.hypixel.nerdbot.util.skyblock;

public enum Stat {
    STRENGTH("❁ Strength", MCColor.RED, null),
    DAMAGE("❁ Damage", MCColor.RED, null),
    HEALTH("❤ Health", MCColor.RED, MCColor.GREEN),
    DEFENSE("❈ Defense", MCColor.GREEN, null),
    TRUE_DEFENSE("❂ True Defense", MCColor.WHITE, null),
    SPEED("✦ Speed", MCColor.WHITE, MCColor.GREEN),
    INTELLIGENCE("✎ Intelligence", MCColor.AQUA, null),
    CRIT_CHANCE("☣ Critical Chance", MCColor.BLUE, null),
    CRIT_DAMAGE("☠ Critical Damage", MCColor.BLUE, null),
    ATTACK_SPEED("⚔ Bonus Attack Speed", MCColor.YELLOW, MCColor.GREEN),
    FEROCITY("⫽ Ferocity", MCColor.RED, null),
    MAGIC_FIND("✯ Magic Find", MCColor.AQUA, null),
    PET_LUCK("♣ Pet Luck", MCColor.LIGHT_PURPLE, MCColor.WHITE),
    SEA_CREATURE_CHANCE("α Sea Creature Chance", MCColor.DARK_AQUA, null),
    ABILITY_DAMAGE("๑ Ability Damage", MCColor.RED, null),
    MINING_SPEED("⸕ Mining Speed", MCColor.GOLD, null),
    PRISTINE("✧ Pristine", MCColor.DARK_PURPLE, null),
    MINING_FORTUNE("☘ Mining Fortune", MCColor.GOLD, null),
    FARMING_FORTUNE("☘ Farming Fortune", MCColor.GOLD, null),
    FORAGING_FORTUNE("☘ Foraging Fortune", MCColor.GOLD, null),
    SOULFLOW("⸎ Soulflow", MCColor.DARK_AQUA, null),
    REQUIRE("❣ Requires", MCColor.RED, null);

    private final String stat;
    private final MCColor color;
    //Some stats have special colors which are used in conjunction to the normal color.
    private final MCColor subColor;

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
