package net.hypixel.nerdbot.generator;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.skyblock.MCColor;

@Getter
@Setter
public class ColoredString {
    @Getter(AccessLevel.NONE) private final StringBuilder currentString;
    private MCColor currentColor;

    private boolean isBold;
    private boolean isItalic;

    public ColoredString() {
        this.currentString = new StringBuilder(36);
        this.currentColor = MCColor.GRAY;
    }

    public ColoredString(ColoredString previousColoredString) {
        this();
        this.setCurrentColor(previousColoredString.currentColor);
        this.setBold(previousColoredString.isBold);
        this.setItalic(previousColoredString.isItalic);
    }

    public boolean isEmpty() {
        return currentString.toString().stripLeading().length() == 0;
    }

    public void addString(String newString) {
        this.currentString.append(newString);
    }

    @Override
    public String toString() {
        return this.currentString.toString();
    }
}
