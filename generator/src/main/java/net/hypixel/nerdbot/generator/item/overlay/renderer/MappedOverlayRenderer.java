package net.hypixel.nerdbot.generator.item.overlay.renderer;

import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.hypixel.nerdbot.generator.item.overlay.OverlayConfig;
import net.hypixel.nerdbot.generator.item.overlay.OverlayRenderer;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Mapped overlay renderer - uses color index mapping for armor trims.
 * <p>
 * This renderer maps source pixel colors to specific indices in a color array,
 * allowing multiple color channels per overlay (e.g., gold trim on armor).
 */
public class MappedOverlayRenderer implements OverlayRenderer {

    @Override
    public void render(BufferedImage target, BufferedImage overlay, OverlayConfig config) {
        int[] colors = config.getColors();
        Map<Integer, Integer> colorMap = config.getColorMap();

        // If no colors or mapping provided, just copy overlay to target
        if (colors == null || colorMap == null) {
            ImageUtil.copyImage(target, overlay);
            return;
        }

        for (int y = 0; y < target.getHeight(); y++) {
            for (int x = 0; x < target.getWidth(); x++) {
                int rgb = overlay.getRGB(x, y);

                // Skip transparent pixels
                if (((rgb >> 24) & 0xFF) == 0) {
                    continue;
                }

                // Map source color to palette index
                Integer colorIndex = colorMap.get(rgb);
                if (colorIndex != null && colorIndex < colors.length) {
                    target.setRGB(x, y, colors[colorIndex]);
                }
            }
        }
    }

    @Override
    public String getTypeName() {
        return "MAPPED";
    }
}