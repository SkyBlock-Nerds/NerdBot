package net.hypixel.skyblocknerds.api.redis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.ToString;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

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

    public KeyValuePair getObjectContainingValue(String searchValue, String keyPattern) {
        return scanForValue(searchValue, keyPattern, false);
    }

    public KeyValuePair getJsonObjectContainingValue(String searchValue, String keyPattern) {
        return scanForValue(searchValue, keyPattern, true);
    }

    private KeyValuePair scanForValue(String searchValue, String keyPattern, boolean isJson) {
        openConnection();
        ScanParams scanParams = new ScanParams().match(keyPattern);
        String cursor = ScanParams.SCAN_POINTER_START;

        do {
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            for (String key : scanResult.getResult()) {
                String value = jedis.get(key);
                if (value != null) {
                    if (isJson) {
                        JsonObject jsonObject = JsonParser.parseString(value).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().equalsIgnoreCase(searchValue)) {
                                return new KeyValuePair(key, value);
                            }
                        }
                    } else {
                        if (value.contains(searchValue)) {
                            return new KeyValuePair(key, value);
                        }
                    }
                }
            }
            cursor = scanResult.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

        return null;
    }

    public KeyValuePair getJsonObjectContainingValue(String searchValue) {
        return getJsonObjectContainingValue(searchValue, "*");
    }

    public KeyValuePair getObjectContainingValue(String searchValue) {
        return getObjectContainingValue(searchValue, "*");
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
