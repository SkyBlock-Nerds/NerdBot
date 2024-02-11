package net.hypixel.nerdbot.generator.skyblock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.hypixel.nerdbot.util.ChatFormat;

@AllArgsConstructor
@Getter
public enum Rarity {
    COMMON("COMMON", ChatFormat.WHITE),
    UNCOMMON("UNCOMMON", ChatFormat.GREEN),
    RARE("RARE", ChatFormat.BLUE),
    EPIC("EPIC", ChatFormat.DARK_PURPLE),
    LEGENDARY("LEGENDARY", ChatFormat.GOLD),
    MYTHIC("MYTHIC", ChatFormat.LIGHT_PURPLE),
    DIVINE("DIVINE", ChatFormat.AQUA),
    SPECIAL("SPECIAL", ChatFormat.RED),
    VERY_SPECIAL("VERY SPECIAL", ChatFormat.RED),
    ADMIN("ADMIN", ChatFormat.DARK_RED),
    SUPREME("SUPREME", ChatFormat.DARK_RED),
    ULTIMATE("ULTIMATE", ChatFormat.DARK_RED),
    NONE("", ChatFormat.GRAY);

    public static final Rarity[] VALUES = values();

    private final String display;
    private final ChatFormat color;

    public String getColorCode() {
        return "&" + color.getCode();
    }

    public String getFormattedDisplay() {
        return getColorCode() + "&" + ChatFormat.BOLD.getCode() + display;
    }
}
