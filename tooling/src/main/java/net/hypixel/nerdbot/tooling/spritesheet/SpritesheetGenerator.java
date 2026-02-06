package net.hypixel.nerdbot.tooling.spritesheet;

import com.google.gson.JsonObject;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.tooling.ToolingConstants;
import net.hypixel.nerdbot.tooling.minecraft.TrimPaletteExtractor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Generates a texture atlas from individual PNG textures.
 */
public class SpritesheetGenerator extends AtlasGenerator {

    private static final int ATLAS_WIDTH = IMAGE_SIZE * 16;
    private static final String[] ARMOR_TYPES = {"helmet", "chestplate", "leggings", "boots"};

    private final String inputPath;
    private Set<String> trimMaterials;

    public SpritesheetGenerator(String inputPath) {
        super(
            ATLAS_WIDTH,
            ToolingConstants.GENERATOR_SPRITESHEETS + "/minecraft_texture_atlas.png",
            ToolingConstants.GENERATOR_JSON + "/atlas_coordinates.json"
        );
        this.inputPath = inputPath;
    }

    public static void main(String[] args) {
        String inputPath = ToolingConstants.RENDERED_ITEMS.toString();

        for (String arg : args) {
            if (arg.startsWith("--input=")) {
                inputPath = arg.substring("--input=".length());
            }
        }

        SpritesheetGenerator generator = new SpritesheetGenerator(inputPath);
        generator.generate();
        System.out.println("Texture atlas generation complete!");
    }

    private boolean isArmorTrimVariant(String name) {
        if (!name.endsWith("_trim")) {
            return false;
        }

        if (trimMaterials == null || trimMaterials.isEmpty()) {
            return false;
        }

        return Arrays.stream(ARMOR_TYPES)
            .anyMatch(armor -> trimMaterials.stream()
                .anyMatch(material -> name.endsWith(armor + "_" + material + "_trim"))
            );
    }

    private void loadTrimMaterials() {
        try {
            trimMaterials = TrimPaletteExtractor.extractAllPalettes().keySet();
            System.out.println("Loaded " + trimMaterials.size() + " trim materials from assets.");
        } catch (Exception e) {
            System.err.println("Failed to load trim materials: " + e.getMessage());
            trimMaterials = Set.of();
        }
    }

    @Override
    protected List<AtlasEntry> loadTextures() {
        loadTrimMaterials();

        List<AtlasEntry> textures = new ArrayList<>();
        File folder = new File(inputPath);
        File[] files = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));

        if (files == null) {
            System.err.println("No textures found in: " + folder.getAbsolutePath());
            return textures;
        }

        System.out.println("Loading textures from: " + folder.getAbsolutePath());
        Arrays.sort(files, Comparator.comparing(File::getName));

        int skipped = 0;
        int trimFiltered = 0;

        for (File file : files) {
            String name = file.getName().replace(".png", "");

            if (isArmorTrimVariant(name)) {
                trimFiltered++;
                continue;
            }

            BufferedImage image = loadImage(file);
            if (image == null) {
                System.err.println("Failed to load: " + file.getName());
                continue;
            }

            try {
                BufferedImage processed = resizeToAtlasSize(image);

                if (ImageUtil.isFullyTransparent(processed)) {
                    skipped++;
                    continue;
                }

                textures.add(new AtlasEntry(name, processed));
            } catch (Exception e) {
                System.err.println("Failed to process: " + file.getName());
                e.printStackTrace();
            }
        }

        if (skipped > 0) {
            System.out.println("Skipped " + skipped + " fully transparent textures.");
        }

        if (trimFiltered > 0) {
            System.out.println("Filtered out " + trimFiltered + " armor trim variants (rendered dynamically).");
        }

        return textures;
    }

    private BufferedImage loadImage(File file) {
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected JsonObject entryToJson(AtlasEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", entry.name);
        obj.addProperty("x", entry.x);
        obj.addProperty("y", entry.y);
        obj.addProperty("size", IMAGE_SIZE);
        return obj;
    }
}