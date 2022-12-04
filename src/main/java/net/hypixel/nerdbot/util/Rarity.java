package net.hypixel.nerdbot.util;

public enum Rarity {
    COMMON("ONE"),
    UNCOMMON("TWO"),
    RARE("THREE"),
    EPIC("FOUR"),
    LEGENDARY("FIVE"),
    MYTHIC("SIX"),
    DIVINE("SEVEN"),
    SPECIAL("EIGHT"),
    VERY_SPECIAL("NINE");

    private final String rarity;

    Rarity(final String rarity) {
        this.rarity = rarity;
    }

    @Override
    public String toString() {
        return rarity;
    }
}
