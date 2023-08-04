package net.hypixel.nerdbot.util.generator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;

public enum Overlay {
    BANNER_OVERLAY(0, 0, new Color(255, 255, 255)),
    ENCHANT_GLINT_OVERLAY(512, 0, new Color(124, 32, 255)),
    FIREWORKS_CHARGE_OVERLAY(1024, 0, new Color(255, 255, 255)),
    LEATHER_BOOTS_OVERLAY(0, 512, new Color(160, 101, 63)),
    LEATHER_CHESTPLATE_OVERLAY(512, 512, new Color(160, 101, 63)),
    LEATHER_HELMET_OVERLAY(1024, 512, new Color(160, 101, 63)),
    LEATHER_LEGGINGS_OVERLAY(0, 1024, new Color(160, 101, 63)),
    POTION_OVERLAY(512, 1024, new Color(55, 93, 198)),
    SPAWN_EGG_OVERLAY(1024, 1024, new Color(255, 255, 255), new Color(255, 255, 255));

    private final int x;
    private final int y;
    private final Color defaultOverlayColor;
    private final Color defaultBaseColor;

    private BufferedImage overlayImage = null;

    Overlay(int x, int y, Color defaultOverlayColor, Color defaultBaseColor) {
        this.x = x;
        this.y = y;
        this.defaultBaseColor = defaultBaseColor;
        this.defaultOverlayColor = defaultOverlayColor;
    }

    Overlay(int x, int y, Color defaultOverlayColor) {
        this(x, y, defaultOverlayColor, null);
    }

    /**
     * Gets the x coordinate in the overlay sprite sheet
     *
     * @return the x coordinate of the sprite
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the y coordinate in the overlay sprite sheet
     *
     * @return the y coordinate of the sprite
     */
    public int getY() {
        return y;
    }

    /**
     * Sets the overlay image from the overlay sprite sheet
     *
     * @param image the overlay sprite
     */
    public void setOverlayImage(BufferedImage image) {
        this.overlayImage = image;
    }

    /**
     * Gets the number of parameters that this overlay accepts
     *
     * @return the number of parameters the overlay accepts (0, 1 or 2)
     */
    public int acceptedParameters() {
        return (this.defaultBaseColor != null ? 1 : 0) + (this.defaultOverlayColor != null ? 1 : 0);
    }

    /**
     * Applies an image modifier onto the selected image
     *
     * @param image         the image the modifier is being applied to
     * @param sourceImage   the overlay source image
     * @param color         the color the image should be shifted by
     */
    private void applyModifierOnImage(BufferedImage image, BufferedImage sourceImage, Color color) {
        HashMap<Integer, Integer> colorMap = new HashMap<>();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                // checking if the pixel has a color
                int rgb = sourceImage.getRGB(x, y);
                if (rgb == 0) {
                    continue;
                }

                // shifting the color based on the color supplied
                int newColor = colorMap.computeIfAbsent(rgb, value -> {
                    int selectedAlpha = (rgb >> 24) & 0xff;
                    int selectedRed = (int) Math.round((((rgb >> 16) & 0xff) / 255.0) * color.getRed());
                    int selectedGreen = (int) Math.round(((((rgb >> 8) & 0xff) / 255.0) * color.getGreen()));
                    int selectedBlue = (int) Math.round((((rgb & 0xff) / 255.0) * color.getBlue()));
                    return (selectedAlpha << 24) | (selectedRed << 16) | (selectedGreen << 8) | selectedBlue;
                });
                image.setRGB(x, y, newColor);
            }
        }
    }

    /**
     * Applies the overlay to the original image
     *
     * @param image     the image the overlay should be applied to
     * @param color     the color the overlay image should have applied to it
     */
    public void applyOverlayColor(BufferedImage image, Color color) {
        // setting the color to the default value if a color wasn't supplied
        if (color == null) {
            color = defaultOverlayColor;
        }
        applyModifierOnImage(image, overlayImage, color);
    }

    public void applyBaseColor(BufferedImage image, Color color) {
        // checking if the base color can be modified
        if (defaultBaseColor == null) {
            return;
        }
        if (color == null) {
            color = defaultBaseColor;
        }
        applyModifierOnImage(image, image, color);
    }

    /**
     * Applies the enchantment glint onto the image provided
     *
     * @param image the image to have the glint applied
     */
    public static void applyEnchantOverlay(BufferedImage image) {
        BufferedImage overlayImage = ENCHANT_GLINT_OVERLAY.overlayImage;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 24) & 0xff) == 0)
                    continue;

                // adding the two colors together
                int enchantmentGlintRGB = overlayImage.getRGB(x, y);
                int r = ((enchantmentGlintRGB >> 16) & 0xff) + ((rgb >> 16) & 0xff);
                int g = ((enchantmentGlintRGB >> 8) & 0xff) + ((rgb >> 8) & 0xff);
                int b = (enchantmentGlintRGB & 0xff) + (rgb & 0xff);
                Color finalColor = new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));

                image.setRGB(x, y, finalColor.getRGB());
            }
        }
    }
}
