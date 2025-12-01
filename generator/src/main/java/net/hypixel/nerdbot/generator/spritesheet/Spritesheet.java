package net.hypixel.nerdbot.generator.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
 

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class Spritesheet {

    private static final Map<String, BufferedImage> IMAGE_MAP = new HashMap<>();
    private static final String ATLAS_PATH = "/minecraft/assets/spritesheets/minecraft_texture_atlas.png";
    private static final String COORDINATES_PATH = "/minecraft/assets/json/atlas_coordinates.json";

    private static BufferedImage textureAtlas;
    private static final Gson GSON = new Gson();

    static {
        try (InputStream atlasStream = Spritesheet.class.getResourceAsStream(ATLAS_PATH)) {
            if (atlasStream == null) {
                throw new IOException("Texture atlas image not found: " + ATLAS_PATH);
            }
            // Load the texture atlas image
            log.info("Loading texture atlas image");
            textureAtlas = ImageIO.read(atlasStream);
            log.info("Loaded texture atlas image (size: " + textureAtlas.getWidth() + "x" + textureAtlas.getHeight() + ")");
        } catch (IOException exception) {
            log.error("Failed to load texture atlas image", exception);
        }

        try (InputStream coordinatesStream = Spritesheet.class.getResourceAsStream(COORDINATES_PATH)) {
            if (coordinatesStream == null) {
                throw new IOException("Texture atlas coordinates file not found: " + COORDINATES_PATH);
            }
            log.info("Loading texture atlas coordinates from JSON file");

            // Load the texture atlas coordinates
            JsonArray jsonCoordinates = GSON.fromJson(new InputStreamReader(coordinatesStream), JsonArray.class);

            for (JsonElement jsonElement : jsonCoordinates) {
                JsonObject itemData = jsonElement.getAsJsonObject();
                String name = itemData.get("name").getAsString();
                int x = itemData.get("x").getAsInt();
                int y = itemData.get("y").getAsInt();
                int size = itemData.get("size").getAsInt();
                BufferedImage image = textureAtlas.getSubimage(x, y, size, size);

                log.info("Loaded texture: " + name + " at (" + x + ", " + y + ") with size " + size + "x" + size);
                IMAGE_MAP.put(name, image);
            }

            log.info("Finished loading texture atlas coordinates from JSON file (" + IMAGE_MAP.size() + " textures loaded)");
        } catch (IOException exception) {
            log.error("Failed to load texture atlas coordinates", exception);
        }
    }

    private Spritesheet() {
    }

    public static BufferedImage getTexture(String textureId) {
        return IMAGE_MAP.get(textureId);
    }

    public static List<Map.Entry<String, BufferedImage>> searchForTexture(String textureId) {
        return IMAGE_MAP.entrySet().stream()
            .filter(entry -> entry.getKey().contains(textureId))
            .collect(Collectors.toList());
    }

    public static Map<String, BufferedImage> getImageMap() {
        return IMAGE_MAP;
    }
}
