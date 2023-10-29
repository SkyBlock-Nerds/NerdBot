package net.hypixel.nerdbot.generator.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class ColoredString {
    @Getter(AccessLevel.NONE)
    private final StringBuilder currentString;
    private MCColor currentColor;
    private boolean bold;
    private boolean italic;
    private boolean strikethrough;
    private boolean underlined;

    public ColoredString() {
        this.currentString = new StringBuilder(36);
        this.currentColor = MCColor.GRAY;
    }

    public ColoredString(@NotNull ColoredString previousColoredString) {
        this();
        this.setCurrentColor(previousColoredString.currentColor);
        this.setBold(previousColoredString.bold);
        this.setItalic(previousColoredString.italic);
    }

    public boolean isEmpty() {
        return this.currentString.toString().stripLeading().length() == 0;
    }

    public void addString(String newString) {
        this.currentString.append(newString);
    }

    public boolean hasSpecialFormatting() {
        return this.bold || this.italic || this.strikethrough || this.underlined;
    }

    @Override
    public String toString() {
        return this.currentString.toString();
    }
}