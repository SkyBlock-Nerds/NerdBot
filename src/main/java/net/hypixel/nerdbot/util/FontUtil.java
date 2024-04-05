package net.hypixel.nerdbot.util;

import java.awt.Font;

public class FontUtil {

    /**
     * Checks if a font can render a character.
     *
     * @param font      The font to check.
     * @param character The character to check.
     *
     * @return True if the font can render the character, false otherwise.
     */
    public static boolean canRenderCharacter(Font font, char character) {
        int index = font.canDisplayUpTo(String.valueOf(character));
        return index == -1;
    }
}
