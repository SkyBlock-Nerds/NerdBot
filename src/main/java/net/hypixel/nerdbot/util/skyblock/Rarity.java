package net.hypixel.nerdbot.util.skyblock;

public enum Rarity {
    COMMON("COMMON", MCColor.WHITE),
    UNCOMMON("UNCOMMON", MCColor.GREEN),
    RARE("RARE", MCColor.BLUE),
    EPIC("EPIC", MCColor.DARK_PURPLE),
    LEGENDARY("LEGENDARY", MCColor.GOLD),
    MYTHIC("MYTHIC", MCColor.LIGHT_PURPLE),
    DIVINE("DIVINE", MCColor.AQUA),
    SPECIAL("SPECIAL", MCColor.RED),
    VERY_SPECIAL("VERY SPECIAL", MCColor.RED),
    NONE("", MCColor.GRAY);

    public static final Rarity[] VALUES = values();

    private final String rarity;
    private final MCColor color;

    Rarity(String rarity, MCColor color) {
        this.rarity = rarity;
        this.color = color;
    }

    public String getId() {
        return rarity;
    }

    public MCColor getRarityColor() {
        return color;
    }
}
