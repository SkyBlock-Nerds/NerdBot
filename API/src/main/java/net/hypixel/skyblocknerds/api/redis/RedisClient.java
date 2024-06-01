package net.hypixel.skyblocknerds.api.redis;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.ToString;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisClient {

    private final String uri;
    protected Jedis jedis;

    public RedisClient(String uri) {
        this.uri = uri;
        openConnection();
    }

    public void openConnection() {
        if (this.jedis == null || !this.jedis.isConnected()) {
            this.jedis = new Jedis(uri);
        }
    }

    public void closeConnection() {
        if (this.jedis != null) {
            this.jedis.close();
        }
    }

    public void set(String key, String value) {
        try {
            openConnection();
            jedis.set(key, value);
        } catch (Exception e) {
            handleException(e);
        }
    }

    public String get(String key) {
        try {
            openConnection();
            return jedis.get(key);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Scan the Redis database for a key-value pair.
     *
     * @param key   The key to scan for.
     * @param value The value to scan for.
     *
     * @return A list of {@link JsonObject} objects that match the key-value pair.
     */
    public List<JsonObject> scanForValue(String key, String value) {
        openConnection();

        String cursor = ScanParams.SCAN_POINTER_START;
        List<JsonObject> resultKeys = new ArrayList<>();

        try {
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, new ScanParams().match("*"));
                List<String> keys = scanResult.getResult();

                for (String k : keys) {
                    String jsonValue = jedis.get(k);
                    JsonObject jsonObject = SkyBlockNerdsAPI.GSON.fromJson(jsonValue, JsonObject.class);

                    if (jsonObject != null && jsonObject.has(key) && jsonObject.get(key).getAsString().equals(value)) {
                        resultKeys.add(jsonObject);
                    }
                }

                cursor = scanResult.getCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
        } finally {
            closeConnection();
        }

        return resultKeys;
    }

    public void delete(String key) {
        try {
            openConnection();
            jedis.del(key);
        } catch (Exception e) {
            handleException(e);
        }
    }

    // Set operations
    public void addToSet(String setName, String... members) {
        try {
            openConnection();
            jedis.sadd(setName, members);
        } catch (Exception e) {
            handleException(e);
        }
    }

    public Set<String> getSetMembers(String setName) {
        try {
            openConnection();
            return jedis.smembers(setName);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    // List operations
    public void pushToList(String listName, String... values) {
        try {
            openConnection();
            jedis.lpush(listName, values);
        } catch (Exception e) {
            handleException(e);
        }
    }

    public List<String> getListRange(String listName, long start, long end) {
        try {
            openConnection();
            return jedis.lrange(listName, start, end);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    // Hash operations
    public void setHashField(String hashName, String field, String value) {
        try {
            openConnection();
            jedis.hset(hashName, field, value);
        } catch (Exception e) {
            handleException(e);
        }
    }

    public String getHashField(String hashName, String field) {
        try {
            openConnection();
            return jedis.hget(hashName, field);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    public Map<String, String> getHashAll(String hashName) {
        try {
            openConnection();
            return jedis.hgetAll(hashName);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    // Transactions
    public void executeTransaction(List<TransactionCommand> commands) {
        try {
            openConnection();
            Transaction transaction = jedis.multi();
            for (TransactionCommand command : commands) {
                command.execute(transaction);
            }
            transaction.exec();
        } catch (Exception e) {
            handleException(e);
        }
    }

    private void handleException(Exception e) {
        e.printStackTrace();
        closeConnection();
    }

    public interface TransactionCommand {
        void execute(Transaction transaction);
    }

    @ToString
    @Getter
    public static class KeyValuePair {
        private final String key;
        private final String value;

        public KeyValuePair(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
