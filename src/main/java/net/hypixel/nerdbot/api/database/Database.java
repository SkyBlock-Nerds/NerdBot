package net.hypixel.nerdbot.api.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.api.repository.RepositoryManager;
import net.hypixel.nerdbot.util.exception.RepositoryException;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Log4j2
public class Database implements ServerMonitorListener {

    private static final FindOneAndReplaceOptions REPLACE_OPTIONS = new FindOneAndReplaceOptions().upsert(true);

    private MongoClient mongoClient = null;
    private final ConnectionString connectionString;
    private boolean connected;
    private final RepositoryManager repositoryManager = new RepositoryManager();

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

        try {
            repositoryManager.registerRepositoriesFromPackage("net.hypixel.nerdbot.repository", mongoClient, databaseName);
        } catch (RepositoryException exception) {
            exception.printStackTrace();
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

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public ConnectionString getConnectionString() {
        return connectionString;
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        mongoClient.close();
        connected = false;
    }

    @Nullable
    public <T> InsertOneResult insertDocument(MongoCollection<T> collection, T object) {
        if (collection == null) {
            return null;
        }
        return collection.insertOne(object);
    }

    @Nullable
    public <T> InsertManyResult insertDocuments(MongoCollection<T> collection, List<T> objects) {
        if (collection == null) {
            return null;
        }
        return collection.insertMany(objects);
    }

    public <T> void upsertDocument(MongoCollection<T> collection, String key, Object value, T object) {
        if (collection == null) {
            return;
        }
        collection.findOneAndReplace(Filters.eq(key, value), object, REPLACE_OPTIONS);
    }

    @Nullable
    public <T> UpdateResult updateDocument(MongoCollection<T> collection, String key, Object value, Object clazz) {
        if (collection == null) {
            return null;
        }
        return collection.updateOne(Filters.eq(key, value), new Document("$set", clazz));
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

    @Nullable
    public <T> DeleteResult deleteDocuments(MongoCollection<T> collection, String key, Object value) {
        if (collection == null) {
            return null;
        }
        return collection.deleteMany(Filters.eq(key, value));
    }

    public RepositoryManager getRepositoryManager() {
        return repositoryManager;
    }
}