package net.hypixel.nerdbot.generator.effect.impl;

import net.hypixel.nerdbot.generator.effect.EffectContext;
import net.hypixel.nerdbot.generator.effect.EffectResult;
import net.hypixel.nerdbot.generator.effect.ImageEffect;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Durability bar effect - adds a colored durability bar at the bottom of items.
 * <p>
 * The durability bar consists of two parts:
 * - A black background bar
 * - A colored fill bar that changes from green (high) to yellow (medium) to red (low)
 */
public class DurabilityBarEffect implements ImageEffect {

    @Override
    public EffectResult apply(EffectContext context) {
        Integer durabilityPercent = context.getMetadata("durabilityPercent", Integer.class).orElse(null);

        if (durabilityPercent == null || durabilityPercent >= 100) {
            // No durability bar needed
            return context.isAnimated()
                ? EffectResult.animated(context.getAnimationFrames(), context.getFrameDelayMs())
                : EffectResult.single(context.getImage());
        }

        if (context.isAnimated()) {
            // Apply to all frames
            List<BufferedImage> frames = context.getAnimationFrames().stream()
                .map(frame -> addDurabilityBar(frame, durabilityPercent))
                .collect(Collectors.toList());
            return EffectResult.animated(frames, context.getFrameDelayMs());
        } else {
            return EffectResult.single(addDurabilityBar(context.getImage(), durabilityPercent));
        }
    }

    private BufferedImage addDurabilityBar(BufferedImage item, int durabilityPercent) {
        BufferedImage result = new BufferedImage(item.getWidth(), item.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();

        // Draw the original item
        g2d.drawImage(item, 0, 0, null);

        // The durability display is two separate bars stacked vertically
        int scaleFactor = item.getWidth() / 16;
        int barWidth = item.getWidth() - (4 * scaleFactor) + scaleFactor;
        int barX = 2 * scaleFactor;

        // Position the bars
        int colorBarY = item.getHeight() - (3 * scaleFactor); // Top bar (colored durability)
        int blackBarY = item.getHeight() - (2 * scaleFactor); // Bottom bar (permanently black)

        // Draw the bottom bar
        g2d.setColor(Color.BLACK);
        g2d.fillRect(barX, blackBarY, barWidth, scaleFactor);

        // Draw the top durability bar background
        g2d.setColor(Color.BLACK);
        g2d.fillRect(barX, colorBarY, barWidth, scaleFactor);

        // Draw the colored portion
        if (durabilityPercent > 0) {
            int filledWidth = (int) (barWidth * durabilityPercent / 100.0);
            Color barColor = getDurabilityColor(durabilityPercent);
            g2d.setColor(barColor);
            g2d.fillRect(barX, colorBarY, filledWidth, scaleFactor);
        }

        g2d.dispose();
        return result;
    }

    private Color getDurabilityColor(int durabilityPercent) {
        if (durabilityPercent > 50) {
            // Green to yellow (high to medium durability)
            int red = (int) (255 * 2 * (100 - durabilityPercent) / 100.0);
            return new Color(red, 255, 0);
        } else {
            // Yellow to red (medium to low durability)
            int green = (int) (255 * 2 * durabilityPercent / 100.0);
            return new Color(255, green, 0);
        }
    }

    @Override
    public String getName() {
        return "durability";
    }

    @Override
    public boolean canApply(EffectContext context) {
        Integer durabilityPercent = context.getMetadata("durabilityPercent", Integer.class).orElse(null);
        return durabilityPercent != null && durabilityPercent < 100;
    }

    @Override
    public int getPriority() {
        return 300;
    }
}