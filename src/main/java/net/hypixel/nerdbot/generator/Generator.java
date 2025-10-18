package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.generator.cache.GeneratorCache;
import net.hypixel.nerdbot.generator.cache.GeneratorCacheKey;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for all generators
 */
public interface Generator {

    /**
     * Entry point for generating an object with caching automatically applied
     *
     * @return the generated object (possibly retrieved from cache)
     */
    default GeneratedObject generate() {
        String cacheKey = GeneratorCacheKey.fromGenerator(this);
        GeneratedObject cachedObject = GeneratorCache.getGeneratedObject(cacheKey);
        if (cachedObject != null) {
            return cachedObject;
        }

        GeneratedObject generatedObject = render();
        GeneratorCache.putGeneratedObject(cacheKey, generatedObject);
        return generatedObject;
    }

    /**
     * Performs the actual rendering logic for the generator
     *
     * @return The generated object
     */
    @NotNull GeneratedObject render();
}
