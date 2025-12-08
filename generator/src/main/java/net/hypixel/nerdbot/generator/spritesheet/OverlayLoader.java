package net.hypixel.nerdbot.generator.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.overlay.ItemOverlay;
import net.hypixel.nerdbot.generator.item.overlay.OverlayColorOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Loads and caches overlay resources.
 * <p>
 * This class handles loading overlay images, color options, and item bindings
 * from JSON configuration files.
 */
@Slf4j
public class OverlayLoader {

    private static final int DEFAULT_IMAGE_SIZE = 128;
    private static final OverlayLoader INSTANCE;

    static {
        log.debug("Initializing OverlayLoader");
        INSTANCE = new OverlayLoader();
        INSTANCE.loadOverlays();
    }

    private final Map<String, ItemOverlay> itemOverlays = new ConcurrentHashMap<>();
    private final Map<String, OverlayColorOptions> colorOptionsMap = new ConcurrentHashMap<>();
    private final Gson gson;
    private final String resourceBasePath;
    private boolean loaded = false;

    public OverlayLoader() {
        this("/minecraft/assets", new Gson());
    }

    /**
     * Constructor to specify custom resource path and {@link Gson} instance.
     *
     * @param resourceBasePath Base path for resources
     * @param gson             Gson instance for JSON parsing
     */
    public OverlayLoader(String resourceBasePath, Gson gson) {
        this.resourceBasePath = resourceBasePath;
        this.gson = gson;
    }

    /**
     * Get the instance of OverlayLoader.
     *
     * @return The OverlayLoader instance
     */
    public static OverlayLoader getInstance() {
        return INSTANCE;
    }

    /**
     * Load all overlays from JSON configuration files.
     */
    public synchronized void loadOverlays() {
        if (loaded) {
            return;
        }

        try {
            log.debug("Loading overlay resources from {}", resourceBasePath);

            // Load spritesheet
            BufferedImage overlaySpriteSheet = loadImage("/spritesheets/overlays.png");

            // Load color options
            Map<String, OverlayColorOptions> colorOptions = loadColorOptions();
            colorOptionsMap.putAll(colorOptions);

            // Load overlay coordinates
            Map<String, ItemOverlay> overlays = loadOverlayCoordinates(overlaySpriteSheet, colorOptions);

            // Load item bindings
            loadItemBindings(overlays);

            loaded = true;
            log.info("Loaded {} item overlays", itemOverlays.size());
        } catch (IOException e) {
            throw new GeneratorException("Failed to load overlay resources", e);
        }
    }

    private BufferedImage loadImage(String path) throws IOException {
        try (InputStream stream = getResource(resourceBasePath + path)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + path);
            }

            return ImageIO.read(stream);
        }
    }

    private Map<String, OverlayColorOptions> loadColorOptions() throws IOException {
        try (InputStream stream = getResource(resourceBasePath + "/json/overlay_colors.json")) {
            if (stream == null) {
                throw new IOException("overlay_colors.json not found");
            }

            OverlayColorOptions[] options = gson.fromJson(
                new InputStreamReader(stream),
                OverlayColorOptions[].class
            );

            Map<String, OverlayColorOptions> result = new HashMap<>();
            for (OverlayColorOptions option : options) {
                result.put(option.getName(), option);
            }

            log.debug("Loaded {} color option sets", result.size());
            return result;
        }
    }

    private Map<String, ItemOverlay> loadOverlayCoordinates(BufferedImage spriteSheet, Map<String, OverlayColorOptions> colorOptions) throws IOException {
        try (InputStream stream = getResource(resourceBasePath + "/json/overlay_coordinates.json")) {
            if (stream == null) {
                throw new IOException("overlay_coordinates.json not found");
            }

            ItemOverlay[] overlayArray = gson.fromJson(
                new InputStreamReader(stream),
                ItemOverlay[].class
            );

            Map<String, ItemOverlay> result = new HashMap<>();

            for (ItemOverlay overlay : overlayArray) {
                // Extract sub-image from spritesheet
                int size = overlay.getSize() > 0 ? overlay.getSize() : DEFAULT_IMAGE_SIZE;
                overlay.setImage(spriteSheet.getSubimage(
                    overlay.getX(),
                    overlay.getY(),
                    size,
                    size
                ));

                // Skip enchant overlays
                if (overlay.getName().contains("enchant")) {
                    continue;
                }

                // Assign color options
                OverlayColorOptions colorOpt = colorOptions.get(overlay.getColorOptions());
                overlay.setOverlayColorOptions(colorOpt);

                result.put(overlay.getName(), overlay);
            }

            log.debug("Loaded {} overlay definitions", result.size());
            return result;
        }
    }

    private void loadItemBindings(Map<String, ItemOverlay> overlays) throws IOException {
        try (InputStream stream = getResource(resourceBasePath + "/json/item_overlay_binding.json")) {
            if (stream == null) {
                throw new IOException("item_overlay_binding.json not found");
            }

            JsonArray bindings = gson.fromJson(new InputStreamReader(stream), JsonArray.class);

            for (JsonElement element : bindings) {
                JsonObject binding = element.getAsJsonObject();
                String itemName = binding.get("name").getAsString();
                String overlayName = binding.get("overlays").getAsString();

                ItemOverlay overlay = overlays.get(overlayName);
                if (overlay != null) {
                    itemOverlays.put(itemName, overlay);
                }
            }

            log.debug("Loaded {} item-to-overlay bindings", itemOverlays.size());
        }
    }

    /**
     * Get overlay for an item (returns null if no overlay exists).
     *
     * @param itemId Item ID (case-insensitive)
     *
     * @return ItemOverlay or null
     */
    public ItemOverlay getOverlay(String itemId) {
        if (!loaded) {
            loadOverlays();
        }
        return itemOverlays.get(itemId.toLowerCase());
    }

    /**
     * Check if an item has an overlay.
     *
     * @param itemId Item ID (case-insensitive)
     *
     * @return true if overlay exists, false otherwise
     */
    public boolean hasOverlay(String itemId) {
        if (!loaded) {
            loadOverlays();
        }
        return itemOverlays.containsKey(itemId.toLowerCase());
    }

    /**
     * Protected method for resource loading (can be overridden for testing).
     *
     * @param path Resource path
     *
     * @return InputStream or null if not found
     */
    protected InputStream getResource(String path) {
        return getClass().getResourceAsStream(path);
    }

    /**
     * Get all available color option names across all overlay types.
     * This combines color options from leather armor, potions, fireworks, etc.
     *
     * @return Set of all color option names
     */
    public Set<String> getAllColorOptionNames() {
        if (!loaded) {
            loadOverlays();
        }

        return colorOptionsMap.values()
            .stream()
            .flatMap(options -> options.getOptionNames().stream())
            .collect(Collectors.toSet());
    }
}