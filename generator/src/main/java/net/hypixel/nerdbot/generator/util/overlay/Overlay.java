package net.hypixel.nerdbot.generator.util.overlay;

import lombok.Getter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;

public abstract class Overlay {
    protected final BufferedImage overlay;
    @Getter
    private final String name;
    private final boolean applyIfNoColor;

    /***
     * Creates an Overlay object which applies color transformations onto an image
     *
     * @param name the name of the overlay
     * @param overlay the image of the overlay
     * @param applyIfNoColor if the overlay should be applied when no valid color is present
     */
    public Overlay(String name, BufferedImage overlay, boolean applyIfNoColor) {
        this.name = name;
        this.overlay = overlay;
        this.applyIfNoColor = applyIfNoColor;
    }

    public boolean applyIfNoColor() {
        return this.applyIfNoColor;
    }

    /***
     * Applies the overlay to the base image with the color provided
     *
     * @param image The image to apply the overlay onto
     * @param color The color to apply to the image
     */
    public abstract void applyOverlay(BufferedImage image, String color);

    /**
     * Attempts to parse the hex color, returning the default color if it couldn't be parsed
     *
     * @param color        The color to parse
     * @param defaultColor The color to display if it couldn't be parsed
     *
     * @return The resultant color
     */
    protected Color tryParseColor(String color, Color defaultColor) {
        Color finalColor;
        try {
            finalColor = Color.decode(color);
        } catch (NumberFormatException ignored) {
            finalColor = defaultColor;
        }

        return finalColor;
    }

    /**
     * Applies an image modifier onto the selected image
     *
     * @param image       the image the modifier is being applied to
     * @param sourceImage the overlay source image
     * @param color       the color the image should be shifted by
     */
    protected void applyColorModifierOnImage(BufferedImage image, BufferedImage sourceImage, Color color) {
        HashMap<Integer, Integer> colorMap = new HashMap<>();

        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();

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
                    int selectedRed = (int) Math.round((((rgb >> 16) & 0xff) / 255.0) * red);
                    int selectedGreen = (int) Math.round(((((rgb >> 8) & 0xff) / 255.0) * green));
                    int selectedBlue = (int) Math.round((((rgb & 0xff) / 255.0) * blue));
                    return (selectedAlpha << 24) | (selectedRed << 16) | (selectedGreen << 8) | selectedBlue;
                });
                image.setRGB(x, y, newColor);
            }
        }
    }
}