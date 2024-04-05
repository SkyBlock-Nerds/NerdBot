package net.hypixel.nerdbot.util.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.generator.item.overlay.ItemOverlay;
import net.hypixel.nerdbot.generator.item.overlay.OverlayColorOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OverlaySheet {

    private static final Map<String, ItemOverlay[]> itemOverlays = new HashMap<>();

    private static BufferedImage smallEnchantGlint;
    private static BufferedImage largeEnchantGlint;

    static {
        try {
            // Load the overlay sprite sheet image
            BufferedImage overlaySpriteSheet = ImageIO.read(new File("src/main/resources/minecraft/spritesheets/overlays.png"));

            Gson gson = new Gson();
            // loading color choices for overlays into memory
            OverlayColorOptions[] overlayColorOptionsOptions = gson.fromJson(new FileReader("src/main/resources/minecraft/json/overlay_colors.json"), OverlayColorOptions[].class);
            HashMap<String, OverlayColorOptions> foundOverlayColors = new HashMap<>();
            for (OverlayColorOptions overlayColorOptions : overlayColorOptionsOptions) {
                foundOverlayColors.put(overlayColorOptions.getName(), overlayColorOptions);
            }

            // loading overlays and assigning their color choices and images
            ItemOverlay[] itemOverlayCoordinates = gson.fromJson(new FileReader("src/main/resources/minecraft/json/overlay_coordinates.json"), ItemOverlay[].class);
            HashMap<String, ItemOverlay> foundOverlays = new HashMap<>();
            for (ItemOverlay itemOverlay : itemOverlayCoordinates) {
                int imageDimensions = itemOverlay.isBig() ? 512 : 16;
                itemOverlay.setImage(overlaySpriteSheet.getSubimage(itemOverlay.getX(), itemOverlay.getY(), imageDimensions, imageDimensions));
                if (itemOverlay.getName().contains("enchant")) {
                    if (itemOverlay.getName().contains("big")) {
                        largeEnchantGlint = itemOverlay.getImage();
                    } else if (itemOverlay.getName().contains("small")) {
                        smallEnchantGlint = itemOverlay.getImage();
                    }
                    continue;
                }

                itemOverlay.setOverlayColorOptions(foundOverlayColors.get(itemOverlay.getColorOptions()));
                foundOverlays.put(itemOverlay.getName(), itemOverlay);
            }

            // assigning the items to their overlay objects
            JsonArray jsonBindings = gson.fromJson(new FileReader("src/main/resources/minecraft/json/item_overlay_binding.json"), JsonArray.class);
            for (JsonElement jsonElement : jsonBindings) {
                JsonObject itemData = jsonElement.getAsJsonObject();
                String itemName = itemData.get("name").getAsString();
                JsonArray overlays = itemData.get("overlays").getAsJsonArray();

                ItemOverlay[] boundItemOverlays = new ItemOverlay[overlays.size()];
                for (int i = 0; i < overlays.size(); i++) {
                    boundItemOverlays[i] = foundOverlays.get(overlays.get(i).getAsString());
                }

                itemOverlays.put(itemName, boundItemOverlays);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemOverlay[] getOverlay(String overlayId) {
        return itemOverlays.get(overlayId);
    }

    public static BufferedImage getEnchantGlint(boolean isSmall) {
        return isSmall ? smallEnchantGlint : largeEnchantGlint;
    }

}
