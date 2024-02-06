package net.hypixel.nerdbot.util.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Spritesheet {

    private static BufferedImage textureAtlas;
    @Getter
    private static final Map<String, BufferedImage> items = new HashMap<>();

    static {
        try {
            // Load the texture atlas image
            textureAtlas = ImageIO.read(new File("src/main/resources/minecraft/spritesheets/minecraft_texture_atlas.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            // Load the texture atlas coordinates
            Gson gson = new Gson();
            JsonArray jsonCoordinates = gson.fromJson(new FileReader("src/main/resources/minecraft/spritesheets/atlas_coordinates.json"), JsonArray.class);

            for (JsonElement jsonElement : jsonCoordinates) {
                JsonObject itemData = jsonElement.getAsJsonObject();
                String name = itemData.get("name").getAsString();
                int x = itemData.get("x").getAsInt();
                int y = itemData.get("y").getAsInt();
                int size = itemData.get("size").getAsInt();
                BufferedImage image = textureAtlas.getSubimage(x, y, size, size);

                items.put(name, image);
            }
        } catch (FileNotFoundException exception) {
            exception.printStackTrace();
        }
    }

    private Spritesheet() {
    }

    public static BufferedImage getTexture(String textureId) {
        return items.get(textureId);
    }
}
