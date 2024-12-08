package net.hypixel.nerdbot.generator.text;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextFormatSettings implements Cloneable {

    private ChatFormat color = ChatFormat.GRAY;
    private boolean italic, bold, underlined, obfuscated, strikethrough;

    @Override
    public TextFormatSettings clone() {
        try {
            TextFormatSettings clone = (TextFormatSettings) super.clone();
            clone.color = ChatFormat.of(color.getCode());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}