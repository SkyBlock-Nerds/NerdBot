package net.hypixel.nerdbot.api.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.mongodb.MongoException;
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
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.NerdBotApp;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public abstract class Repository<T> {

    @Getter
    private final Cache<String, T> cache;
    @Getter
    private final MongoCollection<Document> mongoCollection;
    private final Class<T> entityClass;
    private final String identifierFieldName;
    private Field field;
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(10);

    private final ExecutorService repositoryExecutor = SHARED_EXECUTOR;
    protected Repository(MongoClient mongoClient, String databaseName, String collectionName, String identifierFieldName) {
        this(mongoClient, databaseName, collectionName, identifierFieldName, 0, null);
    }

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

    public CompletableFuture<Void> loadAllDocumentsIntoCacheAsync() {
        return CompletableFuture.supplyAsync(() -> {
            log("Loading ALL documents from database into cache asynchronously");
            
            List<Document> documents = new ArrayList<>();
            mongoCollection.find().into(documents);
            return documents;
        }, repositoryExecutor)
        .thenCompose(documents -> {
            List<CompletableFuture<Void>> futures = documents.stream()
                .map(document -> CompletableFuture.runAsync(() -> {
                    T object = documentToEntity(document);

                    if (cache.getIfPresent(getId(object)) != null) {
                        debug("Document with ID " + getId(object) + " already exists in cache");
                        return;
                    }

                    cacheObject(object);
                }, repositoryExecutor))
                .toList();
                
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        });
    }

    public List<T> getAllDocuments() {
        List<T> documents = new ArrayList<>();

        mongoCollection.find()
            .forEach(document -> {
                T object = documentToEntity(document);
                documents.add(object);
            });

        return documents;
    }

    public CompletableFuture<List<T>> getAllDocumentsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<T> documents = new ArrayList<>();

            mongoCollection.find()
                .forEach(document -> {
                    T object = documentToEntity(document);
                    documents.add(object);
                });

            return documents;
        }, repositoryExecutor);
    }

    public T findById(String id) {
        T cachedObject = cache.getIfPresent(id);
        if (cachedObject != null) {
            debug("Found document with ID " + id + " in cache");
            return cachedObject;
        }

        try {
            Document document = mongoCollection.find(new Document(identifierFieldName, id)).first();
            if (document != null) {
                T object = documentToEntity(document);
                cacheObject(id, object);

                return object;
            }
        } catch (MongoException e) {
            log.error("Error finding document with ID " + id, e);
            return null;
        }

        debug("Could not find document with ID " + id + " in cache or database");
        return null;
    }

    public CompletableFuture<T> findByIdAsync(String id) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, repositoryExecutor);
    }

    public T findOrCreateById(String id, Object... parameters) {
        try {
            T existingObject = findById(id);

            if (existingObject != null) {
                return existingObject;
            }

            List<Class<?>> constructorParameters = new ArrayList<>();
            for (Object param : parameters) {
                constructorParameters.add(param != null ? param.getClass() : Object.class);
            }
            T object = entityClass.getConstructor(constructorParameters.toArray(new Class[0])).newInstance(parameters);

            log.debug("Created new instance of " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters));

            cacheObject(id, object);
            saveToDatabase(object);

            return object;
        } catch (NoSuchMethodException e) {
            log.error("Could not find constructor for " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters));
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            log.error("Error creating new instance of " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters));
        }

        return null;
    }

    public CompletableFuture<T> findOrCreateByIdAsync(String id, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                T existingObject = findById(id);

                if (existingObject != null) {
                    return existingObject;
                }

                List<Class<?>> constructorParameters = new ArrayList<>();
                for (Object param : parameters) {
                    constructorParameters.add(param != null ? param.getClass() : Object.class);
                }
                T object = entityClass.getConstructor(constructorParameters.toArray(new Class[0])).newInstance(parameters);

                log.debug("Created new instance of " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters));

                cacheObject(id, object);
                saveToDatabase(object);

                return object;
            } catch (NoSuchMethodException e) {
                log.error("Could not find constructor for " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters));
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                log.error("Error creating new instance of " + entityClass.getSimpleName() + " with parameters " + Arrays.toString(parameters));
            }

            return null;
        }, repositoryExecutor);
    }

    public T getByIndex(int index) {
        return getAll()
            .stream()
            .skip(index)
            .limit(1)
            .findFirst()
            .orElse(null);
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

    public CompletableFuture<UpdateResult> saveToDatabaseAsync(T object) {
        return CompletableFuture.supplyAsync(() -> {
            String id = getId(object);
            Document document = entityToDocument(object);
            Bson updateOperation = new Document("$set", document);

            return mongoCollection.updateOne(new Document(identifierFieldName, id), updateOperation, new UpdateOptions().upsert(true));
        }, repositoryExecutor);
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

    public CompletableFuture<BulkWriteResult> saveAllToDatabaseAsync() {
        return CompletableFuture.supplyAsync(() -> {
            log("Saving all documents in cache to database asynchronously (found " + cache.asMap().size() + ")");

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
        }, repositoryExecutor);
    }

    public DeleteResult deleteFromDatabase(String id) {
        cache.invalidate(id);
        log("Deleting document with ID " + id + " from database");

        return mongoCollection.deleteOne(Filters.eq(identifierFieldName, id));
    }

    public CompletableFuture<DeleteResult> deleteFromDatabaseAsync(String id) {
        return CompletableFuture.supplyAsync(() -> {
            cache.invalidate(id);
            log("Deleting document with ID " + id + " from database asynchronously");

            return mongoCollection.deleteOne(Filters.eq(identifierFieldName, id));
        }, repositoryExecutor);
    }

    protected String getId(T entity) {
        try {
            if (field == null) {
                field = entity.getClass().getDeclaredField(identifierFieldName);
            }

            Object value = field.get(entity);
            return value != null ? value.toString() : null;
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new RuntimeException("Error accessing identifier field: " + identifierFieldName, exception);
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
