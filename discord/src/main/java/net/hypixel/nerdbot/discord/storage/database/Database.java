package net.hypixel.nerdbot.discord.storage.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.exception.RepositoryException;
import net.hypixel.nerdbot.discord.storage.repository.RepositoryManager;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class Database implements ServerMonitorListener {

    private static final FindOneAndReplaceOptions REPLACE_OPTIONS = new FindOneAndReplaceOptions().upsert(true);
    @Getter
    private final ConnectionString connectionString;
    @Getter
    private final RepositoryManager repositoryManager = new RepositoryManager();
    private final ExecutorService databaseExecutor = Executors.newCachedThreadPool();
    @Getter
    private MongoClient mongoClient = null;
    @Getter
    private boolean connected;

    public Database(String uri, String databaseName) {
        if (uri == null || uri.isBlank() || databaseName == null || databaseName.isBlank()) {
            log.warn("Database URI or database name is null or blank, so not initiating database connection!");
            mongoClient = null;
            connectionString = null;
            connected = false;
            return;
        }

        connectionString = new ConnectionString(uri);

        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .codecRegistry(codecRegistry)
            .applyToServerSettings(builder -> builder.addServerMonitorListener(this))
            .build();

        try {
            mongoClient = MongoClients.create(clientSettings);
            connected = true;
        } catch (MongoException exception) {
            log.error("Failed to create MongoDB client!", exception);
            connected = false;
        }

        if (connected) {
            try {
                repositoryManager.registerRepositoriesFromPackage("net.hypixel.nerdbot.discord.storage.database.repository", mongoClient, databaseName);
            } catch (RepositoryException exception) {
                log.error("Failed to register repositories!", exception);
            }
        } else {
            log.warn("Skipping repository registration because MongoDB is not connected");
        }
    }

    @Override
    public void serverHeartbeatSucceeded(@NotNull ServerHeartbeatSucceededEvent event) {
        connected = true;
    }

    @Override
    public void serverHeartbeatFailed(@NotNull ServerHeartbeatFailedEvent event) {
        connected = false;
    }

    public void disconnect() {
        if (mongoClient != null) {
            mongoClient.close();
        }
        databaseExecutor.shutdown();
        connected = false;
    }

    @Nullable
    public <T> InsertOneResult insertDocument(MongoCollection<T> collection, T object) {
        if (collection == null) {
            return null;
        }
        return collection.insertOne(object);
    }

    public <T> CompletableFuture<InsertOneResult> insertDocumentAsync(MongoCollection<T> collection, T object) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.insertOne(object);
        }, databaseExecutor);
    }

    @Nullable
    public <T> InsertManyResult insertDocuments(MongoCollection<T> collection, List<T> objects) {
        if (collection == null) {
            return null;
        }
        return collection.insertMany(objects);
    }

    public <T> CompletableFuture<InsertManyResult> insertDocumentsAsync(MongoCollection<T> collection, List<T> objects) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.insertMany(objects);
        }, databaseExecutor);
    }

    public <T> void upsertDocument(MongoCollection<T> collection, String key, Object value, T object) {
        if (collection == null) {
            return;
        }
        collection.findOneAndReplace(Filters.eq(key, value), object, REPLACE_OPTIONS);
    }

    public <T> CompletableFuture<T> upsertDocumentAsync(MongoCollection<T> collection, String key, Object value, T object) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.findOneAndReplace(Filters.eq(key, value), object, REPLACE_OPTIONS);
        }, databaseExecutor);
    }

    @Nullable
    public <T> UpdateResult updateDocument(MongoCollection<T> collection, String key, Object value, Object clazz) {
        if (collection == null) {
            return null;
        }
        return collection.updateOne(Filters.eq(key, value), new Document("$set", clazz));
    }

    public <T> CompletableFuture<UpdateResult> updateDocumentAsync(MongoCollection<T> collection, String key, Object value, Object clazz) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.updateOne(Filters.eq(key, value), new Document("$set", clazz));
        }, databaseExecutor);
    }

    @Nullable
    public UpdateResult updateDocuments(MongoCollection<Document> collection, String key, Object value, List<Document> documents) {
        if (collection == null) {
            return null;
        }
        return collection.updateMany(Filters.eq(key, value), documents);
    }

    @Nullable
    public <T> FindIterable<T> findDocument(MongoCollection<T> collection, String key, Object value) {
        if (collection == null) {
            return null;
        }
        return collection.find(Filters.eq(key, value));
    }

    public <T> CompletableFuture<FindIterable<T>> findDocumentAsync(MongoCollection<T> collection, String key, Object value) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.find(Filters.eq(key, value));
        }, databaseExecutor);
    }

    @Nullable
    public <T> FindIterable<T> findDocument(MongoCollection<T> collection, Bson filter) {
        if (collection == null || filter == null) {
            return null;
        }

        return collection.find(filter);
    }

    @Nullable
    public <T> FindIterable<T> findAllDocuments(MongoCollection<T> collection) {
        if (collection == null) {
            return null;
        }
        return collection.find();
    }

    public <T> CompletableFuture<FindIterable<T>> findAllDocumentsAsync(MongoCollection<T> collection) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.find();
        }, databaseExecutor);
    }

    public <T> long countDocuments(MongoCollection<T> collection) {
        if (collection == null) {
            return -1;
        }
        return collection.countDocuments();
    }

    public <T> long countDocuments(MongoCollection<T> collection, Bson filter) {
        if (collection == null) {
            return -1;
        }
        return collection.countDocuments(filter);
    }

    @Nullable
    public <T> DeleteResult deleteDocument(MongoCollection<T> collection, String key, Object value) {
        if (collection == null) {
            return null;
        }
        return collection.deleteOne(Filters.eq(key, value));
    }

    public <T> CompletableFuture<DeleteResult> deleteDocumentAsync(MongoCollection<T> collection, String key, Object value) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.deleteOne(Filters.eq(key, value));
        }, databaseExecutor);
    }

    @Nullable
    public <T> DeleteResult deleteDocuments(MongoCollection<T> collection, String key, Object value) {
        if (collection == null) {
            return null;
        }
        return collection.deleteMany(Filters.eq(key, value));
    }

    public <T> CompletableFuture<DeleteResult> deleteDocumentsAsync(MongoCollection<T> collection, String key, Object value) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return null;
            }
            return collection.deleteMany(Filters.eq(key, value));
        }, databaseExecutor);
    }
}
