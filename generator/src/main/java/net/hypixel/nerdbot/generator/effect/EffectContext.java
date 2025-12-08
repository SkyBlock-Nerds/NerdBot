package net.hypixel.nerdbot.generator.effect;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context object passed through the effect pipeline.
 * Contains the image(s) being processed plus any metadata.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EffectContext {

    private final BufferedImage image;
    private final List<BufferedImage> animationFrames;
    private final int frameDelayMs;
    private final Map<String, Object> metadata;
    private final String itemId;
    private final boolean enchanted;
    private final boolean hovered;

    /**
     * Check if this context contains animation frames.
     *
     * @return true if animated (multiple frames), false otherwise
     */
    public boolean isAnimated() {
        return animationFrames != null && animationFrames.size() > 1;
    }

    /**
     * Get metadata value cast to the specified type.
     *
     * @param key  Metadata key
     * @param type Expected type
     * @param <T>  Type parameter
     *
     * @return {@link Optional} containing the value if present and of correct type, empty otherwise
     */
    public <T> Optional<T> getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    public static class Builder {
        private final Map<String, Object> metadata = new HashMap<>();
        private BufferedImage image;
        private List<BufferedImage> animationFrames;
        private int frameDelayMs;
        private String itemId;
        private boolean enchanted;
        private boolean hovered;

        public Builder withImage(BufferedImage image) {
            this.image = image;
            return this;
        }

        public Builder withAnimationFrames(List<BufferedImage> frames, int delayMs) {
            this.animationFrames = frames;
            this.frameDelayMs = delayMs;

            if (!frames.isEmpty()) {
                this.image = frames.getFirst();
            }

            return this;
        }

        public Builder withItemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder withEnchanted(boolean enchanted) {
            this.enchanted = enchanted;
            return this;
        }

        public Builder withHovered(boolean hovered) {
            this.hovered = hovered;
            return this;
        }

        public Builder putMetadata(String key, Object value) {
            if (value != null) {
                this.metadata.put(key, value);
            }

            return this;
        }

        public Builder withMetadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public EffectContext build() {
            return new EffectContext(
                image,
                animationFrames != null ? Collections.unmodifiableList(animationFrames) : null,
                frameDelayMs,
                Map.copyOf(metadata),
                itemId,
                enchanted,
                hovered
            );
        }
    }
}