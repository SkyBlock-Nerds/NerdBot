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
    UNOBTAINABLE("ADMIN", MCColor.DARK_RED),
    SUPREME("SUPREME", MCColor.DARK_RED),
    NONE("", MCColor.GRAY);

    public static final Rarity[] VALUES = values();

    private final String display;
    private final MCColor color;

    Rarity(String display, MCColor color) {
        this.display = display;
        this.color = color;
    }

    public String getId() {
        return display;
    }

    public MCColor getRarityColor() {
        return color;
    }
}

public enum Soulbound {
    COOP_SOULBOUND("* Co-op Soulbound *", MCColor.GRAY),
    SOULBOUND("* Soulbound *", MCColor.GRAY);'
        //idk what anything after this does in the rarity part so i'm just gonna ignore it
    }
