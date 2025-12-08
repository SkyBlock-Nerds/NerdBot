package net.hypixel.nerdbot.generator.effect;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * The result of applying an effect.
 * Contains either a single image OR a list of animation frames.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EffectResult {

    private final BufferedImage image;
    private final List<BufferedImage> animationFrames;
    private final int frameDelayMs;
    private final boolean animated;

    /**
     * Create a result with a single image.
     *
     * @param image The result image
     *
     * @return {@link EffectResult} instance containing a single image
     */
    public static EffectResult single(BufferedImage image) {
        return new EffectResult(image, null, 0, false);
    }

    /**
     * Create a result with animated frames.
     *
     * @param frames  List of animation frames
     * @param delayMs Delay between frames in milliseconds
     *
     * @return {@link EffectResult} instance containing animation frames
     */
    public static EffectResult animated(List<BufferedImage> frames, int delayMs) {
        return new EffectResult(
            frames.isEmpty() ? null : frames.getFirst(),
            Collections.unmodifiableList(frames),
            delayMs,
            true
        );
    }

    /**
     * Convert this result back to an EffectContext for pipeline continuation.
     * Preserves metadata from the original context.
     *
     * @param original The original context to copy metadata from
     *
     * @return New {@link EffectContext} instance with updated images
     */
    public EffectContext toContext(EffectContext original) {
        EffectContext.Builder builder = new EffectContext.Builder()
            .withItemId(original.getItemId())
            .withEnchanted(original.isEnchanted())
            .withHovered(original.isHovered())
            .withMetadata(original.getMetadata());

        if (animated && animationFrames != null) {
            builder.withAnimationFrames(animationFrames, frameDelayMs);
        } else {
            builder.withImage(image);
        }

        return builder.build();
    }
}