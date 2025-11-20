package net.hypixel.nerdbot.generator.cache;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class GeneratorCache {

    private static final boolean ENABLED = Boolean.getBoolean("generator.cache.enabled");
    private static final int MAX_SIZE = Integer.getInteger("generator.cache.maxSize", 256);

    private static final Map<@NotNull String, GeneratedObject> CACHE = ENABLED
        ? Collections.synchronizedMap(new LinkedHashMap<>(MAX_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GeneratedObject> eldest) {
            return size() > MAX_SIZE;
        }
    })
        : null;

    private GeneratorCache() {
    }

    public static GeneratedObject getGeneratedObject(String key) {
        if (CACHE == null) {
            return null;
        }
        GeneratedObject cachedObject = CACHE.get(key);
        if (cachedObject != null) {
            log.debug("Generator cache hit for key '{}'", key);
        } else {
            log.debug("Generator cache miss for key '{}'", key);
        }
        return cachedObject;
    }

    public static void putGeneratedObject(String key, GeneratedObject object) {
        if (CACHE == null || object == null) {
            return;
        }
        CACHE.put(key, object);
        log.debug("Stored generated object in cache with key '{}'", key);
    }

    public static CacheStats getStats() {
        int objectCount = 0;
        int animatedCount = 0;

        if (CACHE != null) {
            for (GeneratedObject object : CACHE.values()) {
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
            MAX_SIZE,
            0,
            ENABLED
        );
    }

    public record CacheStats(int objectCount, int animatedCount, int maxSize, int ttlMinutes, boolean enabled) {
    }
}
