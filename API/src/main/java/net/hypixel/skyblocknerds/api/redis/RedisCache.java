package net.hypixel.skyblocknerds.api.redis;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisCache extends RedisClient {

    private static final String KEY_FORMAT = "cache:%s:%s";
    private final String prefix;

    /**
     * Create a new Redis cache instance with the given URI and prefix.
     *
     * @param uri    The URI of the Redis server.
     * @param prefix The prefix for the cache keys.
     */
    public RedisCache(URI uri, String prefix) {
        super(uri);
        this.prefix = prefix;
    }

    /**
     * Set a key-value pair in the Redis database.
     *
     * @param key   The key to set.
     * @param value The value to set.
     */
    @Override
    public void set(String key, String value) {
        jedis.set(KEY_FORMAT.formatted(prefix, key), value);
    }

    /**
     * Set a key-value pair in the Redis database with an expiration time in seconds.
     *
     * @param key               The key to set.
     * @param value             The value to set.
     * @param expirationSeconds The expiration time in seconds.
     */
    public void set(String key, String value, int expirationSeconds) {
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("Expiration must be greater than 0");
        }

        jedis.setex(KEY_FORMAT.formatted(prefix, key), expirationSeconds, value);
    }

    /**
     * Set a key-value pair in the Redis database with an expiration time.
     * <p>
     * This method will convert the expiration time to seconds.
     *
     * @param key        The key to set.
     * @param value      The value to set.
     * @param expiration The expiration time.
     * @param timeUnit   The {@link TimeUnit} of the expiration time.
     */
    public void set(String key, String value, int expiration, TimeUnit timeUnit) {
        set(key, value, (int) timeUnit.toSeconds(expiration));
    }

    /**
     * Get a value by key from the cache.
     *
     * @param key The key to get.
     *
     * @return The value from the cache.
     */
    @Override
    public String get(String key) {
        return super.get(KEY_FORMAT.formatted(prefix, key));
    }

    /**
     * Get all values from a key in the cache.
     *
     * @param key The key to get.
     *
     * @return A {@link Map} of all values in the cache.
     */
    @Override
    public Map<String, String> getAll(String key) {
        return jedis.hgetAll(KEY_FORMAT.formatted(prefix, key));
    }
}
