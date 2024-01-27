package net.hypixel.nerdbot.util;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.postgresql.shaded.com.ongres.scram.common.util.Preconditions;

import java.awt.Color;
import java.util.Arrays;
import java.util.Objects;

public enum ChatFormat {
    BLACK('0', 0x000000, 0x000000),
    DARK_BLUE('1', 0x0000AA, 0x00002A),
    DARK_GREEN('2', 0x00AA00, 0x002A00),
    DARK_AQUA('3', 0x00AAAA, 0x002A2A),
    DARK_RED('4', 0xAA0000, 0x2A0000),
    DARK_PURPLE('5', 0xAA00AA, 0x2A002A),
    GOLD('6', 0xFFAA00, 0x2A2A00),
    GRAY('7', 0xAAAAAA, 0x2A2A2A),
    DARK_GRAY('8', 0x555555, 0x151515),
    BLUE('9', 0x5555FF, 0x15153F),
    GREEN('a', 0x55FF55, 0x153F15),
    AQUA('b', 0x55FFFF, 0x153F3F),
    RED('c', 0xFF5555, 0x3F1515),
    LIGHT_PURPLE('d', 0xFF55FF, 0x3F153F),
    YELLOW('e', 0xFFFF55, 0x3F3F15),
    WHITE('f', 0xFFFFFF, 0x3F3F3F),
    OBFUSCATED('k', true, 0xFFFFFF), // Unknown BRGB
    BOLD('l', true, 0xFFFF55),
    STRIKETHROUGH('m', true, 0xFFFFFF), // Unknown BRGB
    UNDERLINE('n', true, 0xFFFFFF), // Unknown BRGB
    ITALIC('o', true, 0x5555FF),
    RESET('r', false, 0x000000);

    public static final ChatFormat[] VALUES = values();
    public static final char SECTION_SYMBOL = '§';

    @Getter
    private final char code;
    @Getter private final boolean isFormat;
    private final @NotNull String toString;
    private final @Nullable Color color;
    @Getter private final @NotNull Color backgroundColor;

    ChatFormat(char code, int rgb, int brgb) {
        this(code, false, rgb, brgb);
    }

    ChatFormat(char code, boolean isFormat, int brgb) {
        this(code, isFormat, -1, brgb);
    }

    ChatFormat(char code, boolean isFormat, int rgb, int brgb) {
        this.code = code;
        this.isFormat = isFormat;
        this.toString = new String(new char[]{ SECTION_SYMBOL, code });
        this.color = (this.isColor() ? new Color(rgb) : null);
        this.backgroundColor = new Color(brgb);
    }

    /**
     * Get the color represented by the specified name.
     *
     * @param name The name to search for.
     * @return The mapped format, or null if none exists.
     */
    public static @Nullable ChatFormat of(@NotNull String name) {
        return Arrays.stream(values())
            .filter(format -> Objects.equals(format.name(), name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the color represented by the specified code.
     *
     * @param code The code to search for.
     * @return The mapped format, or null if none exists.
     */
    public static @Nullable ChatFormat of(char code) {
        return Arrays.stream(values())
            .filter(format -> Objects.equals(format.getCode(), code))
            .findFirst()
            .orElse(null);
    }

    public Color getColor() {
        Preconditions.checkArgument(this.isColor(), "Format has no color!");
        return this.color;
    }

    public Color getColor(float alpha) {
        return this.getColor((int) alpha);
    }

    public Color getColor(int alpha) {
        if (Objects.isNull(this.color))
            throw new UnsupportedOperationException("Formats are not colors!");

        return new Color(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), alpha);
    }

    public int getRGB() {
        return this.getColor().getRGB();
    }

    public boolean isColor() {
        return !this.isFormat() && this != RESET;
    }

    public static boolean isValid(char code) {
        return of(code) != null;
    }

    public ChatFormat getNextFormat() {
        return this.getNextFormat(ordinal());
    }

    private ChatFormat getNextFormat(int ordinal) {
        int nextColor = ordinal + 1;

        if (nextColor > values().length - 1)
            return values()[0];
        else if (!values()[nextColor].isColor())
            return getNextFormat(nextColor);

        return values()[nextColor];
    }

    /**
     * Strips the given message of all color and format codes
     *
     * @param value String to strip of color
     * @return A copy of the input string, without any coloring
     */
    public static String stripColor(@NotNull String value) {
        return Util.VANILLA_PATTERN.matcher(value).replaceAll("");
    }

    public String toLegacyString() {
        return this.toString;
    }

    public String toJsonString() {
        return this.name().toLowerCase();
    }

    @Override
    public String toString() {
        return this.toString;
    }
}
