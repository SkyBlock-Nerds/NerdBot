package net.hypixel.nerdbot.generator.effect.impl;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.effect.EffectContext;
import net.hypixel.nerdbot.generator.effect.EffectResult;
import net.hypixel.nerdbot.generator.effect.ImageEffect;
import net.hypixel.nerdbot.generator.item.overlay.ItemOverlay;
import net.hypixel.nerdbot.generator.item.overlay.OverlayConfig;
import net.hypixel.nerdbot.generator.item.overlay.OverlayRenderer;
import net.hypixel.nerdbot.generator.item.overlay.OverlayRendererRegistry;
import net.hypixel.nerdbot.generator.spritesheet.OverlayLoader;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Overlay application effect - applies item overlays (armor, potions, etc).
 * <p>
 * This effect handles applying colored overlays to items, with special logic
 * for colorable armor vs. other items.
 */
@Slf4j
public class OverlayApplicationEffect implements ImageEffect {

    private final OverlayLoader overlayLoader;

    /**
     * Create overlay application effect with the given overlay loader.
     *
     * @param overlayLoader Loader for overlay data
     */
    public OverlayApplicationEffect(OverlayLoader overlayLoader) {
        if (overlayLoader == null) {
            throw new IllegalArgumentException("OverlayLoader cannot be null");
        }
        this.overlayLoader = overlayLoader;
    }

    @Override
    public EffectResult apply(EffectContext context) {
        String itemId = context.getItemId().toLowerCase();
        ItemOverlay itemOverlay = overlayLoader.getOverlay(itemId);

        if (itemOverlay == null) {
            return EffectResult.single(context.getImage());
        }

        // Use color if provided, otherwise fall back to data
        String colorOption = context.getMetadata("color", String.class).orElse(null);
        String dataOption = colorOption != null && !colorOption.isBlank()
            ? colorOption
            : context.getMetadata("data", String.class).orElse("");
        log.debug("Applying overlay to item '{}' with color option '{}'", itemId, dataOption);

        BufferedImage result = applyOverlay(context.getImage(), itemOverlay, dataOption);
        return EffectResult.single(result);
    }

    private BufferedImage applyOverlay(BufferedImage baseImage, ItemOverlay overlay, String dataOption) {
        BufferedImage overlayImage = overlay.getImage();

        // Get the appropriate renderer for this overlay type
        OverlayRenderer renderer = OverlayRendererRegistry.getRendererOrThrow(overlay.getType());

        // Build overlay configuration
        int[] colors = overlay.getOverlayColorOptions() != null
            ? overlay.getOverlayColorOptions().getColorsFromOption(dataOption)
            : null;

        if (colors == null || colors.length == 0) {
            log.warn("No colors parsed from dataOption '{}' for overlay '{}'", dataOption, overlay.getName());
        }

        // Determine which layer to color based on colorMode
        String colorMode = overlay.getColorMode();
        boolean isOverlayMode = "OVERLAY".equalsIgnoreCase(colorMode);

        // Only use de-tinting (defaultColors) for BASE mode
        // OVERLAY mode (potions, etc.) should use simple multiplication
        OverlayConfig config = new OverlayConfig.Builder()
            .withColors(colors)
            .withDefaultColors(!isOverlayMode && overlay.getOverlayColorOptions() != null
                ? overlay.getOverlayColorOptions().getDefaultColors()
                : null)
            .withColorMap(overlay.getOverlayColorOptions() != null
                ? overlay.getOverlayColorOptions().getMap()
                : null)
            .withDataOption(dataOption)
            .build();

        BufferedImage finalBase;
        BufferedImage finalOverlay;

        if (isOverlayMode) {
            // Color the overlay (e.g., potion liquid)
            finalBase = baseImage;
            finalOverlay = new BufferedImage(overlayImage.getWidth(), overlayImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            renderer.render(finalOverlay, overlayImage, config);
            if (log.isDebugEnabled() && colors != null && colors.length > 0) {
                log.debug("Colored overlay with #{}", String.format("%06X", colors[0] & 0xFFFFFF));
            }
        } else {
            // Color the base (e.g., leather armor)
            finalBase = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            renderer.render(finalBase, baseImage, config);
            finalOverlay = overlayImage;
            if (log.isDebugEnabled() && colors != null && colors.length > 0) {
                log.debug("Colored base item with #{}", String.format("%06X", colors[0] & 0xFFFFFF));
            }
        }

        return compositeImages(baseImage, finalBase, finalOverlay);
    }

    private BufferedImage compositeImages(BufferedImage baseImage, BufferedImage finalBase, BufferedImage finalOverlay) {
        BufferedImage result = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(finalBase, 0, 0, null);
        g.drawImage(finalOverlay, 0, 0, null);
        g.dispose();
        return result;
    }

    @Override
    public String getName() {
        return "overlay";
    }

    @Override
    public boolean canApply(EffectContext context) {
        String itemId = context.getItemId().toLowerCase();
        return overlayLoader.hasOverlay(itemId);
    }

    @Override
    public int getPriority() {
        return 50;
    }
}