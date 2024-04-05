package net.hypixel.nerdbot.generator.item.overlay;

import java.awt.image.BufferedImage;
import java.util.HashMap;

public enum OverlayType {
    NORMAL, MAPPED, DUAL_LAYER;

    OverlayType() {
    }

    public static void normalOverlay(BufferedImage targetImage, BufferedImage sourceImage, int color) {
        HashMap<Integer, Integer> colorMap = new HashMap<>();
        int red = ((color >> 16) & 0xff);
        int green = ((color >> 8) & 0xff);
        int blue = (color) & 0xff;

        for (int y = 0; y < targetImage.getHeight(); y++) {
            for (int x = 0; x < targetImage.getWidth(); x++) {
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
                targetImage.setRGB(x, y, newColor);
            }
        }
    }

    public static void mappedOverlay(BufferedImage targetImage, BufferedImage overlay, HashMap<Integer, Integer> colorBinding, int[] colors) {
        for (int y = 0; y < targetImage.getHeight(); y++) {
            for (int x = 0; x < targetImage.getWidth(); x++) {
                // checking if the pixel has a color
                int rgb = overlay.getRGB(x, y);
                if (((rgb >> 24) & 0xff) == 0) {
                    continue;
                }

                int colorIndex = colorBinding.get(rgb);
                targetImage.setRGB(x, y, colors[colorIndex]);
            }
        }
    }
}
