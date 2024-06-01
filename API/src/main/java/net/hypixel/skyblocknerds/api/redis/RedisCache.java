package net.hypixel.skyblocknerds.api.redis;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisCache extends RedisClient {

    private static final String KEY_FORMAT = "cache:%s:%s";
    private final String prefix;

    public RedisCache(String uri, String prefix) {
        super(uri);
        this.prefix = prefix;
    }

    public void set(String key, String value) {
        jedis.set(KEY_FORMAT.formatted(prefix, key), value);
    }

    public void set(String key, String value, int expirationSeconds) {
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("Expiration must be greater than 0");
        }

        jedis.setex(KEY_FORMAT.formatted(prefix, key), expirationSeconds, value);
    }

    public void set(String key, String value, int expiration, TimeUnit timeUnit) {
        set(key, value, (int) timeUnit.toSeconds(expiration));
    }

    @Override
    public String get(String key) {
        return super.get(KEY_FORMAT.formatted(prefix, key));
    }

    public Map<String, String> getAll(String key) {
        return jedis.hgetAll(KEY_FORMAT.formatted(prefix, key));
    }
}
