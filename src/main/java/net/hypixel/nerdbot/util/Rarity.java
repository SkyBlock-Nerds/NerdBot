package net.hypixel.nerdbot.util;

public enum Rarity {
    COMMON("COMMON"),
    UNCOMMON("UNCOMMON"),
    RARE("RARE"),
    EPIC("EPIC"),
    LEGENDARY("LEGENDARY"),
    MYTHIC("MYTHIC"),
    DIVINE("DIVINE"),
    SPECIAL("SPECIAL"),
    VERY_SPECIAL("VERY SPECIAL");

    private String rarity;

    Rarity(String rarity) {
        this.rarity = rarity;
    }


    public String getID() {
        return rarity;
    }
}
