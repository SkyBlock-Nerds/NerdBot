package net.hypixel.nerdbot.generator.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class Spritesheet {

    private static final Map<String, BufferedImage> IMAGE_MAP = new HashMap<>();
    private static final String FOLDER_PATH = "src/main/resources/minecraft";

    private static BufferedImage textureAtlas;

    static {
        try {
            // Load the texture atlas image
            System.out.println("Loading texture atlas image");
            textureAtlas = ImageIO.read(new File(FOLDER_PATH + "/spritesheets/minecraft_texture_atlas.png"));
            log.info("Loaded texture atlas image");
        } catch (IOException exception) {
            log.error("Failed to load texture atlas image", exception);
        }

        try {
            log.info("Loading texture atlas coordinates from JSON file");

            // Load the texture atlas coordinates
            Gson gson = new Gson();
            JsonArray jsonCoordinates = gson.fromJson(new FileReader(FOLDER_PATH + "/json/atlas_coordinates.json"), JsonArray.class);

            for (JsonElement jsonElement : jsonCoordinates) {
                JsonObject itemData = jsonElement.getAsJsonObject();
                String name = itemData.get("name").getAsString();
                int x = itemData.get("x").getAsInt();
                int y = itemData.get("y").getAsInt();
                int size = itemData.get("size").getAsInt();
                BufferedImage image = textureAtlas.getSubimage(x, y, size, size);

                log.debug("Loaded texture: " + name + " at (" + x + ", " + y + ") with size " + size + "x" + size);
                IMAGE_MAP.put(name, image);
            }

            log.info("Finished loading texture atlas coordinates from JSON file (" + IMAGE_MAP.size() + " textures loaded)");
        } catch (FileNotFoundException exception) {
            log.error("Failed to load texture atlas coordinates", exception);
        }
    }

    private Spritesheet() {
    }

    public static BufferedImage getTexture(String textureId) {
        return IMAGE_MAP.get(textureId);
    }

    public static Map<String, BufferedImage> getImageMap() {
        return IMAGE_MAP;
    }
}
