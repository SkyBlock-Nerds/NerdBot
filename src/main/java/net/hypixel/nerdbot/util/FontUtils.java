package net.hypixel.nerdbot.util;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.command.GeneratorCommands;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class FontUtils {

    /**
     * Initializes a font.
     *
     * @param path The path to the font in the resources' folder.
     *
     * @return The initialized font.
     */
    @Nullable
    public static Font initFont(String path, float size) {
        Font font;
        try (InputStream fontStream = GeneratorCommands.class.getResourceAsStream(path)) {
            if (fontStream == null) {
                log.error("Couldn't initialize font: {}", path);
                return null;
            }
            font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(size);
        } catch (IOException | FontFormatException exception) {
            log.error("Couldn't initialize font: {}", path, exception);
            return null;
        }
        return font;
    }

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
