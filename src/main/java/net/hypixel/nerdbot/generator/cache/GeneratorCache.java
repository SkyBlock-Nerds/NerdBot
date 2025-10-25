package net.hypixel.nerdbot.generator.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.GeneratorConfig;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class GeneratorCache {

    private static final Cache<@NotNull String, GeneratedObject> CACHE;

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
     * Retrieves a cached {@link GeneratedObject}
     *
     * @param key The cache key
     * @return The cached object, or null if not present
     */
    public static GeneratedObject getGeneratedObject(String key) {
        if (CACHE == null) {
            return null;
        }

        GeneratedObject cachedObject = CACHE.getIfPresent(key);
        if (cachedObject != null) {
            log.debug("Generator cache hit for key '{}'", key);
        } else {
            log.debug("Generator cache miss for key '{}'", key);
        }
        return cachedObject;
    }

    /**
     * Stores a {@link GeneratedObject} in the cache
     *
     * @param key    The cache key
     * @param object The generated object to cache
     */
    public static void putGeneratedObject(String key, GeneratedObject object) {
        if (CACHE == null || object == null) {
            return;
        }

        CACHE.put(key, object);
        log.debug("Stored generated object in cache with key '{}'", key);
    }

    /**
     * Gets cache statistics
     *
     * @return Cache statistics
     */
    public static CacheStats getStats() {
        var config = NerdBotApp.getBot().getConfig().getGeneratorConfig().getCache();

        int objectCount = 0;
        int animatedCount = 0;

        if (CACHE != null) {
            for (Map.Entry<String, GeneratedObject> cacheEntry : CACHE.asMap().entrySet()) {
                GeneratedObject object = cacheEntry.getValue();
                if (object != null) {
                    objectCount++;
                    if (object.isAnimated()) {
                        animatedCount++;
                    }
                }
            }
        }

        return new CacheStats(
            objectCount,
            animatedCount,
            config.getMaxSize(),
            config.getTtlMinutes(),
            config.isEnabled()
        );
    }

    /**
     * Cache statistics data class.
     */
    public record CacheStats(int objectCount, int animatedCount, int maxSize, int ttlMinutes, boolean enabled) {
    }
}
