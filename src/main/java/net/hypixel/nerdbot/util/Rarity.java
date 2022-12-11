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

    // Get the display name of the enum
    public String getID() {
        return rarity;
    }

    // Get the associated Minecraft color for the rarity enum
    public Color getColor() {
        return switch (this.rarity) {
            case "COMMON"           -> new Color(255, 255, 255);
            case "UNCOMMON"         -> new Color(85, 255, 85);
            case "RARE"             -> new Color(85, 85, 255);
            case "EPIC"             -> new Color(170, 0, 170);
            case "LEGENDARY"        -> new Color(255, 170, 0);
            case "MYTHIC"           -> new Color(255, 85, 255);
            case "DIVINE"           -> new Color(85, 255, 255);
            case "SPECIAL",
                    "VERY SPECIAL"  -> new Color(170, 0, 0);
            default -> null;
        };
    }
}
