package net.hypixel.nerdbot.tooling.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.tooling.ToolingConstants;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates the overlay spritesheet from Minecraft texture assets.
 */
public class OverlayGenerator extends AtlasGenerator {

    private static final int ATLAS_WIDTH = 1_024;

    private static final Path ITEM_TEXTURES = ToolingConstants.MINECRAFT_ASSETS.resolve("textures/item");
    private static final Path TRIM_ITEMS = ToolingConstants.MINECRAFT_ASSETS.resolve("textures/trims/items");
    private static final Path OVERLAY_DEFINITIONS_PATH = Path.of(ToolingConstants.GENERATOR_JSON + "/overlay_definitions.json");

    private List<OverlayDefinition> specialOverlayDefinitions = List.of();
    private List<String> armorOverlayPatterns = List.of("leather_", "wolf_");

    public OverlayGenerator() {
        super(
            ATLAS_WIDTH,
            ToolingConstants.GENERATOR_SPRITESHEETS + "/overlays.png",
            ToolingConstants.GENERATOR_JSON + "/overlay_coordinates.json"
        );
    }

    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(50));
            System.out.println("Overlay Generator");
            System.out.println("=".repeat(50));

            if (!Files.exists(ToolingConstants.MINECRAFT_ASSETS)) {
                throw new IOException("Minecraft assets not found at: " + ToolingConstants.MINECRAFT_ASSETS +
                    "\nRun 'MinecraftAssetTool --download' first.");
            }

            new OverlayGenerator().generate();
            new OverlayColorConfigGenerator().generate();

            System.out.println("\n" + "=".repeat(50));
            System.out.println("Overlay generation complete!");
            System.out.println("=".repeat(50));
        } catch (IOException e) {
            System.err.println("Failed to generate overlays: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    protected List<AtlasEntry> loadTextures() {
        List<AtlasEntry> overlays = new ArrayList<>();

        loadOverlayDefinitions();

        System.out.println("\nDiscovering dyeable armor overlays from: " + ITEM_TEXTURES);
        List<OverlayDefinition> armorOverlays = discoverArmorOverlays();
        System.out.println("  Found " + armorOverlays.size() + " armor overlays");
        loadOverlays(overlays, ITEM_TEXTURES, armorOverlays);

        System.out.println("\nDiscovering special overlays from config");
        List<OverlayDefinition> specialOverlays = discoverSpecialOverlays();
        System.out.println("  Found " + specialOverlays.size() + " special overlays");
        loadOverlays(overlays, ITEM_TEXTURES, specialOverlays);

        System.out.println("\nDiscovering trim overlays from: " + TRIM_ITEMS);
        List<OverlayDefinition> trimOverlays = discoverTrimOverlays();
        System.out.println("  Found " + trimOverlays.size() + " trim types");
        loadOverlays(overlays, TRIM_ITEMS, trimOverlays);

        System.out.println("\nTotal overlays loaded: " + overlays.size());
        return overlays;
    }

    /**
     * Loads overlay definitions from overlay_definitions.json config file.
     */
    private void loadOverlayDefinitions() {
        if (!Files.exists(OVERLAY_DEFINITIONS_PATH)) {
            System.err.println("  WARNING: overlay_definitions.json not found, using defaults");
            return;
        }

        try (FileReader reader = new FileReader(OVERLAY_DEFINITIONS_PATH.toFile())) {
            Gson gson = new Gson();
            JsonObject config = gson.fromJson(reader, JsonObject.class);

            // Load special overlays
            if (config.has("specialOverlays")) {
                List<OverlayDefinition> specials = new ArrayList<>();
                JsonArray specialsArray = config.getAsJsonArray("specialOverlays");
                for (JsonElement element : specialsArray) {
                    JsonObject obj = element.getAsJsonObject();
                    specials.add(new OverlayDefinition(
                        obj.get("fileName").getAsString(),
                        obj.get("colorOptions").getAsString(),
                        obj.get("type").getAsString(),
                        obj.has("colorMode") ? obj.get("colorMode").getAsString() : null
                    ));
                }
                specialOverlayDefinitions = specials;
                System.out.println("  Loaded " + specials.size() + " special overlay definitions from config");
            }

            // Load armor overlay patterns
            if (config.has("armorOverlayPatterns")) {
                List<String> patterns = new ArrayList<>();
                JsonArray patternsArray = config.getAsJsonArray("armorOverlayPatterns");
                for (JsonElement element : patternsArray) {
                    patterns.add(element.getAsString());
                }
                armorOverlayPatterns = patterns;
                System.out.println("  Loaded " + patterns.size() + " armor overlay patterns from config");
            }
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to load overlay_definitions.json: " + e.getMessage());
        }
    }

    /**
     * Filters special overlay definitions to only include files that exist in assets.
     * These have unique color handling so mappings are kept explicit, but we verify existence.
     */
    private List<OverlayDefinition> discoverSpecialOverlays() {
        if (!Files.exists(ITEM_TEXTURES)) {
            return List.of();
        }

        return specialOverlayDefinitions.stream()
            .filter(def -> ITEM_TEXTURES.resolve(def.fileName + ".png").toFile().exists())
            .collect(Collectors.toList());
    }

    /**
     * Discovers dyeable armor overlays from the textures directory.
     * Uses patterns from overlay_definitions.json (e.g., leather_*, wolf_*)
     */
    private List<OverlayDefinition> discoverArmorOverlays() {
        List<OverlayDefinition> definitions = new ArrayList<>();

        if (!Files.exists(ITEM_TEXTURES)) {
            return definitions;
        }

        File[] files = ITEM_TEXTURES.toFile().listFiles((dir, name) -> {
            if (!name.endsWith("_overlay.png")) {
                return false;
            }

            return armorOverlayPatterns.stream().anyMatch(name::startsWith);
        });

        if (files == null) {
            return definitions;
        }

        for (File file : files) {
            String name = file.getName().replace(".png", "");
            definitions.add(new OverlayDefinition(name, "leather_armor", "NORMAL", null));
        }

        return definitions.stream()
            .sorted((a, b) -> a.fileName.compareTo(b.fileName))
            .collect(Collectors.toList());
    }

    /**
     * Discovers armor trim overlay type from the trims/items directory.
     * Each unique armor type (helmet, chestplate, etc.) found becomes a trim definition.
     */
    private List<OverlayDefinition> discoverTrimOverlays() {
        List<OverlayDefinition> definitions = new ArrayList<>();

        if (!Files.exists(TRIM_ITEMS)) {
            return definitions;
        }

        File[] files = TRIM_ITEMS.toFile().listFiles((dir, name) -> name.endsWith(".png"));

        if (files == null) {
            return definitions;
        }

        // Extract unique armor types from filenames like "coast_helmet.png" -> "helmet"
        // Pattern: {trim_pattern}_{armor_type}.png
        return Arrays.stream(files)
            .map(f -> f.getName().replace(".png", ""))
            .map(name -> {
                int lastUnderscore = name.lastIndexOf('_');
                return lastUnderscore > 0 ? name.substring(lastUnderscore + 1) : null;
            })
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .map(type -> new OverlayDefinition(type + "_trim", "armor_trim", "MAPPED", null))
            .collect(Collectors.toList());
    }

    private void loadOverlays(List<AtlasEntry> overlays, Path sourceDir, List<OverlayDefinition> definitions) {
        if (!Files.exists(sourceDir)) {
            System.err.println("  Directory not found: " + sourceDir);
            return;
        }

        for (OverlayDefinition def : definitions) {
            File file = sourceDir.resolve(def.fileName + ".png").toFile();
            if (!file.exists()) {
                System.err.println("  Not found: " + def.fileName);
                continue;
            }

            try {
                BufferedImage image = ImageIO.read(file);
                if (image == null) {
                    System.err.println("  Failed to read: " + def.fileName);
                    continue;
                }

                BufferedImage resized = resizeToAtlasSize(ImageUtil.convertToArgbRaw(image));

                AtlasEntry entry = new AtlasEntry(def.fileName, resized)
                    .withMeta("type", def.type)
                    .withMeta("colorOptions", def.colorOptions);

                if (def.colorMode != null) {
                    entry.withMeta("colorMode", def.colorMode);
                }

                overlays.add(entry);
                System.out.println("  Loaded: " + def.fileName + " (" + image.getWidth() + "x" + image.getHeight() + " -> " + IMAGE_SIZE + "x" + IMAGE_SIZE + ")");
            } catch (IOException e) {
                System.err.println("  Error loading " + def.fileName + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected JsonObject entryToJson(AtlasEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", entry.name);
        obj.addProperty("x", entry.x);
        obj.addProperty("y", entry.y);
        obj.addProperty("size", IMAGE_SIZE);
        obj.addProperty("type", entry.getMeta("type"));
        obj.addProperty("colorOptions", entry.getMeta("colorOptions"));

        String colorMode = entry.getMeta("colorMode");
        if (colorMode != null) {
            obj.addProperty("colorMode", colorMode);
        }
        return obj;
    }

    private record OverlayDefinition(String fileName, String colorOptions, String type, String colorMode) {
    }
}