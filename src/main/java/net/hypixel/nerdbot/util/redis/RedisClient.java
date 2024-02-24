package net.hypixel.nerdbot.util.redis;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.util.KeyValuePair;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
public class RedisClient {

    protected final Jedis jedis;

    public RedisClient(String host, int port) {
        this.jedis = new Jedis(host, port);
    }

    public String set(String key, String value) {
        log.debug("Setting key '{}' to value '{}'", key, value);
        return jedis.set(key, value);
    }

    public String get(String key) {
        log.debug("Getting value for key '{}'", key);
        return jedis.get(key);
    }

    @Nullable
    public KeyValuePair search(String searchValue, String keyPattern) {
        ScanParams scanParams = new ScanParams().match(keyPattern);
        String cursor = ScanParams.SCAN_POINTER_START;

        log.debug("Searching for value '{}' in keys matching pattern '{}'", searchValue, keyPattern);

        do {
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);

            for (String key : scanResult.getResult()) {
                String value = jedis.get(key);

                log.debug("Checking key '{}' for search value '{}': {}", key, searchValue, value);

                if (value != null && value.contains(searchValue)) {
                    log.debug("Matched key '{}' with search value '{}': '{}'", key, searchValue, value);
                    return new KeyValuePair(key, value);
                }
            }

            cursor = scanResult.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

        log.debug("No match found for search value '{}' in keys matching pattern '{}'", searchValue, keyPattern);
        return null;
    }

    public long delete(String key) {
        log.debug("Deleting key '{}'", key);
        return jedis.del(key);
    }

    public long addToSet(String setName, String... members) {
        log.debug("Adding members to set '{}': {}", setName, members);
        return jedis.sadd(setName, members);
    }

    public Set<String> getSetMembers(String setName) {
        log.debug("Getting members of set '{}'", setName);
        return jedis.smembers(setName);
    }

    public long pushToList(String listName, String... values) {
        log.debug("Pushing values to list '{}': {}", listName, values);
        return jedis.lpush(listName, values);
    }

    public List<String> getListRange(String listName, long start, long end) {
        log.debug("Getting range of values from list '{}' ({} to {})", listName, start, end);
        return jedis.lrange(listName, start, end);
    }

    public long setHashField(String hashName, String field, String value) {
        log.debug("Setting field '{}' of hash '{}' to value '{}'", field, hashName, value);
        return jedis.hset(hashName, field, value);
    }

    public String getHashField(String hashName, String field) {
        log.debug("Getting value of field '{}' from hash '{}'", field, hashName);
        return jedis.hget(hashName, field);
    }

    public Map<String, String> getHashAll(String hashName) {
        log.debug("Getting all fields and values from hash '{}'", hashName);
        return jedis.hgetAll(hashName);
    }

    public void executeTransaction(List<TransactionCommand> commands) {
        Transaction transaction = jedis.multi();

        commands.forEach(command -> {
            log.debug("Executing transaction command: {}", command);
            command.execute(transaction);
        });

        List<Object> executions = transaction.exec();
        log.debug("Executed {} transaction commands: {}", executions.size(), executions);
    }

    public void close() {
        log.debug("Closing Redis client connection");
        jedis.close();
    }

    public interface TransactionCommand {
        void execute(Transaction transaction);
    }
}
