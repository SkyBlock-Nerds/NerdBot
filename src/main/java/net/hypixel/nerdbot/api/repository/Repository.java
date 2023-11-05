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
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.UpdateResult;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import org.bson.Document;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Log4j2
public abstract class Repository<T> {

    @Getter
    private final Cache<String, T> cache;
    @Getter
    private final MongoCollection<Document> mongoCollection;
    private final Class<T> entityClass;

    protected Repository(MongoClient mongoClient, String databaseName, String collectionName) {
        this(mongoClient, databaseName, collectionName, 1, TimeUnit.DAYS);
    }

    protected Repository(MongoClient mongoClient, String databaseName, String collectionName, long expireAfterAccess, TimeUnit timeUnit) {
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        this.entityClass = (Class<T>) parameterizedType.getActualTypeArguments()[0];

        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.mongoCollection = database.getCollection(collectionName);

        this.cache = Caffeine.newBuilder()
            .expireAfterAccess(expireAfterAccess, timeUnit)
            .scheduler(Scheduler.systemScheduler())
            .removalListener((String key, T value, RemovalCause cause) -> {
                debug("Removing document with ID " + key + " from cache for reason " + cause.toString());

                if (cause != RemovalCause.EXPLICIT) {
                    saveToDatabase(value);
                }
            })
            .build();
    }

    public void loadAllDocumentsIntoCache() {
        for (Document document : mongoCollection.find()) {
            T object = documentToEntity(document);
            cacheObject(object);
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
        String id = getId(object);
        cacheObject(id, object);
    }

    public UpdateResult saveToDatabase(T object) {
        String id = getId(object);
        Document document = entityToDocument(object);

        return mongoCollection.replaceOne(new Document("_id", id), document, new ReplaceOptions().upsert(true));
    }

    @Nullable
    public InsertManyResult saveAllToDatabase() {
        debug("Saving all documents in cache to database (found " + cache.asMap().size() + ")");

        List<Document> documents = cache.asMap().values()
            .stream()
            .map(this::entityToDocument)
            .toList();

        debug("Converted " + documents.size() + " documents to JSON");

        if (!documents.isEmpty()) {
            return mongoCollection.insertMany(documents);
        } else {
            return null;
        }
    }

    public DeleteResult deleteFromDatabase(String id) {
        cache.invalidate(id);
        debug("Deleting document with ID " + id + " from database");
        return mongoCollection.deleteOne(new Document("_id", id));
    }

    protected abstract String getId(T entity);

    public Document entityToDocument(Object entity) {
        return Document.parse(NerdBotApp.GSON.toJson(entity));
    }

    protected T documentToEntity(Document document) {
        debug("Converting document to entity " + entityClass + ": " + document.toJson());
        return NerdBotApp.GSON.fromJson(document.toJson(), entityClass);
    }

    public boolean isEmpty() {
        return cache.asMap().isEmpty();
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
