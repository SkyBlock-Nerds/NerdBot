package net.hypixel.nerdbot.api.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
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
    private final String identifierFieldName;
    private Field field;

    protected Repository(MongoClient mongoClient, String databaseName, String collectionName, String identifierFieldName) {
        this(mongoClient, databaseName, collectionName, identifierFieldName, 1, TimeUnit.DAYS);
    }

    protected Repository(MongoClient mongoClient, String databaseName, String collectionName, String identifierFieldName, long expireAfterAccess, TimeUnit timeUnit) {
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        this.entityClass = (Class<T>) parameterizedType.getActualTypeArguments()[0];

        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.mongoCollection = database.getCollection(collectionName);

        this.cache = Caffeine.newBuilder()
            .expireAfterAccess(expireAfterAccess, timeUnit)
            .scheduler(Scheduler.systemScheduler())
            .removalListener((String key, T value, RemovalCause cause) -> {
                debug("Removing document with ID " + key + " from cache for reason " + cause.toString());

                if (cause != RemovalCause.EXPLICIT || cause != RemovalCause.REPLACED) {
                    saveToDatabase(value);
                }
            })
            .build();

        this.identifierFieldName = identifierFieldName;
    }

    public void loadAllDocumentsIntoCache() {
        log("Loading ALL documents from database into cache");

        for (Document document : mongoCollection.find()) {
            T object = documentToEntity(document);

            if (cache.getIfPresent(getId(object)) != null) {
                debug("Document with ID " + getId(object) + " already exists in cache");
                continue;
            }

            cacheObject(object);
        }
    }

    public T findById(String id) {
        T cachedObject = cache.getIfPresent(id);
        if (cachedObject != null) {
            debug("Found document with ID " + id + " in cache");
            return cachedObject;
        }

        Document document = mongoCollection.find(new Document(identifierFieldName, id)).first();
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
        Bson updateOperation = new Document("$set", document);

        return mongoCollection.updateOne(new Document(identifierFieldName, id), updateOperation, new UpdateOptions().upsert(true));
    }


    @Nullable
    public BulkWriteResult saveAllToDatabase() {
        log("Saving all documents in cache to database (found " + cache.asMap().size() + ")");

        List<WriteModel<Document>> updates = new ArrayList<>();

        getAll().stream().map(this::entityToDocument).forEach(doc -> {
            Bson filter = Filters.eq(identifierFieldName, doc.get(identifierFieldName));
            Bson update = new Document("$set", doc);
            updates.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        });

        debug("Prepared " + updates.size() + " update operations");

        if (!updates.isEmpty()) {
            return mongoCollection.bulkWrite(updates);
        }

        return null;
    }

    public DeleteResult deleteFromDatabase(String id) {
        cache.invalidate(id);
        log("Deleting document with ID " + id + " from database");

        return mongoCollection.deleteOne(Filters.eq(identifierFieldName, id));
    }

    protected String getId(T entity) {
        try {
            if (field == null) {
                field = entity.getClass().getDeclaredField(identifierFieldName);
            }

            Object value = field.get(entity);
            return value != null ? value.toString() : null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Error accessing identifier field: " + identifierFieldName, e);
        }
    }

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
        return new ArrayList<>(cache.asMap().values());
    }

    public Collection<T> filter(Predicate<T> filter) {
        return getAll().stream()
            .filter(filter)
            .toList();
    }

    public void forEach(Consumer<T> consumer) {
        getAll().forEach(consumer);
    }

    private void log(String message) {
        log.info("[" + getClass().getSimpleName() + "] " + message);
    }

    private void debug(String message) {
        log.debug("[" + getClass().getSimpleName() + "] " + message);
    }
}
