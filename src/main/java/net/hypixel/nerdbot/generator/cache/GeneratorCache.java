package net.hypixel.nerdbot.generator.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.GeneratorConfig;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class GeneratorCache {

    private static final Cache<@NotNull String, BufferedImage> IMAGE_CACHE;

    static {
        GeneratorConfig.CacheConfig config = NerdBotApp.getBot().getConfig().getGeneratorConfig().getCache();

        if (config.isEnabled()) {
            IMAGE_CACHE = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())
                .expireAfterWrite(config.getTtlMinutes(), TimeUnit.MINUTES)
                .build();

            log.info("Generator image cache initialized with max size: {}, TTL: {} minutes",
                config.getMaxSize(), config.getTtlMinutes());
        } else {
            IMAGE_CACHE = null;
            log.info("Generator image cache is disabled");
        }
    }

    private GeneratorCache() {
        // Utility class
    }

    /**
     * Gets a cached image
     *
     * @param key The cache key
     * @return The cached image or null if not found/expired
     */
    public static BufferedImage getImage(String key) {
        if (IMAGE_CACHE == null) {
            return null;
        }

        BufferedImage image = IMAGE_CACHE.getIfPresent(key);
        if (image != null) {
            log.debug("Cache hit for image key: {}", key);
        }
        return image;
    }

    /**
     * Caches an image
     *
     * @param key The cache key
     * @param image The image to cache
     */
    public static void putImage(String key, BufferedImage image) {
        if (IMAGE_CACHE == null || image == null) {
            return;
        }

        IMAGE_CACHE.put(key, image);
        log.debug("Cached image with key: {}", key);
    }

    /**
     * Gets cache statistics
     *
     * @return Cache statistics
     */
    public static CacheStats getStats() {
        var config = NerdBotApp.getBot().getConfig().getGeneratorConfig().getCache();

        int imageCount = IMAGE_CACHE != null ? (int) IMAGE_CACHE.estimatedSize() : 0;

        return new CacheStats(
            imageCount,
            config.getMaxSize(),
            config.getTtlMinutes(),
            config.isEnabled()
        );
    }

    /**
     * Cache statistics data class
     */
    public record CacheStats(int imageCount, int maxSize, int ttlMinutes, boolean enabled) {
    }
}