package net.hypixel.nerdbot.generator.item.overlay.renderer;

import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.generator.item.overlay.OverlayConfig;
import net.hypixel.nerdbot.generator.item.overlay.OverlayRenderer;

import java.awt.image.BufferedImage;

/**
 * Dual layer overlay renderer - applies two sequential color applications.
 * <p>
 * This renderer applies the NORMAL overlay strategy twice with different colors,
 * first with the second color, then with the first color.
 */
public class DualLayerOverlayRenderer implements OverlayRenderer {

    private final NormalOverlayRenderer normalRenderer = new NormalOverlayRenderer();

    @Override
    public void render(BufferedImage target, BufferedImage source, OverlayConfig config) {
        int[] colors = config.getColors();

        // Need at least two colors for dual layer
        if (colors == null || colors.length < 2) {
            ImageUtil.copyImage(target, source);
            return;
        }

        // First pass: Apply second color (index 1)
        OverlayConfig layer1Config = new OverlayConfig.Builder()
            .withColors(colors[1])
            .build();
        normalRenderer.render(target, source, layer1Config);

        // Second pass: Apply first color (index 0) on top of first pass
        OverlayConfig layer2Config = new OverlayConfig.Builder()
            .withColors(colors[0])
            .build();
        normalRenderer.render(target, target, layer2Config);
    }

    @Override
    public String getTypeName() {
        return "DUAL_LAYER";
    }
}