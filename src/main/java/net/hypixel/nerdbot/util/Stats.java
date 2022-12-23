package net.hypixel.nerdbot.util;

public enum Stats {
    STRENGTH("❁ Strength", MCColor.RED),
    DAMAGE("❁ Damage", MCColor.RED),
    HEALTH("❤ Health", MCColor.RED),
    DEFENSE("❈ Defense", MCColor.GREEN),
    TRUE_DEFENSE("❂ True Defense", MCColor.WHITE),
    SPEED("✦ Speed", MCColor.WHITE),
    INTELLIGENCE("✎ Intelligence", MCColor.AQUA),
    CRIT_CHANCE("☣ Critical Chance", MCColor.BLUE),
    CRIT_DAMAGE("☠ Critical Damage", MCColor.BLUE),
    ATTACK_SPEED("⚔ Bonus Attack Speed", MCColor.YELLOW),
    FEROCITY("⫽ Ferocity", MCColor.RED),
    MAGIC_FIND("✯ Magic Find", MCColor.AQUA),
    PET_LUCK("♣ Pet Luck", MCColor.LIGHT_PURPLE),
    SEA_CREATURE_CHANCE("α Sea Creature Chance", MCColor.DARK_AQUA),
    ABILITY_DAMAGE("๑ Ability Damage", MCColor.RED),
    MINING_SPEED("⸕ Mining Speed", MCColor.GOLD),
    PRISTINE("✧ Pristine", MCColor.DARK_PURPLE),
    MINING_FORTUNE("☘ Mining Fortune", MCColor.GOLD),
    FARMING_FORTUNE("☘ Farming Fortune", MCColor.GOLD),
    FORAGING_FORTUNE("☘ Foraging Fortune", MCColor.GOLD),
    SOULFLOW("⸎ Soulflow", MCColor.DARK_AQUA);

    private final String stat;
    private final MCColor color;

    Stats(String stat, MCColor color) {
        this.stat = stat;
        this.color = color;
    }

    public String getId() {return stat;}
    public MCColor getColor() {return color;}
}
