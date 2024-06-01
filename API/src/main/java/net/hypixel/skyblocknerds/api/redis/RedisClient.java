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

    /**
     * Create a new Redis client instance with the given URI.
     *
     * @param uri The URI of the Redis server.
     */
    public RedisClient(String uri) {
        this.uri = uri;
        openConnection();
    }

    /**
     * Open a connection to the Redis server.
     */
    public void openConnection() {
        if (this.jedis == null || !this.jedis.isConnected()) {
            this.jedis = new Jedis(uri);
        }
    }

    /**
     * Close the connection to the Redis server.
     */
    public void closeConnection() {
        if (this.jedis != null) {
            this.jedis.close();
        }
    }

    /**
     * Set a key-value pair in the Redis database.
     *
     * @param key   The key to set.
     * @param value The value to set.
     */
    public void set(String key, String value) {
        try {
            openConnection();
            jedis.set(key, value);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Get a value from the Redis database.
     *
     * @param key The key to get.
     *
     * @return The value of the key.
     */
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
     * Get all values with the given key from the Redis database.
     *
     * @param key The key to get.
     *
     * @return A {@link Map} of all values with the given key.
     */
    public Map<String, String> getAll(String key) {
        return jedis.hgetAll(key);
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

    /**
     * Delete a key from the Redis database.
     *
     * @param key The key to delete.
     */
    public void delete(String key) {
        try {
            openConnection();
            jedis.del(key);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Add a value to a set in the Redis database.
     *
     * @param setName The name of the set.
     * @param members The members to add to the set.
     */
    public void addToSet(String setName, String... members) {
        try {
            openConnection();
            jedis.sadd(setName, members);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Get all members of a set in the Redis database.
     *
     * @param setName The name of the set.
     *
     * @return A set of all members in the set.
     */
    public Set<String> getSetMembers(String setName) {
        try {
            openConnection();
            return jedis.smembers(setName);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Add a value to a list in the Redis database.
     *
     * @param listName The name of the list.
     * @param values   The values to add to the list.
     */
    public void pushToList(String listName, String... values) {
        try {
            openConnection();
            jedis.lpush(listName, values);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Get a range of values from a list in the Redis database.
     *
     * @param listName The name of the list.
     * @param start    The start index of the range.
     * @param end      The end index of the range.
     *
     * @return A {@link List} of values in the range.
     */
    public List<String> getListRange(String listName, long start, long end) {
        try {
            openConnection();
            return jedis.lrange(listName, start, end);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Set a hash field in the Redis database with the given value.
     *
     * @param hashName The name of the hash.
     * @param field    The field to set.
     * @param value    The value to set.
     */
    public void setHashField(String hashName, String field, String value) {
        try {
            openConnection();
            jedis.hset(hashName, field, value);
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Get a hash field from the Redis database.
     *
     * @param hashName The name of the hash.
     * @param field    The field to get.
     *
     * @return The value of the field.
     */
    public String getHashField(String hashName, String field) {
        try {
            openConnection();
            return jedis.hget(hashName, field);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Get all hash fields by the given hash name.
     *
     * @param hashName The name.
     *
     * @return A {@link Map} of all fields and their values.
     */
    public Map<String, String> getHashAll(String hashName) {
        try {
            openConnection();
            return jedis.hgetAll(hashName);
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Execute a transaction with the given commands.
     *
     * @param commands The commands to execute.
     */
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

    /**
     * Prints the stack trace of the exception and closes the connection.
     *
     * @param e The exception to handle.
     */
    private void handleException(Exception e) {
        e.printStackTrace();
        closeConnection();
    }

    public interface TransactionCommand {
        /**
         * Execute the command with the given transaction.
         *
         * @param transaction The transaction to execute the command with.
         */
        void execute(Transaction transaction);
    }

    @ToString
    @Getter
    public static class KeyValuePair {
        private final String key;
        private final String value;

        /**
         * Create a new key-value pair.
         *
         * @param key   The key.
         * @param value The value.
         */
        public KeyValuePair(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
