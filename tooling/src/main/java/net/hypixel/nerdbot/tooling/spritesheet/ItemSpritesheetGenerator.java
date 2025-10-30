package net.hypixel.nerdbot.tooling.spritesheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.generator.GeneratorBuilder;

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

public final class ItemSpritesheetGenerator {

    private static final int ATLAS_WIDTH = 1_024 * 8;

    private static final List<TextureInfo> TEXTURE_INFO = new ArrayList<>();

    private ItemSpritesheetGenerator() {
    }

    public static void main(String[] args) {
        String path = "./src/main/resources/minecraft/assets/textures/items";
        String outputDir = "./src/main/resources/minecraft/assets/spritesheets";
        String atlasName = "minecraft_texture_atlas.png";
        String jsonFileName = "atlas_coordinates.json";

        List<String> textureNames = new ArrayList<>();
        TEXTURE_INFO.addAll(loadTextures(path, textureNames));

        System.out.println("Loaded " + TEXTURE_INFO.size() + " textures.");

        BufferedImage atlas = createTextureAtlas();
        saveTextureAtlas(atlas, outputDir, atlasName, jsonFileName);
        System.out.println("Texture atlas generation complete!");
    }

    private static List<TextureInfo> loadTextures(String inputDir, List<String> textureNames) {
        List<TextureInfo> textureInfoList = new ArrayList<>();
        File folder = new File(inputDir);
        File[] files = folder.listFiles(new PNGFileFilter());

        if (files != null) {
            System.out.println("Loading textures from: " + folder.getAbsolutePath());
            Arrays.sort(files, Comparator.comparing(File::getName));

            for (File file : files) {
                try {
                    BufferedImage texture = ImageIO.read(file);
                    System.out.println("Loaded texture: " + file.getName());

                    if (texture.getWidth() != GeneratorBuilder.IMAGE_WIDTH || texture.getHeight() != GeneratorBuilder.IMAGE_HEIGHT) {
                        System.out.println("Resizing texture: " + file.getName() + " to " + GeneratorBuilder.IMAGE_WIDTH + "x" + GeneratorBuilder.IMAGE_HEIGHT);
                        texture = resizeImage(texture);
                    }

                    TextureInfo textureInfo = new TextureInfo(file.getName(), texture, -1, -1);
                    textureInfoList.add(textureInfo);
                    textureNames.add(file.getName());
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }

        return textureInfoList;
    }

    private static BufferedImage resizeImage(BufferedImage originalImage) {
        BufferedImage resizedImage = new BufferedImage(GeneratorBuilder.IMAGE_WIDTH, GeneratorBuilder.IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics2D.drawImage(originalImage, 0, 0, GeneratorBuilder.IMAGE_WIDTH, GeneratorBuilder.IMAGE_HEIGHT, null);
        graphics2D.dispose();
        return resizedImage;
    }

    private static BufferedImage createTextureAtlas() {
        int atlasHeight = 0;
        int x = 0;
        int y = 0;

        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, 1, BufferedImage.TYPE_INT_ARGB);

        int totalTextures = TEXTURE_INFO.size();
        double progressStep = 1.0 / totalTextures;
        double progress = 0.0;

        for (TextureInfo textureInfo : TEXTURE_INFO) {
            int frameWidth = GeneratorBuilder.IMAGE_WIDTH;

            if (x + frameWidth > ATLAS_WIDTH) {
                x = 0;
                y += atlasHeight;
            }

            atlasHeight = GeneratorBuilder.IMAGE_HEIGHT;

            for (int yPos = 0; yPos < textureInfo.getImage().getHeight(); yPos += GeneratorBuilder.IMAGE_HEIGHT) {
                BufferedImage frame = textureInfo.getImage()
                    .getSubimage(0, yPos, frameWidth, Math.min(GeneratorBuilder.IMAGE_HEIGHT, textureInfo.getImage().getHeight() - yPos));

                atlas = extendTextureAtlas(atlas, x, y, frame);

                textureInfo.setX(x);
                textureInfo.setY(y);

                x += frameWidth;
            }

            progress += progressStep;
            System.out.print("\r");
            System.out.printf("Texture atlas creation progress: %.2f%%", progress * 100);
        }

        return atlas;
    }

    private static BufferedImage extendTextureAtlas(BufferedImage atlas, int x, int y, BufferedImage texture) {
        int newWidth = Math.max(atlas.getWidth(), x + texture.getWidth());
        int newHeight = Math.max(atlas.getHeight(), y + texture.getHeight());

        BufferedImage newAtlas = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        newAtlas.getGraphics().drawImage(atlas, 0, 0, null);
        newAtlas.getGraphics().drawImage(texture, x, y, null);

        return newAtlas;
    }

    private static void saveTextureAtlas(BufferedImage atlas, String outputDir, String atlasName, String jsonFileName) {
        File outputFolder = new File(outputDir);
        outputFolder.mkdirs();

        File outputFile = new File(outputFolder, atlasName);

        try {
            System.out.println();
            System.out.println("Saving texture atlas to: " + outputFile.getAbsolutePath());
            ImageIO.write(atlas, "png", outputFile);
            System.out.println("Texture atlas saved successfully!");

            saveTextureCoordinatesJson(outputFolder, jsonFileName);
        } catch (IOException exception) {
            System.err.println("Failed to save texture atlas!");
            exception.printStackTrace();
        }
    }

    private static void saveTextureCoordinatesJson(File outputFolder, String jsonFileName) {
        JsonArray jsonArray = new JsonArray();

        for (TextureInfo info : TEXTURE_INFO) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", info.name.split("\\.")[0]);
            jsonObject.addProperty("x", info.getX());
            jsonObject.addProperty("y", info.getY());
            jsonArray.add(jsonObject);
        }

        File jsonFile = new File(outputFolder, jsonFileName);

        try (FileWriter fileWriter = new FileWriter(jsonFile)) {
            BotEnvironment.GSON.toJson(jsonArray, fileWriter);
            System.out.println("Texture coordinates JSON file saved successfully!");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static final class PNGFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && file.getName().toLowerCase().endsWith(".png");
        }
    }

    private static final class TextureInfo {
        private final String name;
        private final BufferedImage image;
        private int x;
        private int y;

        private TextureInfo(String name, BufferedImage image, int x, int y) {
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