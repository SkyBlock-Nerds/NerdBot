package net.hypixel.nerdbot.generator.spritesheet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.generator.item.overlay.ItemOverlay;
import net.hypixel.nerdbot.generator.item.overlay.OverlayColorOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class OverlaySheet {

    private static final Map<String, ItemOverlay[]> itemOverlays = new HashMap<>();

    private static BufferedImage smallEnchantGlint;
    private static BufferedImage largeEnchantGlint;

    static {
        try (InputStream overlayStream = OverlaySheet.class.getResourceAsStream("/minecraft/spritesheets/overlays.png")) {
            if (overlayStream == null) {
                throw new IOException("Overlay sprite sheet image not found: /minecraft/spritesheets/overlays.png");
            }
            // Load the overlay sprite sheet image
            BufferedImage overlaySpriteSheet = ImageIO.read(overlayStream);

            Gson gson = new Gson();
            // loading color choices for overlays into memory
            try (InputStream colorOptionsStream = OverlaySheet.class.getResourceAsStream("/minecraft/json/overlay_colors.json")) {
                if (colorOptionsStream == null) {
                    throw new IOException("Overlay colors JSON file not found: /minecraft/json/overlay_colors.json");
                }
                OverlayColorOptions[] overlayColorOptionsOptions = gson.fromJson(new InputStreamReader(colorOptionsStream), OverlayColorOptions[].class);
                HashMap<String, OverlayColorOptions> foundOverlayColors = new HashMap<>();
                for (OverlayColorOptions overlayColorOptions : overlayColorOptionsOptions) {
                    foundOverlayColors.put(overlayColorOptions.getName(), overlayColorOptions);
                }

                // loading overlays and assigning their color choices and images
                try (InputStream overlayCoordinatesStream = OverlaySheet.class.getResourceAsStream("/minecraft/json/overlay_coordinates.json")) {
                    if (overlayCoordinatesStream == null) {
                        throw new IOException("Overlay coordinates JSON file not found: /minecraft/json/overlay_coordinates.json");
                    }
                    ItemOverlay[] itemOverlayCoordinates = gson.fromJson(new InputStreamReader(overlayCoordinatesStream), ItemOverlay[].class);
                    HashMap<String, ItemOverlay> foundOverlays = new HashMap<>();
                    for (ItemOverlay itemOverlay : itemOverlayCoordinates) {
                        int imageDimensions = itemOverlay.isBig() ? SpritesheetGenerator.IMAGE_WIDTH : 16;
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
                    try (InputStream itemOverlayBindingStream = OverlaySheet.class.getResourceAsStream("/minecraft/json/item_overlay_binding.json")) {
                        if (itemOverlayBindingStream == null) {
                            throw new IOException("Item overlay binding JSON file not found: /minecraft/json/item_overlay_binding.json");
                        }
                        JsonArray jsonBindings = gson.fromJson(new InputStreamReader(itemOverlayBindingStream), JsonArray.class);
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
                    }
                }
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
