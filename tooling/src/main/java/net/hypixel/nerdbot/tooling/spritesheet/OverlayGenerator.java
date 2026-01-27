package net.hypixel.nerdbot.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.discord.storage.DataSerialization;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class OverlayGenerator {

    public static final int OVERLAY_OUTPUT_SIZE = SpritesheetGenerator.IMAGE_SIZE; // Match item size from actual spritesheet
    private static final int ATLAS_WIDTH = 1_024;

    private static final String TOOLING_RESOURCES = "tooling/src/main/resources/minecraft";
    private static final String GENERATOR_RESOURCES = "generator/src/main/resources/minecraft/assets";

    private static final List<OverlayInfo> overlayInfo = new ArrayList<>();

    public static void main(String[] args) {
        String inputPath = TOOLING_RESOURCES + "/textures/overlays";
        String outputDir = GENERATOR_RESOURCES + "/spritesheets";
        String jsonOutputDir = GENERATOR_RESOURCES + "/json";
        String atlasName = "overlays.png";
        String jsonFileName = "overlay_coordinates.json";

        List<String> overlayNames = new ArrayList<>();
        overlayInfo.addAll(loadOverlays(inputPath, overlayNames));

        System.out.println("Loaded " + overlayInfo.size() + " overlays.");

        BufferedImage atlas = createOverlayAtlas();
        saveOverlayAtlas(atlas, outputDir, jsonOutputDir, atlasName, jsonFileName);
        System.out.println("Overlay atlas generation complete!");
    }

    private static List<OverlayInfo> loadOverlays(String inputDir, List<String> overlayNames) {
        List<OverlayInfo> overlayInfoList = new ArrayList<>();
        File folder = new File(inputDir);
        File[] files = folder.listFiles(new PNGFileFilter());

        if (files != null) {
            System.out.println("Loading overlays from: " + folder.getAbsolutePath());

            Arrays.sort(files, Comparator.comparing(File::getName));

            for (File file : files) {
                try {
                    BufferedImage overlay = ImageIO.read(file);
                    System.out.println("Loaded overlay: " + file.getName());

                    // Always resize overlays to match item dimensions (256x256)
                    System.out.println("Scaling overlay: " + file.getName() + " from " +
                                     overlay.getWidth() + "x" + overlay.getHeight() +
                                     " to " + OVERLAY_OUTPUT_SIZE + "x" + OVERLAY_OUTPUT_SIZE);
                    overlay = resizeOverlay(overlay);

                    String overlayName = file.getName().split("\\.")[0];
                    OverlayInfo overlayInfo = new OverlayInfo(overlayName, overlay, -1, -1);
                    overlayInfoList.add(overlayInfo);
                    overlayNames.add(overlayName);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }

        return overlayInfoList;
    }

    /**
     * Converts an image to TYPE_INT_ARGB format.
     */
    private static BufferedImage ensureArgbFormat(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] destPixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                destPixels[y * width + x] = source.getRGB(x, y);
            }
        }

        return result;
    }

    /**
     * Resizes an overlay using direct pixel copying with nearest-neighbor interpolation.
     * This preserves straight alpha values without premultiplication.
     */
    private static BufferedImage resizeOverlay(BufferedImage originalOverlay) {
        BufferedImage source = ensureArgbFormat(originalOverlay);
        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();

        int[] srcPixels = ((DataBufferInt) source.getRaster().getDataBuffer()).getData();

        BufferedImage resizedOverlay = new BufferedImage(OVERLAY_OUTPUT_SIZE, OVERLAY_OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] dstPixels = ((DataBufferInt) resizedOverlay.getRaster().getDataBuffer()).getData();

        for (int dstY = 0; dstY < OVERLAY_OUTPUT_SIZE; dstY++) {
            int srcY = dstY * srcHeight / OVERLAY_OUTPUT_SIZE;
            for (int dstX = 0; dstX < OVERLAY_OUTPUT_SIZE; dstX++) {
                int srcX = dstX * srcWidth / OVERLAY_OUTPUT_SIZE;
                dstPixels[dstY * OVERLAY_OUTPUT_SIZE + dstX] = srcPixels[srcY * srcWidth + srcX];
            }
        }

        return resizedOverlay;
    }

    private static BufferedImage createOverlayAtlas() {
        int atlasHeight = 0;
        int x = 0;
        int y = 0;

        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, 1, BufferedImage.TYPE_INT_ARGB);

        int totalOverlays = overlayInfo.size();
        double progressStep = 1.0 / totalOverlays;
        double progress = 0.0;

        for (OverlayInfo overlayInfo : overlayInfo) {
            int frameWidth = OVERLAY_OUTPUT_SIZE;

            if (x + frameWidth > ATLAS_WIDTH) {
                x = 0;
                y += atlasHeight;
            }

            atlasHeight = OVERLAY_OUTPUT_SIZE;

            atlas = extendOverlayAtlas(atlas, x, y, overlayInfo.getImage());

            overlayInfo.setX(x);
            overlayInfo.setY(y);

            x += frameWidth;

            progress += progressStep;
            System.out.print("\r");
            System.out.printf("Overlay atlas creation progress: %.2f%%", progress * 100);
        }

        return atlas;
    }

    /**
     * Extends the overlay atlas to fit the new overlay at the specified position.
     * Uses direct raster data access to preserve straight alpha values.
     */
    private static BufferedImage extendOverlayAtlas(BufferedImage atlas, int x, int y, BufferedImage overlay) {
        int newWidth = Math.max(atlas.getWidth(), x + overlay.getWidth());
        int newHeight = Math.max(atlas.getHeight(), y + overlay.getHeight());

        BufferedImage newAtlas = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        int[] destPixels = ((DataBufferInt) newAtlas.getRaster().getDataBuffer()).getData();

        if (atlas.getWidth() > 0 && atlas.getHeight() > 0) {
            int[] atlasPixels = ((DataBufferInt) atlas.getRaster().getDataBuffer()).getData();
            for (int row = 0; row < atlas.getHeight(); row++) {
                System.arraycopy(atlasPixels, row * atlas.getWidth(), destPixels, row * newWidth, atlas.getWidth());
            }
        }

        int[] overlayPixels = ((DataBufferInt) overlay.getRaster().getDataBuffer()).getData();
        for (int row = 0; row < overlay.getHeight(); row++) {
            System.arraycopy(overlayPixels, row * overlay.getWidth(), destPixels, (y + row) * newWidth + x, overlay.getWidth());
        }

        return newAtlas;
    }

    private static void saveOverlayAtlas(BufferedImage atlas, String outputDir, String jsonOutputDir, String atlasName, String jsonFileName) {
        File outputFolder = new File(outputDir);
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            System.err.println("Failed to create directory: " + outputFolder.getAbsolutePath());
            return;
        }

        File outputFile = new File(outputFolder, atlasName);

        try {
            System.out.println();
            System.out.println("Saving overlay atlas to: " + outputFile.getAbsolutePath());
            ImageIO.write(atlas, "png", outputFile);
            System.out.println("Overlay atlas saved successfully!");

            File jsonFolder = new File(jsonOutputDir);
            if (!jsonFolder.exists() && !jsonFolder.mkdirs()) {
                System.err.println("Failed to create directory: " + jsonFolder.getAbsolutePath());
                return;
            }
            saveOverlayCoordinatesJson(jsonFolder, jsonFileName);
        } catch (IOException exception) {
            System.err.println("Failed to save overlay atlas!");
            exception.printStackTrace();
        }
    }

    private static void saveOverlayCoordinatesJson(File outputFolder, String jsonFileName) {
        JsonArray jsonArray = new JsonArray();

        for (OverlayInfo info : overlayInfo) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", info.name);
            jsonObject.addProperty("x", info.getX());
            jsonObject.addProperty("y", info.getY());
            jsonObject.addProperty("size", OVERLAY_OUTPUT_SIZE);
            jsonObject.addProperty("type", "NORMAL"); // Default type
            jsonObject.addProperty("colorOptions", "leather_armor"); // Default color options
            jsonArray.add(jsonObject);
        }

        File jsonFile = new File(outputFolder, jsonFileName);

        try (FileWriter fileWriter = new FileWriter(jsonFile)) {
            DataSerialization.GSON.toJson(jsonArray, fileWriter);
            System.out.println("Overlay coordinates JSON file saved successfully!");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static class PNGFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && file.getName().toLowerCase().endsWith(".png");
        }
    }

    static class OverlayInfo {
        private final String name;
        private final BufferedImage image;
        private int x;
        private int y;

        public OverlayInfo(String name, BufferedImage image, int x, int y) {
            this.name = name;
            this.image = image;
            this.x = x;
            this.y = y;
        }

        public String getName() {
            return name;
        }

        public BufferedImage getImage() {
            return image;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }
}