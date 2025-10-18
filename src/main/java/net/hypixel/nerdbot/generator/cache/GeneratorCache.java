package net.hypixel.nerdbot.generator.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.GeneratorConfig;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class GeneratorCache {

    private static final Cache<@NotNull String, CacheEntry> CACHE;

    static {
        GeneratorConfig.CacheConfig config = NerdBotApp.getBot().getConfig().getGeneratorConfig().getCache();

        if (config.isEnabled()) {
            CACHE = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())
                .expireAfterWrite(config.getTtlMinutes(), TimeUnit.MINUTES)
                .build();

            log.info("Generator cache initialized with max size: {}, TTL: {} minutes",
                config.getMaxSize(), config.getTtlMinutes());
        } else {
            CACHE = null;
            log.info("Generator cache is disabled");
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
        CacheEntry entry = getEntry(key);
        return entry == null ? null : entry.image();
    }

    /**
     * Caches an image
     *
     * @param key   The cache key
     * @param image The image to cache
     */
    public static void putImage(String key, BufferedImage image) {
        if (CACHE == null || image == null) {
            return;
        }

        CACHE.put(key, new CacheEntry(image, null, null, 0));
        log.debug("Cached image with key: {}", key);
    }

    /**
     * Gets a cached GIF entry
     *
     * @param key The cache key
     * @return The cached GIF entry or null if not found/expired
     */
    public static GifCacheEntry getGif(String key) {
        CacheEntry entry = getEntry(key);
        if (entry == null || entry.gifData() == null) {
            return null;
        }

        return new GifCacheEntry(entry.gifData(), entry.frames(), entry.frameDelayMs());
    }

    /**
     * Caches a GIF entry
     *
     * @param key          The cache key
     * @param gifData      The GIF data to cache
     * @param frames       The animation frames to cache
     * @param frameDelayMs The frame delay in milliseconds
     */
    public static void putGif(String key, byte[] gifData, List<BufferedImage> frames, int frameDelayMs) {
        if (CACHE == null || gifData == null || gifData.length == 0 || frames == null || frames.isEmpty() || frameDelayMs <= 0) {
            return;
        }

        CACHE.put(key, new CacheEntry(frames.getFirst(), gifData, List.copyOf(frames), frameDelayMs));
        log.debug("Cached gif with key: {}", key);
    }

    /**
     * Gets cache statistics
     *
     * @return Cache statistics
     */
    public static CacheStats getStats() {
        var config = NerdBotApp.getBot().getConfig().getGeneratorConfig().getCache();

        int imageCount = 0;
        int gifCount = 0;

        if (CACHE != null) {
            for (CacheEntry entry : CACHE.asMap().values()) {
                if (entry.image() != null) {
                    imageCount++;
                }

                if (entry.gifData() != null) {
                    gifCount++;
                }
            }
        }

        return new CacheStats(
            imageCount,
            gifCount,
            config.getMaxSize(),
            config.getTtlMinutes(),
            config.isEnabled()
        );
    }

    /**
     * Cache statistics data class.
     */
    public record CacheStats(int imageCount, int gifCount, int maxSize, int ttlMinutes, boolean enabled) {
    }

    /**
     * Cached GIF entry
     */
    public record GifCacheEntry(byte[] gifData, List<BufferedImage> frames, int frameDelayMs) {
    }

    private static CacheEntry getEntry(String key) {
        if (CACHE == null) {
            return null;
        }

        CacheEntry entry = CACHE.getIfPresent(key);
        if (entry != null) {
            log.debug("Cache hit for key: {}", key);
        }

        return entry;
    }

    private record CacheEntry(BufferedImage image, byte[] gifData, List<BufferedImage> frames, int frameDelayMs) {
    }
}
