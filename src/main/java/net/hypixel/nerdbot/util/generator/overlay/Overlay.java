package net.hypixel.nerdbot.util.generator.overlay;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;

public abstract class Overlay {
    private final String name;
    private int x;
    private int y;
    protected final BufferedImage overlay;
    private final boolean applyIfNoColor;

    public Overlay(String name, BufferedImage overlay, boolean applyIfNoColor) {
        this.name = name;
        this.overlay = overlay;
        this.applyIfNoColor = applyIfNoColor;
    }

    public String getName() {
        return this.name;
    }

    public boolean applyIfNoColor() {
        return this.applyIfNoColor;
    }

    public abstract void applyColor(BufferedImage image, String color);

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

    @Override
    public String toString() {
        return "Overlay{" +
            "name='" + name + '\'' +
            ", x=" + x +
            ", y=" + y +
            ", overlay=" + overlay +
            ", applyIfNoColor=" + applyIfNoColor +
            '}';
    }
}
