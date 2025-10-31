package net.hypixel.nerdbot.core.util.skyblock;

import lombok.Getter;

import java.util.function.BiFunction;

@Getter
public enum Icon {
    DOT("•", "Dot"),
    TICKER("Ⓞ", "Ticker", Icon::buildRepeatingIcon),
    ZOMBIE_CHARGE("ⓩ", "Available Charge", Icon::buildRepeatingIcon),
    STAR("✪", "Star", Icon::buildRepeatingIcon),
    STARRED("⚚", "Starred"),
    FRAGGED("⚚", "Starred"),
    BINGO("Ⓑ", "Bingo"),
    ZONE("⏣", "Zone"),
    ABIPHONE("✆", "Abiphone Call"),
    CHECKMARK("✔", "Checkmark"),
    CROSS("✖", "Cross"),
    RAFFLE("⛃", "Raffle"),
    SWEEP_BOOSTER("ꕮ", "Sweep Booster"),
    LUCK_BOOSTER("ꆤ", "Luck Booster"),
    FIGHTING_BOOSTER("४", "Fighting Booster"),
    FORAGING_WISDOM_BOOSTER("⸙", "Foraging Wisdom Booster"),
    FORAGING_FORTUNE_BOOSTER("⎋", "Foraging Fortune Booster");

    public static final Icon[] VALUES = values();

    private final String icon;
    private final String name;
    private final BiFunction<Icon, String, String> iconParser;

    Icon(String icon, String name) {
        this(icon, name, Icon::buildDefaultIcon);
    }

    Icon(String icon, String name, BiFunction<Icon, String, String> iconParser) {
        this.icon = icon;
        this.name = name;
        this.iconParser = iconParser;
    }

    public String getParsedIcon(String extraData) {
        return iconParser.apply(this, extraData);
    }

    private static String buildDefaultIcon(Icon icon, String extraData) {
        return icon.getIcon();
    }

    private static String buildRepeatingIcon(Icon icon, String extraData) {
        String text = buildDefaultIcon(icon, extraData);
        try {
            int amount = Integer.parseInt(extraData);
            if (amount < 1) {
                return text;
            }
            return text.repeat(amount);
        } catch (NumberFormatException exception) {
            return text;
        }
    }
}