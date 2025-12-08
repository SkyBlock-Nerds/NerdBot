package net.hypixel.nerdbot.generator.effect.impl;

import net.hypixel.nerdbot.generator.effect.EffectContext;
import net.hypixel.nerdbot.generator.effect.EffectResult;
import net.hypixel.nerdbot.generator.effect.ImageEffect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hover effect - adds semi-transparent white overlay.
 * <p>
 * This effect adds a brightness overlay to simulate the hovered item appearance
 * in Minecraft menus.
 */
public class HoverImageEffect implements ImageEffect {

    private static final float OPACITY = 0.5f;
    private static final Color OVERLAY_COLOR = Color.WHITE;

    @Override
    public EffectResult apply(EffectContext context) {
        if (context.isAnimated()) {
            List<BufferedImage> hoveredFrames = context.getAnimationFrames().stream()
                .map(this::applyHover)
                .collect(Collectors.toList());
            return EffectResult.animated(hoveredFrames, context.getFrameDelayMs());
        } else {
            return EffectResult.single(applyHover(context.getImage()));
        }
    }

    private BufferedImage applyHover(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage hoveredItem = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int overlayR = OVERLAY_COLOR.getRed();
        int overlayG = OVERLAY_COLOR.getGreen();
        int overlayB = OVERLAY_COLOR.getBlue();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;

                if (alpha == 0) {
                    // Transparent pixel: fill with light color to show hover effect
                    int lightness = (int) (255 * OPACITY);
                    int newPixel = (255 << 24) | (lightness << 16) | (lightness << 8) | lightness;
                    hoveredItem.setRGB(x, y, newPixel);
                } else {
                    // Non-transparent pixel: blend with overlay color
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;

                    // Blend: result = base * (1 - opacity) + overlay * opacity
                    int blendedR = Math.min(255, (int) (r * (1 - OPACITY) + overlayR * OPACITY));
                    int blendedG = Math.min(255, (int) (g * (1 - OPACITY) + overlayG * OPACITY));
                    int blendedB = Math.min(255, (int) (b * (1 - OPACITY) + overlayB * OPACITY));

                    // Always use fully opaque alpha for GIF compatibility
                    int newPixel = (255 << 24) | (blendedR << 16) | (blendedG << 8) | blendedB;
                    hoveredItem.setRGB(x, y, newPixel);
                }
            }
        }

        return hoveredItem;
    }

    @Override
    public String getName() {
        return "hover";
    }

    @Override
    public boolean canApply(EffectContext context) {
        return context.isHovered();
    }

    @Override
    public int getPriority() {
        return 200;
    }
}