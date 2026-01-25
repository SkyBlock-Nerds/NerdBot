package net.hypixel.nerdbot.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.discord.storage.DataSerialization;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a texture atlas (spritesheet) from individual PNG textures.
 * All textures are normalized to IMAGE_SIZE x IMAGE_SIZE and packed into rows.
 */
public class SpritesheetGenerator {

    protected static final int IMAGE_SIZE = 256;
    private static final int ATLAS_WIDTH = IMAGE_SIZE * 16;

    private static final String TOOLING_RESOURCES = "tooling/src/main/resources/minecraft";
    private static final String GENERATOR_RESOURCES = "generator/src/main/resources/minecraft/assets";

    public static void main(String[] args) {
        String inputPath = TOOLING_RESOURCES + "/textures";
        String outputPath = GENERATOR_RESOURCES + "/spritesheets/minecraft_texture_atlas.png";
        String jsonPath = GENERATOR_RESOURCES + "/json/atlas_coordinates.json";

        // Load all textures
        List<TextureInfo> textures = loadTextures(inputPath);
        System.out.println("Loaded " + textures.size() + " textures.");

        // Calculate atlas dimensions
        int texturesPerRow = ATLAS_WIDTH / IMAGE_SIZE;
        int rows = (textures.size() + texturesPerRow - 1) / texturesPerRow;
        int atlasHeight = rows * IMAGE_SIZE;

        // Create atlas and pack textures
        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        int[] atlasPixels = ((DataBufferInt) atlas.getRaster().getDataBuffer()).getData();

        for (int i = 0; i < textures.size(); i++) {
            TextureInfo entry = textures.get(i);
            int col = i % texturesPerRow;
            int row = i / texturesPerRow;
            entry.x = col * IMAGE_SIZE;
            entry.y = row * IMAGE_SIZE;

            // Copy texture pixels into atlas
            int[] texturePixels = ((DataBufferInt) entry.image.getRaster().getDataBuffer()).getData();
            for (int py = 0; py < IMAGE_SIZE; py++) {
                int atlasOffset = (entry.y + py) * ATLAS_WIDTH + entry.x;
                int textureOffset = py * IMAGE_SIZE;
                System.arraycopy(texturePixels, textureOffset, atlasPixels, atlasOffset, IMAGE_SIZE);
            }

            System.out.printf("\rPacking textures: %d/%d (%.1f%%)", i + 1, textures.size(), (i + 1) * 100.0 / textures.size());
        }
        System.out.println();

        // Save outputs
        saveAtlas(atlas, outputPath);
        saveCoordinatesJson(textures, jsonPath);
        System.out.println("Texture atlas generation complete!");
    }

    private static List<TextureInfo> loadTextures(String inputDir) {
        List<TextureInfo> textures = new ArrayList<>();
        File folder = new File(inputDir);
        File[] files = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));

        if (files == null) {
            System.err.println("No textures found in: " + folder.getAbsolutePath());
            return textures;
        }

        System.out.println("Loading textures from: " + folder.getAbsolutePath());
        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            try {
                BufferedImage raw = ImageIO.read(file);
                BufferedImage processed = toArgb(raw);

                String name = file.getName().replace(".png", "");
                textures.add(new TextureInfo(name, processed));
            } catch (IOException exception) {
                System.err.println("Failed to load: " + file.getName());
                exception.printStackTrace();
            }
        }

        return textures;
    }

    /**
     * Checks if the image is grayscale (1 or 2 bands), excluding indexed color images.
     * Grayscale images need raw raster access instead of getRGB() to avoid sRGB color space conversion.
     * <p>
     * Bands are color channels: 1 = grayscale, 2 = grayscale + alpha, 3 = RGB, 4 = RGBA.
     *
     * @see <a href="http://www.libpng.org/pub/png/spec/1.1/PNG-Chunks.html#C.sBIT">PNG Specification - sBIT chunk (channels per color type)</a>
     */
    private static boolean isGrayscaleFormat(BufferedImage image) {
        int numBands = image.getRaster().getNumBands();
        int type = image.getType();

        // Indexed color has 1 band but stores palette indices, not grayscale - use getRGB() instead
        if (type == BufferedImage.TYPE_BYTE_INDEXED) {
            return false;
        }

        // 1 band = grayscale, 2 bands = grayscale + alpha
        return numBands == 1 || numBands == 2;
    }

    /**
     * Converts any image to TYPE_INT_ARGB, resizing with nearest-neighbor sampling.
     */
    private static BufferedImage toArgb(BufferedImage source) {
        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();

        BufferedImage result = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] destPixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        if (isGrayscaleFormat(source)) {
            // Read raw samples to preserve linear grayscale values without gamma conversion
            Raster srcRaster = source.getRaster();
            int numBands = srcRaster.getNumBands();
            int[] samples = new int[numBands];

            for (int dstY = 0; dstY < IMAGE_SIZE; dstY++) {
                int srcY = dstY * srcHeight / IMAGE_SIZE;
                for (int dstX = 0; dstX < IMAGE_SIZE; dstX++) {
                    int srcX = dstX * srcWidth / IMAGE_SIZE;
                    srcRaster.getPixel(srcX, srcY, samples);
                    int gray = samples[0];
                    int alpha = numBands == 2 ? samples[1] : 255;
                    destPixels[dstY * IMAGE_SIZE + dstX] = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
                }
            }
        } else {
            // Use getRGB() for indexed, RGB, RGBA - handles palette lookup and transparency correctly
            for (int dstY = 0; dstY < IMAGE_SIZE; dstY++) {
                int srcY = dstY * srcHeight / IMAGE_SIZE;
                for (int dstX = 0; dstX < IMAGE_SIZE; dstX++) {
                    int srcX = dstX * srcWidth / IMAGE_SIZE;
                    destPixels[dstY * IMAGE_SIZE + dstX] = source.getRGB(srcX, srcY);
                }
            }
        }

        return result;
    }

    private static void saveAtlas(BufferedImage atlas, String outputPath) {
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
            return;
        }

        try {
            System.out.println("Saving texture atlas to: " + outputFile.getAbsolutePath());
            ImageIO.write(atlas, "png", outputFile);
            System.out.println("Texture atlas saved successfully!");
        } catch (IOException exception) {
            System.err.println("Failed to save texture atlas!");
            exception.printStackTrace();
        }
    }

    private static void saveCoordinatesJson(List<TextureInfo> textures, String jsonPath) {
        JsonArray jsonArray = new JsonArray();

        for (TextureInfo entry : textures) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", entry.name);
            obj.addProperty("x", entry.x);
            obj.addProperty("y", entry.y);
            obj.addProperty("size", IMAGE_SIZE);
            jsonArray.add(obj);
        }

        File jsonFile = new File(jsonPath);
        File parentDir = jsonFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
            return;
        }

        try (FileWriter writer = new FileWriter(jsonFile)) {
            DataSerialization.GSON.toJson(jsonArray, writer);
            System.out.println("Texture atlas coordinates JSON saved successfully!");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    static class TextureInfo {
        final String name;
        final BufferedImage image;
        int x;
        int y;

        TextureInfo(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
        }
    }
}