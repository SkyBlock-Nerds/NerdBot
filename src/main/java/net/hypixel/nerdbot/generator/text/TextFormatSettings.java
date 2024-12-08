package net.hypixel.nerdbot.generator.text;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextFormatSettings {

    private ChatFormat color = ChatFormat.GRAY;
    private boolean italic, bold, underlined, obfuscated, strikethrough;
}