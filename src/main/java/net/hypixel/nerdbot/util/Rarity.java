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
    VERY_SPECIAL("VERY_SPECIAL");

    private final String rarity;

    Rarity(final String rarity) {
        this.rarity = rarity;
    }

    @Override
    public String toString() {
        return rarity;
    }
}
