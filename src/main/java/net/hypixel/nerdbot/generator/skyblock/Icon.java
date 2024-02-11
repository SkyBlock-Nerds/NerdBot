package net.hypixel.nerdbot.generator.skyblock;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Icon {
    DOT("•", "Dot"),
    TICKER("Ⓞ", "Ticker"),
    ZOMBIE_CHARGE("ⓩ", "Available Charge"),
    STAR("✪", "Star"),
    STARRED("⚚", "Starred"),
    FRAGGED("⚚", "Starred"),
    BINGO("Ⓑ", "Bingo");

    public static final Icon[] VALUES = values();

    private final String icon;
    private final String name;
}
