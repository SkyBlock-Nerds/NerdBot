package net.hypixel.nerdbot.generator.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@UtilityClass
public class MinecraftFonts {

    public static final int REGULAR = 0;

    private static final float DEFAULT_SIZE = 20.0f;
    private static final Font UNIFONT;
    private static final Font UNIFONT_UPPER;
    private static final List<Font> MINECRAFT_FONTS = new ArrayList<>(4);

    static {
        log.info("Initializing Minecraft fonts...");

        UNIFONT = loadFont("/minecraft/assets/fonts/unifont-17.0.03.otf", DEFAULT_SIZE);
        if (UNIFONT != null) {
            registerFont(UNIFONT);
        }

        UNIFONT_UPPER = loadFont("/minecraft/assets/fonts/unifont_upper-17.0.03.otf", DEFAULT_SIZE);
        if (UNIFONT_UPPER != null) {
            registerFont(UNIFONT_UPPER);
        }

        Object[][] fontConfigs = {
            {"Regular", "/minecraft/assets/fonts/Minecraft-Regular.otf", 15.5f},
            {"Bold", "/minecraft/assets/fonts/3_Minecraft-Bold.otf", 20.0f},
            {"Italic", "/minecraft/assets/fonts/2_Minecraft-Italic.otf", 20.5f},
            {"BoldItalic", "/minecraft/assets/fonts/4_Minecraft-BoldItalic.otf", 20.5f}
        };

        for (Object[] config : fontConfigs) {
            String name = (String) config[0];
            String path = (String) config[1];
            float size = (float) config[2];
            Font font = loadFont(path, size);

            if (font == null) {
                throw new IllegalStateException("Required font failed to load: " + name);
            }

            registerFont(font);
            MINECRAFT_FONTS.add(font);
        }

        log.info("Font initialization complete");
    }


    /**
     * Load a font.
     *
     * @param path The path where the font file is stored.
     * @param size The font size to use.
     *
     * @return A {@link Font} instance.
     */
    private static Font loadFont(String path, float size) {
        try (InputStream stream = MinecraftFonts.class.getResourceAsStream(path)) {
            if (stream == null) {
                log.error("Font resource not found: {}", path);
                return null;
            }

            return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(size);
        } catch (IOException | FontFormatException e) {
            log.error("Failed to load font: {}", path, e);
            return null;
        }
    }

    /**
     * Register a {@link Font} with the Graphics Environment
     *
     * @param font The {@link Font} to register.
     */
    private static void registerFont(Font font) {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            log.info("Loaded font: " + font.getFontName());
        } catch (Exception e) {
            log.warn("Failed to register font '{}': {}", font.getName(), e.getMessage());
        }
    }

    /**
     * Returns the Minecraft font for the given style index.
     *
     * @param style 0 = regular, 1 = bold, 2 = italic, 3 = bold+italic
     *
     * @return The font for that style
     */
    @NotNull
    public static Font getFont(int style) {
        if (style < 0 || style >= MINECRAFT_FONTS.size()) {
            return MINECRAFT_FONTS.get(REGULAR);
        }

        return MINECRAFT_FONTS.get(style);
    }

    /**
     * Returns the Minecraft font for the given style flags.
     *
     * @param bold   Whether the font should be bold
     * @param italic Whether the font should be italic
     *
     * @return The font for that style combination
     */
    @NotNull
    public static Font getFont(boolean bold, boolean italic) {
        return MINECRAFT_FONTS.get((bold ? 1 : 0) + (italic ? 2 : 0));
    }

    /**
     * Returns all Minecraft fonts.
     *
     * @return Unmodifiable list of all loaded Minecraft fonts
     */
    @NotNull
    public static List<Font> getAllFonts() {
        return List.copyOf(MINECRAFT_FONTS);
    }

    /**
     * Returns a fallback font for characters the Minecraft font can't display.
     * <p>
     * Most characters (letters, numbers, symbols) use the standard Unifont.
     * Emoji and other special characters (above 0xFFFF) need Unifont Upper.
     *
     * @param codePoint The character's Unicode value (e.g. 'A' = 65, '🔥' = 128293)
     * @param size      The desired font size
     *
     * @return A Unifont that can display the character, or null if unavailable
     */
    public static Font getFallbackFont(int codePoint, float size) {
        // Emoji and other special characters have values above 0xFFFF and need the "upper" font
        if (codePoint > 0xFFFF) {
            return UNIFONT_UPPER != null ? UNIFONT_UPPER.deriveFont(size) : null;
        }

        return UNIFONT != null ? UNIFONT.deriveFont(size) : null;
    }

    /**
     * Checks if a font can render a specific character.
     *
     * @param font      The font to check
     * @param character The character to check
     *
     * @return True if the font can render the character
     */
    public static boolean canRender(Font font, char character) {
        return font.canDisplayUpTo(String.valueOf(character)) == -1;
    }
}
