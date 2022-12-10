package net.hypixel.nerdbot.util;

import java.awt.*;

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
    public Color getColor(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Color.WHITE;
            case UNCOMMON -> Color.GREEN;
            case RARE -> Color.BLUE;
            case EPIC -> Color.MAGENTA;
            case LEGENDARY -> Color.YELLOW;
            case MYTHIC -> Color.MAGENTA;
            case DIVINE -> Color.CYAN;
            case SPECIAL, VERY_SPECIAL -> Color.RED;
        };
    }
}
