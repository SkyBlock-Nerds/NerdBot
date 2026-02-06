package net.hypixel.nerdbot.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.discord.storage.DataSerialization;
import net.hypixel.nerdbot.tooling.ToolingConstants;
import net.hypixel.nerdbot.tooling.minecraft.ColorExtractor;
import net.hypixel.nerdbot.tooling.minecraft.DyeColorData;
import net.hypixel.nerdbot.tooling.minecraft.ExtractedColors;
import net.hypixel.nerdbot.tooling.minecraft.TrimPaletteExtractor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generates overlay_colors.json by extracting colors and trim palette PNGs
 * from the Minecraft JAR. This config defines color options for dyeable items,
 * potions, effects, fireworks, and armor trims.
 */
public class OverlayColorConfigGenerator {

    // Default undyed leather armor color (brown)
    private static final int DEFAULT_LEATHER_COLOR = 0xA06540;

    // Water bottle and default potion color (blue)
    private static final int WATER_POTION_COLOR = 0xFF395FCA;

    // Default firework star color (white)
    private static final int DEFAULT_FIREWORK_COLOR = 0xFFFFFF;

    // Grayscale values used in Minecraft's trim overlay textures
    private static final int[] TRIM_GRAYSCALE_MAP = {
        0xFFE0E0E0, // Light gray
        0xFFC0C0C0,
        0xFFA0A0A0,
        0xFF808080, // Mid-gray
        0xFF606060,
        0xFF404040,
        0xFF202020,
        0xFF000000  // Black
    };

    private final String version;

    public OverlayColorConfigGenerator() {
        this(null);
    }

    public OverlayColorConfigGenerator(String version) {
        this.version = version;
    }

    /**
     * Generates overlay_colors.json.
     */
    public void generate() throws IOException {
        System.out.println("\nGenerating overlay color config...");

        ExtractedColors extractedColors = extractColorsFromJar();
        Map<String, int[]> trimPalettes = extractTrimPalettes();

        if (extractedColors == null) {
            throw new IOException("Failed to extract colors from JAR. Ensure the Minecraft JAR and mappings are downloaded.");
        }

        JsonArray colorConfig = buildColorConfig(extractedColors, trimPalettes);
        saveConfig(colorConfig);
    }

    private ExtractedColors extractColorsFromJar() throws IOException {
        String resolvedVersion = version != null ? version : findCachedVersion();

        if (resolvedVersion == null) {
            System.err.println("  ERROR: No cached JAR found. Run 'MinecraftAssetTool --download' first.");
            return null;
        }

        Path jarPath = ToolingConstants.getJarPath(resolvedVersion);
        Path mappingsPath = ToolingConstants.getMappingsPath(resolvedVersion);

        if (!Files.exists(jarPath) || !Files.exists(mappingsPath)) {
            System.err.println("  ERROR: JAR or mappings not found");
            if (!Files.exists(jarPath)) {
                System.err.println("    Missing JAR: " + jarPath);
            }

            if (!Files.exists(mappingsPath)) {
                System.err.println("    Missing mappings: " + mappingsPath);
            }

            return null;
        }

        System.out.println("  Extracting colors from JAR (version " + resolvedVersion + ")...");
        try {
            ColorExtractor extractor = new ColorExtractor(jarPath, mappingsPath);
            ExtractedColors colors = extractor.extract();
            System.out.println("  Successfully extracted " + colors.dyeColors.size() + " dye colors and " +
                colors.potionColors.size() + " potion colors from JAR");
            return colors;
        } catch (Exception e) {
            System.err.println("  ERROR: Failed to extract colors from JAR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String findCachedVersion() throws IOException {
        Path jarCacheDir = ToolingConstants.JAR_CACHE;
        if (!Files.exists(jarCacheDir)) {
            return null;
        }

        try (Stream<Path> files = Files.list(jarCacheDir)) {
            return files
                .filter(p -> p.getFileName().toString().endsWith("-client.jar"))
                .findFirst()
                .map(p -> p.getFileName().toString()
                    .replace("minecraft-", "")
                    .replace("-client.jar", ""))
                .orElse(null);
        }
    }

    private Map<String, int[]> extractTrimPalettes() {
        System.out.println("  Extracting trim palettes from PNG files...");
        Map<String, int[]> palettes = TrimPaletteExtractor.extractAllPalettes();
        System.out.println("  Found " + palettes.size() + " trim palettes");
        return palettes;
    }

    private JsonArray buildColorConfig(ExtractedColors colors, Map<String, int[]> trimPalettes) {
        JsonArray config = new JsonArray();

        if (colors.dyeColors.isEmpty()) {
            System.err.println("  ERROR: No dye colors extracted from JAR!");
        } else {
            System.out.println("  Adding leather_armor colors (" + colors.dyeColors.size() + " colors)");
            config.add(createLeatherArmorConfig(colors.dyeColors));
        }

        if (trimPalettes.isEmpty()) {
            System.err.println("  ERROR: No trim palettes found in assets!");
        } else {
            System.out.println("  Adding armor_trim colors (" + trimPalettes.size() + " palettes)");
            config.add(createArmorTrimConfig(trimPalettes));
        }

        if (colors.potionColors.isEmpty()) {
            System.err.println("  ERROR: No potion colors extracted from JAR!");
        } else {
            System.out.println("  Adding potion colors (" + colors.potionColors.size() + " effects with potion forms)");
            config.add(createPotionConfig(colors.potionColors));
        }

        if (colors.effectColors.isEmpty()) {
            System.err.println("  ERROR: No effect colors extracted from JAR!");
        } else {
            System.out.println("  Adding effect colors (" + colors.effectColors.size() + " total effects)");
            config.add(createEffectConfig(colors.effectColors));
        }

        if (colors.dyeColors.isEmpty()) {
            System.err.println("  ERROR: No firework colors (uses dye colors) extracted from JAR!");
        } else {
            System.out.println("  Adding firework colors (" + colors.dyeColors.size() + " colors)");
            config.add(createFireworkConfig(colors.dyeColors));
        }

        return config;
    }

    private void saveConfig(JsonArray config) throws IOException {
        File outputDir = new File(ToolingConstants.GENERATOR_JSON);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + outputDir);
        }

        File outputFile = new File(ToolingConstants.GENERATOR_JSON, "overlay_colors.json");
        try (FileWriter writer = new FileWriter(outputFile)) {
            DataSerialization.GSON.toJson(config, writer);
        }

        System.out.println("Color config saved to: " + outputFile.getAbsolutePath());
    }

    private JsonObject createLeatherArmorConfig(Map<String, DyeColorData> dyeColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "leather_armor");

        JsonObject options = new JsonObject();
        for (Map.Entry<String, DyeColorData> entry : dyeColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue().dyeColor()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(DEFAULT_LEATHER_COLOR));

        return config;
    }

    private JsonObject createArmorTrimConfig(Map<String, int[]> palettes) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "armor_trim");

        JsonObject map = new JsonObject();
        for (int i = 0; i < TRIM_GRAYSCALE_MAP.length; i++) {
            map.addProperty(String.valueOf(TRIM_GRAYSCALE_MAP[i]), i);
        }
        config.add("map", map);

        JsonObject options = new JsonObject();
        for (Map.Entry<String, int[]> entry : palettes.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", false);
        config.addProperty("useDefaultIfMissing", false);

        return config;
    }

    private JsonObject createPotionConfig(Map<String, Integer> potionColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "potion");

        JsonObject options = new JsonObject();
        options.add("water", jsonArray(WATER_POTION_COLOR));

        for (Map.Entry<String, Integer> entry : potionColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(WATER_POTION_COLOR));

        return config;
    }

    private JsonObject createEffectConfig(Map<String, Integer> effectColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "effect");

        JsonObject options = new JsonObject();
        options.add("water", jsonArray(WATER_POTION_COLOR));

        for (Map.Entry<String, Integer> entry : effectColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(WATER_POTION_COLOR));

        return config;
    }

    private JsonObject createFireworkConfig(Map<String, DyeColorData> dyeColors) {
        JsonObject config = new JsonObject();
        config.addProperty("name", "firework");

        JsonObject options = new JsonObject();
        for (Map.Entry<String, DyeColorData> entry : dyeColors.entrySet()) {
            options.add(entry.getKey(), jsonArray(entry.getValue().fireworkColor()));
        }
        config.add("options", options);

        config.addProperty("allowHexColors", true);
        config.addProperty("useDefaultIfMissing", true);
        config.add("defaultColors", jsonArray(DEFAULT_FIREWORK_COLOR));

        return config;
    }

    private JsonArray jsonArray(int... values) {
        JsonArray arr = new JsonArray();
        for (int v : values) {
            arr.add(v);
        }
        return arr;
    }
}