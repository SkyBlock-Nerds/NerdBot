package net.hypixel.nerdbot.util;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;

public class FontUtil {

    /**
     * Checks if a font can render a character.
     * @param font The font to check.
     * @param character The character to check.
     * @return True if the font can render the character, false otherwise.
     */
    public static boolean canRenderCharacter(Font font, char character) {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = font.createGlyphVector(frc, Character.toString(character));
        Shape shape = gv.getOutline();

        System.out.println("Font " + font.getName() + " can render " + character + ": " + !shape.getBounds().isEmpty());
        return !shape.getBounds().isEmpty();
    }
}
