package net.hypixel.nerdbot.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.NerdBotApp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ItemSpritesheetGenerator {

    private static final int ATLAS_WIDTH = 1_024;

    private static final List<TextureInfo> textureInfo = new ArrayList<>();

    public static void main(String[] args) {
        String itemDir = "./src/main/resources/Minecraft/textures/item";
        String blockDir = "./src/main/resources/Minecraft/textures/block";
        String outputDir = "./src/main/resources/atlas";
        String atlasName = "minecraft_texture_atlas.png";
        String jsonFileName = "atlas_coordinates.json";

        List<BufferedImage> textures = new ArrayList<>();
        List<String> textureNames = new ArrayList<>();

        textures.addAll(loadTextures(itemDir, textureNames));
        textures.addAll(loadTextures(blockDir, textureNames));

        System.out.println("Loaded " + textures.size() + " textures.");

        BufferedImage atlas = createTextureAtlas(textures);
        saveTextureAtlas(atlas, outputDir, atlasName, jsonFileName, textureNames);
        System.out.println("Texture atlas generation complete!");
    }

    /**
     * Save the texture atlas to the given output directory
     *
     * @param inputDir     The input directory
     * @param textureNames The names of the textures
     *
     * @return The loaded textures as a list
     */
    private static List<BufferedImage> loadTextures(String inputDir, List<String> textureNames) {
        List<BufferedImage> textures = new ArrayList<>();
        File folder = new File(inputDir);
        File[] files = folder.listFiles(new PNGFileFilter());

        if (files != null) {
            System.out.println("Loading textures from: " + folder.getAbsolutePath());

            // Sort alphabetically
            Arrays.sort(files, Comparator.comparing(File::getName));

            // Load textures
            for (File file : files) {
                try {
                    BufferedImage texture = ImageIO.read(file);
                    System.out.println("Loaded texture: " + file.getName());
                    textures.add(texture);
                    textureNames.add(file.getName());
                    textureInfo.add(new TextureInfo(file.getName(), -1, -1));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return textures;
    }

    /**
     * Create a texture atlas from the given textures and the s
     *
     * @param textures The textures to add to the atlas
     *
     * @return The texture atlas image
     */
    private static BufferedImage createTextureAtlas(List<BufferedImage> textures) {
        int atlasHeight = 0;
        int x = 0;
        int y = 0;

        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, 1, BufferedImage.TYPE_INT_ARGB);

        for (BufferedImage texture : textures) {
            int frameWidth = 16;
            int frameHeight = Math.min(16, texture.getHeight());

            if (x + frameWidth > ATLAS_WIDTH) {
                // Move to the next row
                x = 0;
                y += atlasHeight;
                atlasHeight = 0;
            }

            atlasHeight = Math.max(atlasHeight, frameHeight);

            for (int yPos = 0; yPos < texture.getHeight(); yPos += 16) {
                BufferedImage frame = texture.getSubimage(0, yPos, frameWidth, Math.min(16, texture.getHeight() - yPos));

                atlas = extendTextureAtlas(atlas, x, y, frame);

                TextureInfo info = textureInfo.get(textures.indexOf(texture));
                info.setX(x);
                info.setY(y);

                System.out.println("Adding texture at position (x=" + info.getX() + ", y=" + info.getY() + ")");

                x += frameWidth;

                // Move to the next row
                if (x + frameWidth > ATLAS_WIDTH) {
                    x = 0;
                    y += atlasHeight;
                    atlasHeight = 0;
                }
            }
        }

        return atlas;
    }

    /**
     * Extends the texture atlas to fit the new texture at the specified position
     *
     * @param atlas   The texture atlas to extend
     * @param x       The x position of the new texture
     * @param y       The y position of the new texture
     * @param texture The new texture to add
     *
     * @return The extended texture atlas with the new texture added
     */
    private static BufferedImage extendTextureAtlas(BufferedImage atlas, int x, int y, BufferedImage texture) {
        int newWidth = Math.max(atlas.getWidth(), x + texture.getWidth());
        int newHeight = Math.max(atlas.getHeight(), y + texture.getHeight());

        BufferedImage newAtlas = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        newAtlas.getGraphics().drawImage(atlas, 0, 0, null);
        newAtlas.getGraphics().drawImage(texture, x, y, null);

        return newAtlas;
    }

    /**
     * Saves the texture atlas to the specified output directory
     *
     * @param atlas        The texture atlas to save
     * @param outputDir    The output directory to save the texture atlas to
     * @param atlasName    The name of the texture atlas file
     * @param jsonFileName The name of the JSON file containing the texture coordinates
     * @param textureNames The names of the textures in the atlas
     */
    private static void saveTextureAtlas(BufferedImage atlas, String outputDir, String atlasName, String jsonFileName, List<String> textureNames) {
        File outputFolder = new File(outputDir);
        outputFolder.mkdirs();

        File outputFile = new File(outputFolder, atlasName);

        // Save the texture atlas file
        try {
            System.out.println("Saving texture atlas to: " + outputFile.getAbsolutePath());
            ImageIO.write(atlas, "png", outputFile);
            System.out.println("Texture atlas saved successfully!");

            saveTextureCoordinatesJson(outputFolder, jsonFileName, textureNames);
        } catch (IOException e) {
            System.err.println("Failed to save texture atlas!");
            e.printStackTrace();
        }
    }

    /**
     * Saves the texture coordinates to a JSON file
     *
     * @param outputFolder The output folder to save the JSON file to
     * @param jsonFileName The name of the JSON file
     * @param textureNames The names of the textures in the atlas
     */
    private static void saveTextureCoordinatesJson(File outputFolder, String jsonFileName, List<String> textureNames) {
        JsonArray jsonArray = new JsonArray();

        // Append each TextureInfo object to the JSON file
        for (TextureInfo info : textureInfo) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", info.name.split("\\.")[0]);
            jsonObject.addProperty("x", info.x);
            jsonObject.addProperty("y", info.y);
            jsonArray.add(jsonObject);
        }

        File jsonFile = new File(outputFolder, jsonFileName);

        // Save the file
        try (FileWriter fileWriter = new FileWriter(jsonFile)) {
            NerdBotApp.GSON.toJson(jsonArray, fileWriter);
            System.out.println("Texture coordinates JSON file saved successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * A file filter for PNG files
     */
    private static class PNGFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && file.getName().toLowerCase().endsWith(".png");
        }
    }

    /**
     * A class containing information about a texture
     */
    static class TextureInfo {
        private final String name;
        private int x;
        private int y;

        public TextureInfo(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }

        public String getName() {
            return name;
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