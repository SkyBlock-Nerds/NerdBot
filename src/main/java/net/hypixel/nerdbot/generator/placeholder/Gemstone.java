package net.hypixel.nerdbot.generator.placeholder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.hypixel.nerdbot.generator.text.ChatFormat;

@AllArgsConstructor
@Getter
public enum Gemstone {
    GEM_RUBY("❤", ChatFormat.RED),
    GEM_AMETHYST("❈", ChatFormat.DARK_PURPLE),
    GEM_OPAL("❂", ChatFormat.WHITE),
    GEM_SAPPHIRE("✎", ChatFormat.AQUA),
    GEM_JASPER("❁", ChatFormat.LIGHT_PURPLE),
    GEM_JADE("☘", ChatFormat.GREEN),
    GEM_AMBER("⸕", ChatFormat.GOLD),
    GEM_TOPAZ("✧", ChatFormat.YELLOW),
    GEM_ONYX("☠", ChatFormat.DARK_GRAY),
    GEM_PERIDOT("☘", ChatFormat.DARK_GREEN),
    GEM_CITRINE("☘", ChatFormat.DARK_RED),
    GEM_AQUAMARINE("α", ChatFormat.BLUE),
    GEM_COMBAT("⚔", null),
    GEM_OFFENSIVE("☠", null),
    GEM_DEFENSIVE("☤", null),
    GEM_MINING("✦", null),
    GEM_UNIVERSAL("❂", null);

    public static final Gemstone[] VALUES = values();

    private final String icon;
    private final ChatFormat color;

    public String getFormattedIcon() {
        return color == null ? icon : color + icon;
    }
}
