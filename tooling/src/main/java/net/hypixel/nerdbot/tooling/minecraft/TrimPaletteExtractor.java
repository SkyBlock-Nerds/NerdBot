package net.hypixel.nerdbot.tooling.minecraft;

import net.hypixel.nerdbot.tooling.ToolingConstants;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts armor trim color palettes from Minecraft's 8x1 pixel PNG files.
 * Each trim material has an 8-pixel wide, 1-pixel tall PNG where each pixel
 * represents a color in the trim gradient.
 */
public class TrimPaletteExtractor {

    private static final Path DEFAULT_PALETTES_PATH = ToolingConstants.MINECRAFT_ASSETS.resolve("textures/trims/color_palettes");

    /**
     * Extracts all trim color palettes from Minecraft assets.
     *
     * @return map of palette name to array of RGB colors
     */
    public static Map<String, int[]> extractAllPalettes() {
        return extractAllPalettes(DEFAULT_PALETTES_PATH);
    }

    /**
     * Extracts all trim color palettes from the specified path.
     *
     * @param palettesPath path to the color_palettes directory
     *
     * @return map of palette name to array of RGB colors
     */
    public static Map<String, int[]> extractAllPalettes(Path palettesPath) {
        Map<String, int[]> palettes = new LinkedHashMap<>();

        if (!Files.exists(palettesPath)) {
            System.err.println("Palettes directory not found: " + palettesPath);
            return palettes;
        }

        File[] paletteFiles = palettesPath.toFile().listFiles((dir, name) ->
            name.endsWith(".png") && !name.equals("trim_palette.png")
        );

        if (paletteFiles == null) {
            return palettes;
        }

        for (File file : paletteFiles) {
            String name = file.getName().replace(".png", "");
            int[] colors = extractPalette(file);
            if (colors != null) {
                palettes.put(name, colors);
            }
        }

        return palettes;
    }

    /**
     * Extracts colors from a single palette PNG file.
     *
     * @param paletteFile the 8x1 PNG file
     *
     * @return array of RGB colors as signed integers, or null if extraction failed
     */
    public static int[] extractPalette(File paletteFile) {
        BufferedImage image;
        try {
            image = ImageIO.read(paletteFile);
        } catch (IOException e) {
            System.err.println("Failed to read palette '" + paletteFile.getName() + "': " + e.getMessage());
            return null;
        }

        if (image == null) {
            System.err.println("Failed to read palette '" + paletteFile.getName() + "': unsupported format");
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (height != 1) {
            System.err.println("Palette '" + paletteFile.getName() + "' has unexpected height: " + height);
        }

        int[] colors = new int[width];
        for (int x = 0; x < width; x++) {
            colors[x] = image.getRGB(x, 0);
        }

        return colors;
    }
}