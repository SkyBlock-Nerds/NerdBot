package net.hypixel.nerdbot.util.generator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class Item {
    private String name;
    private int x;
    private int y;
    private Overlay overlay;

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
     * Gets the overlay that should be applied to this item
     *
     * @return the overlay for modifiers
     */
    public Overlay getOverlay() {
        return this.overlay;
    }

    /**
     * Applies image modifiers (overlay and enchantment glint) to the sprite
     *
     * @param image         the image to apply the modifiers to
     * @param extraDetails  any extra details required for the modifiers
     */
    public void applyModifiers(BufferedImage image, String[] extraDetails) {
        if (extraDetails == null) {
            extraDetails = new String[] {};
        }

        if (overlay != null) {
            Color overlayColor = null;
            Color baseColor = null;
            if (extraDetails.length >= 1) {
                try {
                    overlayColor = Color.decode(extraDetails[0]);
                } catch (NumberFormatException ignored) {}
            }
            if (extraDetails.length >= 2) {
                try {
                    baseColor = Color.decode(extraDetails[1]);
                } catch (NumberFormatException ignored) {}
            }

            overlay.applyBaseColor(image, baseColor);
            overlay.applyOverlayColor(image, overlayColor);
        }

        // applys
        if (Arrays.stream(extraDetails).anyMatch(element -> element.equalsIgnoreCase("enchant"))) {
            Overlay.applyEnchantOverlay(image);
        }
    }
}