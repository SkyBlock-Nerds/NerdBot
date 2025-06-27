package net.hypixel.nerdbot.util.skyblock;

import lombok.Getter;
import net.hypixel.nerdbot.generator.parser.IconParser;

import java.util.function.BiFunction;

@Getter
public enum Icon {
    DOT("•", "Dot"),
    TICKER("Ⓞ", "Ticker", IconParser::repeatingIconParser),
    ZOMBIE_CHARGE("ⓩ", "Available Charge", IconParser::repeatingIconParser),
    STAR("✪", "Star", IconParser::repeatingIconParser),
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
        this(icon, name, IconParser::defaultIconParser);
    }

    Icon(String icon, String name, BiFunction<Icon, String, String> iconParser) {
        this.icon = icon;
        this.name = name;
        this.iconParser = iconParser;
    }

    /**
     * Parses the icon with the extra data provided
     *
     * @param extraData extra arguments provided in the section
     *
     * @return returns a color parsed replacement string
     */
    public String getParsedIcon(String extraData) {
        return iconParser.apply(this, extraData);
    }
}
