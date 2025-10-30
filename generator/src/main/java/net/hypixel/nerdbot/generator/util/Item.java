package net.hypixel.nerdbot.generator.util;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.generator.util.overlay.Overlay;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;

public class Item {
    @Setter
    private static HashMap<String, Overlay> availableOverlays;
    @Setter
    private static Overlay smallEnchantGlint;
    @Setter
    private static Overlay largeEnchantGlint;

    /**
     * -- GETTER --
     * Gets the name of the sprite
     */
    @Getter
    private String name;
    /**
     * -- GETTER --
     * Gets the x coordinate in the sprite sheet
     */
    @Getter
    private int x;
    /**
     * -- GETTER --
     * Gets the y coordinate in the sprite sheet
     */
    @Getter
    private int y;
    /**
     * -- GETTER --
     * Gets the size of the image in the sprite sheet
     */
    @Getter
    private int size;
    private String[] overlays;

    /**
     * Applies image modifiers (overlay and enchantment glint) to the sprite
     *
     * @param image        the image to apply the modifiers to
     * @param extraDetails any extra details required for the modifiers
     */
    public void applyModifiers(BufferedImage image, String extraDetails) {
        if (extraDetails == null) {
            extraDetails = "";
        }

        String[] availableModifiers = extraDetails.split(",");
        if (overlays != null) {
            for (int i = 0; i < overlays.length; i++) {
                String color = availableModifiers.length > i ? availableModifiers[i] : "";
                availableOverlays.get(overlays[i]).applyOverlay(image, color);
            }
        }

        // applies the enchantment glint if "enchant" is present
        if (Arrays.stream(availableModifiers).anyMatch(element -> element.toLowerCase().contains("enchant"))) {
            (image.getWidth() == 16 ? smallEnchantGlint : largeEnchantGlint).applyOverlay(image, "#7c20ff");
        }
    }
}