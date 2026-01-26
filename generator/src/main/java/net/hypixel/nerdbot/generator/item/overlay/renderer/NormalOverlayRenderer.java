package net.hypixel.nerdbot.generator.item.overlay.renderer;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.generator.item.overlay.OverlayConfig;
import net.hypixel.nerdbot.generator.item.overlay.OverlayRenderer;

import java.awt.image.BufferedImage;
import java.util.HashMap;

/**
 * Normal overlay renderer - applies color tinting to the overlay.
 * <p>
 * This renderer takes a single color and tints the overlay image by
 * proportionally shifting the RGB values based on the source pixel colors.
 */
@Slf4j
public class NormalOverlayRenderer implements OverlayRenderer {

    @Override
    public void render(BufferedImage target, BufferedImage source, OverlayConfig config) {
        int[] colors = config.getColors();

        // If no colors provided, just copy the source to target
        if (colors == null || colors.length == 0) {
            ImageUtil.copyImage(target, source);
            return;
        }

        int color = colors[0];
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        int[] defaultColors = config.getDefaultColors();
        boolean hasDefaultColor = defaultColors != null && defaultColors.length > 0;

        // Skip de-tinting for grayscale images to avoid color bias
        if (hasDefaultColor && ImageUtil.isGrayscaleImage(source)) {
            hasDefaultColor = false;
        }

        final int defaultRed;
        final int defaultGreen;
        final int defaultBlue;

        if (hasDefaultColor) {
            int defaultColor = defaultColors[0];
            defaultRed = (defaultColor >> 16) & 0xFF;
            defaultGreen = (defaultColor >> 8) & 0xFF;
            defaultBlue = defaultColor & 0xFF;
            log.debug("De-tinting enabled: default RGB({}, {}, {}), target RGB({}, {}, {})",
                defaultRed, defaultGreen, defaultBlue, red, green, blue);
        } else {
            defaultRed = 255;
            defaultGreen = 255;
            defaultBlue = 255;
            log.debug("De-tinting disabled: target RGB({}, {}, {})", red, green, blue);
        }

        final boolean useDefaultColor = hasDefaultColor;
        HashMap<Integer, Integer> colorCache = new HashMap<>();

        for (int y = 0; y < target.getHeight(); y++) {
            for (int x = 0; x < target.getWidth(); x++) {
                int rgb = source.getRGB(x, y);

                // Skip transparent pixels
                if (((rgb >> 24) & 0xFF) == 0) {
                    continue;
                }

                // Apply color transformation with caching
                int newColor = colorCache.computeIfAbsent(rgb, value -> {
                    int alpha = (rgb >> 24) & 0xFF;
                    int srcR = (rgb >> 16) & 0xFF;
                    int srcG = (rgb >> 8) & 0xFF;
                    int srcB = rgb & 0xFF;

                    int finalR, finalG, finalB;
                    if (useDefaultColor) {
                        // De-tint then re-tint: (source / default) * desired
                        finalR = defaultRed > 0
                            ? (int) Math.min(255, Math.max(0, Math.round((srcR * red) / (double) defaultRed)))
                            : (int) Math.round((srcR / 255.0) * red);
                        finalG = defaultGreen > 0
                            ? (int) Math.min(255, Math.max(0, Math.round((srcG * green) / (double) defaultGreen)))
                            : (int) Math.round((srcG / 255.0) * green);
                        finalB = defaultBlue > 0
                            ? (int) Math.min(255, Math.max(0, Math.round((srcB * blue) / (double) defaultBlue)))
                            : (int) Math.round((srcB / 255.0) * blue);
                    } else {
                        // Simple multiplicative tint: (source / 255) * desired
                        finalR = (int) Math.round((srcR / 255.0) * red);
                        finalG = (int) Math.round((srcG / 255.0) * green);
                        finalB = (int) Math.round((srcB / 255.0) * blue);
                    }

                    return (alpha << 24) | (finalR << 16) | (finalG << 8) | finalB;
                });

                target.setRGB(x, y, newColor);
            }
        }
    }

    @Override
    public String getTypeName() {
        return "NORMAL";
    }
}