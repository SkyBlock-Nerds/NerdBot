package net.hypixel.nerdbot.api.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import org.bson.Document;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Log4j2
public abstract class CachedMongoRepository<T> {

    private final Cache<String, T> cache;
    private final MongoCollection<Document> mongoCollection;
    private final Class<T> entityClass;

    protected CachedMongoRepository(MongoClient mongoClient, String databaseName, String collectionName) {
        this(mongoClient, databaseName, collectionName, 15, TimeUnit.MINUTES);
    }

    protected CachedMongoRepository(MongoClient mongoClient, String databaseName, String collectionName, long expireAfterAccess, TimeUnit timeUnit) {
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        this.entityClass = (Class<T>) parameterizedType.getActualTypeArguments()[0];

        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.mongoCollection = database.getCollection(collectionName);

        this.cache = Caffeine.newBuilder()
            .expireAfterAccess(expireAfterAccess, timeUnit)
            .scheduler(Scheduler.systemScheduler())
            .removalListener((String key, T value, RemovalCause cause) -> {
                debug("Removing document with ID " + key + " from cache for reason " + cause.toString());
                saveToDatabase(value);
            })
            .build();

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveAllToDatabase));
    }

    private void loadAllDocumentsIntoCache() {
        for (Document document : mongoCollection.find()) {
            T object = documentToEntity(document);
            String id = getId(object);

            cacheObject(object);
            debug("Loaded document with ID " + id + " into cache (1)");
        }
    }

    public T findById(String id) {
        T cachedObject = cache.getIfPresent(id);
        if (cachedObject != null) {
            debug("Found document with ID " + id + " in cache");
            return cachedObject;
        }

        Document document = mongoCollection.find(new Document("_id", id)).first();
        if (document != null) {
            T object = documentToEntity(document);
            cacheObject(id, object);
            return object;
        }

        debug("Could not find document with ID " + id + " in cache or database");
        return null;
    }

    public void cacheObject(String id, T object) {
        cache.put(id, object);
        debug("Cached document with ID " + id);
    }

    public void cacheObject(T object) {
        cacheObject(getId(object), object);
    }

    public UpdateResult saveToDatabase(T object) {
        String id = getId(object);
        Document document = entityToDocument(object);

        debug("Saving document with ID " + id + " to database from cache");
        return mongoCollection.replaceOne(new Document("_id", id), document, new ReplaceOptions().upsert(true));
    }

    public void saveAllToDatabase() {
        for (T object : cache.asMap().values()) {
            saveToDatabase(object);
        }

        debug("Saved all documents in cache to database");
    }

    public DeleteResult deleteFromDatabase(String id) {
        cache.invalidate(id);
        debug("Deleting document with ID " + id + " from database");
        return mongoCollection.deleteOne(new Document("_id", id));
    }

    protected abstract String getId(T entity);

    protected Document entityToDocument(T entity) {
        return Document.parse(NerdBotApp.GSON.toJson(entity));
    }

    protected T documentToEntity(Document document) {
        debug("Converting document to entity " + entityClass + ": " + document.toJson());
        return NerdBotApp.GSON.fromJson(document.toJson(), entityClass);
    }

    public boolean isEmpty() {
        return cache.asMap().isEmpty();
    }

    public Cache<String, T> getCache() {
        return cache;
    }

    public List<T> getAll() {
        return (List<T>) cache.asMap().values();
    }

    public Collection<T> filter(Predicate<T> filter) {
        return cache.asMap().values()
            .stream()
            .filter(filter)
            .toList();
    }

    public void forEach(Consumer<T> consumer) {
        cache.asMap().values().forEach(consumer);
    }

    private void log(String message) {
        log.info("[" + getClass().getSimpleName() + "] " + message);
    }

    private void debug(String message) {
        log.debug("[" + getClass().getSimpleName() + "] " + message);
    }
}
