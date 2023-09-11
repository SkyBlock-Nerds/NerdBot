package net.hypixel.nerdbot.util.skyblock;

public enum Icon {
    DOT("•", "Dot"),
    TICKER("Ⓞ", "Ticker"),
    ZOMBIE_CHARGE("ⓩ", "Available Charge"),
    STAR("✪", "Star"),
    STARRED("⚚", "Starred"),
    BINGO("Ⓑ", "Bingo");

    public static final Icon[] VALUES = values();

    private final String icon;
    private final String name;

    Icon(String icon, String name) {
        this.icon = icon;
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public String getName() {
        return name;
    }
}
