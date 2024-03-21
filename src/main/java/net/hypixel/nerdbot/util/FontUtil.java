package net.hypixel.nerdbot.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;

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
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = font.createGlyphVector(frc, Character.toString(character));

        System.out.println("Font: " + font.getFontName() + ", Glyphs: " + gv.getNumGlyphs() + ", Code: " + gv.getGlyphCode(0));
        return gv.getNumGlyphs() == 1 && gv.getGlyphCode(0) == 0x25A1; // Unicode for empty box character
    }
}
