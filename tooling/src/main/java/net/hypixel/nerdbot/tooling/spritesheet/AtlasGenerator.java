package net.hypixel.nerdbot.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.discord.storage.DataSerialization;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for texture atlas generation.
 */
public abstract class AtlasGenerator {

    protected static final int IMAGE_SIZE = 256;

    protected final int atlasWidth;
    protected final String atlasOutputPath;
    protected final String jsonOutputPath;

    protected AtlasGenerator(int atlasWidth, String atlasOutputPath, String jsonOutputPath) {
        this.atlasWidth = atlasWidth;
        this.atlasOutputPath = atlasOutputPath;
        this.jsonOutputPath = jsonOutputPath;
    }

    protected static BufferedImage resizeToAtlasSize(BufferedImage source) {
        return ImageUtil.resizeImageRaw(source, IMAGE_SIZE, IMAGE_SIZE);
    }

    protected abstract List<AtlasEntry> loadTextures();

    protected abstract JsonObject entryToJson(AtlasEntry entry);

    public void generate() {
        List<AtlasEntry> entries = loadTextures();
        System.out.println("Loaded " + entries.size() + " textures.");

        if (entries.isEmpty()) {
            System.err.println("No textures to pack!");
            return;
        }

        BufferedImage atlas = packAtlas(entries);
        saveAtlas(atlas);
        saveCoordinatesJson(entries);

        System.out.println("Atlas generation complete!");
    }

    protected BufferedImage packAtlas(List<AtlasEntry> entries) {
        int texturesPerRow = atlasWidth / IMAGE_SIZE;
        int rows = (entries.size() + texturesPerRow - 1) / texturesPerRow;
        int atlasHeight = rows * IMAGE_SIZE;

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        int[] atlasPixels = ((DataBufferInt) atlas.getRaster().getDataBuffer()).getData();

        for (int i = 0; i < entries.size(); i++) {
            AtlasEntry entry = entries.get(i);
            int col = i % texturesPerRow;
            int row = i / texturesPerRow;
            entry.x = col * IMAGE_SIZE;
            entry.y = row * IMAGE_SIZE;

            int[] texturePixels = ((DataBufferInt) entry.image.getRaster().getDataBuffer()).getData();
            for (int py = 0; py < IMAGE_SIZE; py++) {
                int atlasOffset = (entry.y + py) * atlasWidth + entry.x;
                int textureOffset = py * IMAGE_SIZE;
                System.arraycopy(texturePixels, textureOffset, atlasPixels, atlasOffset, IMAGE_SIZE);
            }

            double progress = (i + 1) * 100.0 / entries.size();
            System.out.printf("\rPacking textures: %d/%d (%.1f%%)", i + 1, entries.size(), progress);
        }
        System.out.println();

        return atlas;
    }

    protected void saveAtlas(BufferedImage atlas) {
        File outputFile = new File(atlasOutputPath);
        File parentDir = outputFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
            return;
        }

        try {
            System.out.println("Saving atlas to: " + outputFile.getAbsolutePath());
            ImageIO.write(atlas, "png", outputFile);
            System.out.println("Atlas saved successfully!");
        } catch (IOException e) {
            System.err.println("Failed to save atlas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected void saveCoordinatesJson(List<AtlasEntry> entries) {
        File jsonFile = new File(jsonOutputPath);
        File parentDir = jsonFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
            return;
        }

        JsonArray jsonArray = new JsonArray();
        for (AtlasEntry entry : entries) {
            jsonArray.add(entryToJson(entry));
        }

        try (FileWriter writer = new FileWriter(jsonFile)) {
            DataSerialization.GSON.toJson(jsonArray, writer);
            System.out.println("Coordinates JSON saved to: " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save coordinates JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Atlas entry with name, image, position, and optional metadata.
     */
    public static class AtlasEntry {
        public final String name;
        public final BufferedImage image;
        public final Map<String, String> metadata;
        public int x;
        public int y;

        public AtlasEntry(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
            this.metadata = new HashMap<>();
        }

        public AtlasEntry withMeta(String key, String value) {
            metadata.put(key, value);
            return this;
        }

        public String getMeta(String key) {
            return metadata.get(key);
        }
    }
}