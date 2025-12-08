package net.hypixel.nerdbot.generator.effect;

import java.awt.image.BufferedImage;

/**
 * Base interface for all image effects that can be applied to a {@link BufferedImage}.
 * Effects can be single (static) or multi-frame (animated).
 */
public interface ImageEffect {

    /**
     * Apply this effect to an image or set of animation frames.
     *
     * @param context The effect context containing image(s) and metadata
     *
     * @return Result containing transformed image(s)
     */
    EffectResult apply(EffectContext context);

    /**
     * Get the name of this effect.
     *
     * @return The effect name (e.g., "glint", "hover")
     */
    String getName();

    /**
     * Check if this effect can be applied based on context conditions.
     * <p>
     * Examples: "only if enchanted", "only if animated", "only for specific items"
     *
     * @param context The {@link EffectContext effect context} to check
     *
     * @return true if this effect should be applied, false to skip
     */
    default boolean canApply(EffectContext context) {
        return true;
    }

    /**
     * Get the priority/order hint for this effect.
     * Lower numbers execute first. Default is 100.
     *
     * @return Priority value (lower = earlier execution)
     */
    default int getPriority() {
        return 100;
    }
}