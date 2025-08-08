package net.hypixel.nerdbot.generator.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.NerdBotApp;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class OverlayGenerator {

    public static final int OVERLAY_OUTPUT_SIZE = SpritesheetGenerator.IMAGE_HEIGHT; // Match item size from actual spritesheet
    private static final int ATLAS_WIDTH = 1_024;
    
    private static final List<OverlayInfo> overlayInfo = new ArrayList<>();

    public static void main(String[] args) {
        String path = "./src/main/resources/minecraft/textures/overlays";
        String outputDir = "./src/main/resources/minecraft/spritesheets";
        String atlasName = "overlays.png";
        String jsonFileName = "overlay_coordinates.json";

        List<String> overlayNames = new ArrayList<>();
        overlayInfo.addAll(loadOverlays(path, overlayNames));

        System.out.println("Loaded " + overlayInfo.size() + " overlays.");

        BufferedImage atlas = createOverlayAtlas();
        saveOverlayAtlas(atlas, outputDir, atlasName, jsonFileName);
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

    private static BufferedImage resizeOverlay(BufferedImage originalOverlay) {
        BufferedImage resizedOverlay = new BufferedImage(OVERLAY_OUTPUT_SIZE, OVERLAY_OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = resizedOverlay.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics2D.drawImage(originalOverlay, 0, 0, OVERLAY_OUTPUT_SIZE, OVERLAY_OUTPUT_SIZE, null);
        graphics2D.dispose();
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

    private static BufferedImage extendOverlayAtlas(BufferedImage atlas, int x, int y, BufferedImage overlay) {
        int newWidth = Math.max(atlas.getWidth(), x + overlay.getWidth());
        int newHeight = Math.max(atlas.getHeight(), y + overlay.getHeight());

        BufferedImage newAtlas = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        newAtlas.getGraphics().drawImage(atlas, 0, 0, null);
        newAtlas.getGraphics().drawImage(overlay, x, y, null);

        return newAtlas;
    }

    private static void saveOverlayAtlas(BufferedImage atlas, String outputDir, String atlasName, String jsonFileName) {
        File outputFolder = new File(outputDir);
        outputFolder.mkdirs();

        File outputFile = new File(outputFolder, atlasName);

        try {
            System.out.println();
            System.out.println("Saving overlay atlas to: " + outputFile.getAbsolutePath());
            ImageIO.write(atlas, "png", outputFile);
            System.out.println("Overlay atlas saved successfully!");

            File jsonFolder = new File("./src/main/resources/minecraft/json");
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
            jsonObject.addProperty("isBig", false); // All overlays are now standardized
            jsonObject.addProperty("colorOptions", "leather_armor"); // Default color options
            jsonArray.add(jsonObject);
        }

        File jsonFile = new File(outputFolder, jsonFileName);

        try (FileWriter fileWriter = new FileWriter(jsonFile)) {
            NerdBotApp.GSON.toJson(jsonArray, fileWriter);
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

    @Getter
    static class OverlayInfo {
        private final String name;
        private final BufferedImage image;
        @Setter
        private int x;
        @Setter
        private int y;

        public OverlayInfo(String name, BufferedImage image, int x, int y) {
            this.name = name;
            this.image = image;
            this.x = x;
            this.y = y;
        }
    }
}