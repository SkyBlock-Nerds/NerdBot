package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.generator.cache.GeneratorCache;
import net.hypixel.nerdbot.generator.cache.GeneratorCacheKey;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        return generate(null);
    }

    /**
     * Entry point for generating an object with caching automatically applied
     *
     * @param generationContext the generation context, or null if unavailable
     *
     * @return the generated object (possibly retrieved from cache)
     */
    default GeneratedObject generate(@Nullable GenerationContext generationContext) {
        String cacheKey = GeneratorCacheKey.fromGenerator(this);
        GeneratedObject cachedObject = GeneratorCache.getGeneratedObject(cacheKey);
        if (cachedObject != null) {
            return cachedObject;
        }

        GeneratedObject generatedObject = render(generationContext);
        GeneratorCache.putGeneratedObject(cacheKey, generatedObject);
        return generatedObject;
    }

    /**
     * Performs the actual rendering logic for the generator
     *
     * @param generationContext the generation context, or null if unavailable
     * @return The generated object
     */
    @NotNull GeneratedObject render(@Nullable GenerationContext generationContext);
}
