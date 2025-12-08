package net.hypixel.nerdbot.generator.item.overlay;

import java.awt.image.BufferedImage;

/**
 * Interface to render overlays onto items.
 * <p>
 * Implementations handle different overlay rendering strategies, for example,
 * - NORMAL: Single color tinting
 * - MAPPED: Color-indexed mapping
 * - DUAL_LAYER: Two-color applications
 * <p>
 * etc.
 */
public interface OverlayRenderer {

    /**
     * Render an overlay onto the target image.
     *
     * @param target  The image to render onto
     * @param overlay The overlay image to render
     * @param config  Configuration for this overlay (colors, options, etc.)
     */
    void render(BufferedImage target, BufferedImage overlay, OverlayConfig config);

    /**
     * Get the type name for this renderer.
     *
     * @return The renderer type name
     */
    String getTypeName();
}