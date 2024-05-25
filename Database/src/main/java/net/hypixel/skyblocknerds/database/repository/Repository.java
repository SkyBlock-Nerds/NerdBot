package net.hypixel.skyblocknerds.database.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
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
import com.mongodb.lang.Nullable;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /**
     * Creates a new {@link Repository} with the given {@link MongoClient}, {@link String databaseName},
     * {@link String collectionName}, and {@link String identifierFieldName}.
     * <p>
     * This repository will not expire documents.
     * <br>
     * The {@link Cache} will be initialized with no expiration time.
     *
     * @param mongoClient         The {@link MongoClient} to use for database operations
     * @param databaseName        The name of the database to use
     * @param collectionName      The name of the collection to use
     * @param identifierFieldName The name of the field that is used as the identifier for the documents
     */
    protected Repository(MongoClient mongoClient, String databaseName, String collectionName, String identifierFieldName) {
        this(mongoClient, databaseName, collectionName, identifierFieldName, 0, null);
    }

    /**
     * Creates a new {@link Repository} with the given {@link MongoClient}, {@link String databaseName}, {@link String collectionName},
     * {@link String identifierFieldName}, {@link long expireAfterAccess}, and {@link TimeUnit timeUnit}.
     * <p>
     * This repository will expire documents after the given {@link long expireAfterAccess} and {@link TimeUnit timeUnit}.
     * <br>
     * The {@link Cache} will be initialized with the given {@link long expireAfterAccess} and {@link TimeUnit timeUnit}.
     * <br>
     * The {@link Cache} will have a {@link RemovalListener} that saves the document to the database if it is removed from the cache.
     *
     * @param mongoClient         The {@link MongoClient} to use for database operations
     * @param databaseName        The name of the database to use
     * @param collectionName      The name of the collection to use
     * @param identifierFieldName The name of the field that is used as the identifier for the documents
     * @param expireAfterAccess   The time after which documents will expire
     * @param timeUnit            The {@link TimeUnit} for the expiration time
     */
    protected Repository(MongoClient mongoClient, String databaseName, String collectionName, String identifierFieldName, long expireAfterAccess, TimeUnit timeUnit) {
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        this.entityClass = (Class<T>) parameterizedType.getActualTypeArguments()[0];

        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.mongoCollection = database.getCollection(collectionName);

        Caffeine<Object, Object> builder = Caffeine.newBuilder();

        if (expireAfterAccess > 0 && timeUnit != null) {
            log("Documents in this repository will expire after " + expireAfterAccess + " " + timeUnit.toString().toLowerCase());
            builder.expireAfterAccess(expireAfterAccess, timeUnit)
                .scheduler(Scheduler.systemScheduler());
        } else {
            log("Documents in this repository will not expire");
        }

        this.cache = builder
            .removalListener((String key, T value, RemovalCause cause) -> {
                debug("Removing document with ID " + key + " from cache for reason " + cause.toString());

                if (cause != RemovalCause.EXPLICIT && cause != RemovalCause.REPLACED) {
                    saveToDatabase(value);
                }
            })
            .build();

        this.identifierFieldName = identifierFieldName;
    }

    /**
     * Loads all documents from the database into the {@link Cache}
     */
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

    /**
     * Finds a document by its ID in the cache, or database if it is not present in the cache.
     * <p>
     * If the document is found in the database, it will be cached, and returned as an {@link Optional}.
     * If the document is not found in the database, an empty {@link Optional} will be returned.
     *
     * @param id The ID of the document to find
     *
     * @return An {@link Optional} containing the {@link T object} if it was found, otherwise an empty {@link Optional}
     */
    public Optional<T> findById(String id) {
        T cachedObject = cache.getIfPresent(id);
        if (cachedObject != null) {
            debug("Found document with ID " + id + " in cache");
            return Optional.of(cachedObject);
        }

        Document document = mongoCollection.find(new Document(identifierFieldName, id)).first();
        if (document != null) {
            T object = documentToEntity(document);
            cacheObject(id, object);

            return Optional.of(object);
        }

        debug("Could not find document with ID " + id + " in cache or database");
        return Optional.empty();
    }

    /**
     * Finds a {@link T object} by its ID in the cache, or creates a new instance of the {@link T object} if it is not present in either.
     *
     * @param id         The ID of the document to find or create
     * @param parameters The parameters to pass to the constructor of the document if it needs to be created
     *
     * @return An {@link Optional} containing the {@link T object} if it was found or created, otherwise an empty {@link Optional}
     */
    public Optional<T> findOrCreateById(String id, Object... parameters) {
        try {
            Optional<T> existingObject = findById(id);

            if (existingObject.isPresent()) {
                return existingObject;
            }

            Class<?>[] parameterTypes = Arrays.stream(parameters).map(Object::getClass).toArray(Class[]::new);
            T object = entityClass.getConstructor(parameterTypes).newInstance(parameters);
            log.debug("Created new instance of " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters));

            cacheObject(id, object);
            saveToDatabase(object);

            return Optional.of(object);
        } catch (NoSuchMethodException e) {
            log.error("Could not find constructor for " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters), e);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            log.error("Error creating new instance of " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters), e);
        }

        return Optional.empty();
    }

    /**
     * Returns an {@link T object} by the given index in the cache.
     *
     * @param index The index of the object to return
     *
     * @return An {@link Optional} containing the {@link T object}
     */
    public Optional<T> getByIndex(int index) {
        return getAll()
            .stream()
            .skip(index)
            .limit(1)
            .findFirst();
    }

    /**
     * Caches an {@link T object} with the given ID.
     *
     * @param id     The ID of the object
     * @param object The {@link T object} to cache
     */
    public void cacheObject(String id, T object) {
        cache.put(id, object);
        debug("Cached document with ID " + id);
    }

    /**
     * Caches an {@link T object} with the ID of the object.
     *
     * @param object The {@link T object} to cache
     */
    public void cacheObject(T object) {
        String id = getId(object);
        cacheObject(id, object);
    }

    /**
     * Saves an {@link T object} to the database.
     *
     * @param object The {@link T object} to save
     *
     * @return The {@link UpdateResult} of the operation
     */
    public UpdateResult saveToDatabase(T object) {
        String id = getId(object);
        Document document = entityToDocument(object);
        Bson updateOperation = new Document("$set", document);

        return mongoCollection.updateOne(new Document(identifierFieldName, id), updateOperation, new UpdateOptions().upsert(true));
    }

    /**
     * Saves all {@link T objects} in the cache to the database.
     *
     * @return The {@link BulkWriteResult} of the operation
     */
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

    /**
     * Deletes an {@link T object} from the cache and the database, if it exists.
     *
     * @param id The ID of the {@link T object} to delete
     *
     * @return The {@link DeleteResult} of the operation
     */
    public DeleteResult deleteFromDatabase(String id) {
        cache.invalidate(id);
        log("Deleting document with ID " + id + " from database");

        return mongoCollection.deleteOne(Filters.eq(identifierFieldName, id));
    }

    /**
     * Finds the ID of the given {@link T object}.
     *
     * @param object The {@link T object} to find the ID of
     *
     * @return The ID of the {@link T object}
     */
    protected String getId(T object) {
        try {
            if (field == null) {
                field = object.getClass().getDeclaredField(identifierFieldName);
            }

            Object value = field.get(object);
            return value != null ? value.toString() : null;
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new RuntimeException("Error accessing identifier field: " + identifierFieldName, exception);
        }
    }

    /**
     * Converts an {@link T object} to a {@link Document}.
     *
     * @param entity The {@link T object} to convert
     *
     * @return The {@link Document} representation of the {@link T object}
     */
    public Document entityToDocument(Object entity) {
        return Document.parse(SkyBlockNerdsAPI.GSON.toJson(entity));
    }

    /**
     * Converts a {@link Document} to an {@link T object}.
     *
     * @param document The {@link Document} to convert
     *
     * @return The {@link T object} representation of the {@link Document}
     */
    protected T documentToEntity(Document document) {
        debug("Converting document to entity " + entityClass + ": " + document.toJson());
        return SkyBlockNerdsAPI.GSON.fromJson(document.toJson(), entityClass);
    }

    /**
     * Checks if the {@link Cache} is empty.
     *
     * @return {@code true} if the {@link Cache} is empty, otherwise {@code false}
     */
    public boolean isEmpty() {
        return cache.asMap().isEmpty();
    }

    /**
     * Returns all {@link T objects} in the {@link Cache}.
     *
     * @return A {@link List} of {@link T objects}
     */
    public List<T> getAll() {
        return new ArrayList<>(cache.asMap().values());
    }

    /**
     * Helper method to filter {@link T objects} in the {@link Cache} by a {@link Predicate}.
     *
     * @param filter The {@link Predicate} to filter by
     *
     * @return A {@link Collection} of {@link T objects} that match the {@link Predicate}
     */
    public Collection<T> filter(Predicate<T> filter) {
        return getAll().stream()
            .filter(filter)
            .toList();
    }

    /**
     * Helper method to iterate over all {@link T objects} in the {@link Cache}.
     *
     * @param consumer The {@link Consumer} to apply to each {@link T object}
     */
    public void forEach(Consumer<T> consumer) {
        getAll().forEach(consumer);
    }

    /**
     * Helper method to log a message with the class name.
     *
     * @param message The message to log
     */
    private void log(String message) {
        log.info("[" + getClass().getSimpleName() + "] " + message);
    }

    /**
     * Helper method to log a debug message with the class name.
     *
     * @param message The message to log
     */
    private void debug(String message) {
        log.debug("[" + getClass().getSimpleName() + "] " + message);
    }
}

