package net.hypixel.nerdbot.util.skyblock;

import java.awt.*;

public enum MCColor {
    BLACK('0', new Color(0, 0, 0), new Color(0, 0, 0)),
    DARK_BLUE('1', new Color(0, 0, 170), new Color(0, 0, 42)),
    DARK_GREEN('2', new Color(0, 170, 0), new Color(0, 42, 0)),
    BLUE('3', new Color(0, 170, 170), new Color(0, 42, 42)),
    DARK_RED('4', new Color(170, 0, 0), new Color(42, 0, 0)),
    DARK_PURPLE('5', new Color(170, 0, 170), new Color(42, 0, 42)),
    GOLD('6', new Color(255, 170, 0), new Color(42, 42, 0)),
    GRAY('7', new Color(170, 170, 170), new Color(42, 42, 42)),
    DARK_GRAY('8', new Color(85, 85, 85), new Color(21, 21, 21)),
    DARK_AQUA('9', new Color(85, 85, 255), new Color(21, 21, 63)),
    GREEN('a', new Color(85, 255, 85), new Color(21, 63, 21)),
    AQUA('b', new Color(85, 255, 255), new Color(21, 63, 63)),
    RED('c', new Color(255, 85, 85), new Color(63, 21, 21)),
    LIGHT_PURPLE('d', new Color(255, 85, 255), new Color(63, 21, 63)),
    YELLOW('e', new Color(255, 255, 85), new Color(63, 63, 21)),

    WHITE('f', new Color(255, 255, 255), new Color(63, 63, 63));

    public static final MCColor[] VALUES = values();

    private final char colorCode;
    private final Color color;
    private final Color backgroundColor;

    MCColor(char colorCode, Color color, Color backgroundColor) {
        this.colorCode = colorCode;
        this.color = color;
        this.backgroundColor = backgroundColor;
    }

    public char getColorCode() {
        return colorCode;
    }

    public Color getColor() {
        return color;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }
}
