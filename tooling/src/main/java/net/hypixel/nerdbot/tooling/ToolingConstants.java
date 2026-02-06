package net.hypixel.nerdbot.tooling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Shared constants and utilities for the tooling module.
 */
public final class ToolingConstants {

    // Build directories
    public static final Path BUILD_DIR = Path.of("tooling/build");
    public static final Path MINECRAFT_ASSETS = BUILD_DIR.resolve("minecraft-assets");
    public static final Path RENDERED_ITEMS = BUILD_DIR.resolve("rendered-items");
    public static final Path JAR_CACHE = BUILD_DIR.resolve("minecraft-jars");
    public static final Path MAPPINGS_CACHE = BUILD_DIR.resolve("minecraft-mappings");
    public static final Path RENDERER_CLONE = BUILD_DIR.resolve("MinecraftRenderer");
    // Generator output paths
    public static final String GENERATOR_RESOURCES = "generator/src/main/resources/minecraft/assets";
    public static final String GENERATOR_SPRITESHEETS = GENERATOR_RESOURCES + "/spritesheets";
    public static final String GENERATOR_JSON = GENERATOR_RESOURCES + "/json";
    // Debug flag
    private static boolean debugEnabled = false;

    private ToolingConstants() {
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * Prints a debug message if debug mode is enabled.
     */
    public static void printDebug(String message) {
        if (debugEnabled) {
            System.out.println("[DEBUG] " + message);
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    public static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + p + " - " + e.getMessage());
                    }
                });
        }
    }

    /**
     * Gets the path to a cached Minecraft client JAR.
     */
    public static Path getJarPath(String version) {
        return JAR_CACHE.resolve("minecraft-" + version + "-client.jar");
    }

    /**
     * Gets the path to cached Mojang mappings.
     */
    public static Path getMappingsPath(String version) {
        return MAPPINGS_CACHE.resolve("minecraft-" + version + "-client.txt");
    }
}