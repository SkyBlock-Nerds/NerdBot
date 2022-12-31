package net.hypixel.nerdbot.util;

import java.awt.*;

public enum MCColor {
    DARK_RED('4', new Color(170, 0, 0)),
    RED('c', new Color(255, 85, 85)),
    GOLD('6', new Color(255, 170, 0)),
    YELLOW('e', new Color(255, 255, 85)),
    DARK_GREEN('2', new Color(0, 170, 0)),
    GREEN('a', new Color(85, 255, 85)),
    AQUA('b', new Color(85, 255, 255)),
    DARK_AQUA('9', new Color(0, 170, 170)),
    DARK_BLUE('1', new Color(0, 0, 170)),
    BLUE('3', new Color(85, 85, 255)),
    LIGHT_PURPLE('d', new Color(255, 85, 255)),
    DARK_PURPLE('5', new Color(170, 0, 170)),
    WHITE('f', new Color(255, 255, 255)),
    GRAY('7', new Color(170, 170, 170)),
    DARK_GRAY('8', new Color(85, 85, 85)),
    BLACK('0', new Color(0, 0, 0));

    private final char colorCode;
    private final Color color;


    MCColor(char colorCode, Color color) {
        this.colorCode = colorCode;
        this.color = color;
    }

    public char getColorCode() { return colorCode; }
    public Color getColor() {
        return color;
    }

}
