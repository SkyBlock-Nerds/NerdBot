package net.hypixel.nerdbot.util;

import java.awt.*;

public enum Rarity {
    COMMON("COMMON", new Color(255, 255, 255)),
    UNCOMMON("UNCOMMON", new Color(85, 255, 85)),
    RARE("RARE", new Color(85, 85, 255)),
    EPIC("EPIC", new Color(170, 0, 170)),
    LEGENDARY("LEGENDARY", new Color(255, 170, 0)),
    MYTHIC("MYTHIC", new Color(255, 85, 255)),
    DIVINE("DIVINE", new Color(85, 255, 255)),
    SPECIAL("SPECIAL", new Color(170, 0, 0)),
    VERY_SPECIAL("VERY SPECIAL", new Color(170, 0, 0));

    private final String rarity;
    private final Color color;

    Rarity(String rarity, Color color) {
        this.rarity = rarity;
        this.color = color;
    }

    public String getId() {
        return rarity;
    }

    public Color getColor() {
        return color;
    }
}
