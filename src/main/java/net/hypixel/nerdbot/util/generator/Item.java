package net.hypixel.nerdbot.util.generator;

import net.hypixel.nerdbot.util.generator.overlay.Overlay;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;

public class Item {
    private static HashMap<String, Overlay> availableOverlays;
    private static Overlay smallEnchantGlint;
    private static Overlay largeEnchantGlint;

    private String name;
    private int x;
    private int y;
    private int size;
    private String[] overlays;

    /**
     * Gets the name of the sprite
     *
     * @return the sprite name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the x coordinate in the sprite sheet
     *
     * @return the x coordinate of the sprite
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the y coordinate in the sprite sheet
     *
     * @return the y coordinate of the sprite
     */
    public int getY() {
        return y;
    }

    /**
     * Gets the size of the image in the sprite sheet
     *
     * @return the size of the image in the sprite sheet
     */
    public int getSize() {
        return this.size;
    }

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

    public static void setAvailableOverlays(HashMap<String, Overlay> createdOverlays) {
        availableOverlays = createdOverlays;
    }

    public static void setSmallEnchantGlint(Overlay enchantGlint) {
        smallEnchantGlint = enchantGlint;
    }

    public static void setLargeEnchantGlint(Overlay enchantGlint) {
        largeEnchantGlint = enchantGlint;
    }
}