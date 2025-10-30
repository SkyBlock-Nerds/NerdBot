package net.hypixel.nerdbot.generator.util.overlay;

import java.awt.*;
import java.awt.image.BufferedImage;

public class EnchantGlintOverlay extends Overlay {

    /***
     * Creates an Overlay object which applies an enchant glint onto an image
     *
     * @param name the name of the overlay
     * @param overlay the image of the overlay
     * @param applyIfNoColor if the overlay should be applied when no valid color is present
     */
    public EnchantGlintOverlay(String name, BufferedImage overlay, boolean applyIfNoColor) {
        super(name, overlay, applyIfNoColor);
    }

    @Override
    public void applyOverlay(BufferedImage image, String color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 24) & 0xff) == 0) {
                    continue;
                }

                // adding the two colors together
                int enchantmentGlintRGB = this.overlay.getRGB(x, y);
                int r = ((enchantmentGlintRGB >> 16) & 0xff) + ((rgb >> 16) & 0xff);
                int g = ((enchantmentGlintRGB >> 8) & 0xff) + ((rgb >> 8) & 0xff);
                int b = (enchantmentGlintRGB & 0xff) + (rgb & 0xff);
                Color finalColor = new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));

                image.setRGB(x, y, finalColor.getRGB());
            }
        }
    }
}