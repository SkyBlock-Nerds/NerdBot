package net.hypixel.nerdbot.api.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
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
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.util.List;

@Log4j2
public class Database implements ServerMonitorListener {
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final ConnectionString connectionString;
    private boolean connected;

    public Database(String uri, String databaseName) {
        if (uri == null || uri.isBlank() || databaseName == null || databaseName.isBlank()) {
            log.warn("Couldn't connect to the Database due to an invalid URI or Database Name!");
            mongoClient = null;
            database = null;
            connectionString = null;
            connected = false;
            return;
        }
        connectionString = new ConnectionString(uri);
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder().applyConnectionString(connectionString).codecRegistry(codecRegistry).applyToServerSettings(builder -> builder.addServerMonitorListener(this)).build();
        mongoClient = MongoClients.create(clientSettings);
        database = mongoClient.getDatabase(databaseName);
        connected = true;
    }

    @Override
    public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
        connected = true;
    }

    @Override
    public void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
        connected = false;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public ConnectionString getConnectionString() {
        return connectionString;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        mongoClient.close();
        connected = false;
    }

    public <T> MongoCollection<T> getCollection(String collectionName, Class<T> clazz) {
        if (database == null) { return null; }
        return database.getCollection(collectionName, clazz);
    }

    public MongoCollection<Document> getCollection(String collectionName) {
        if (database == null) { return null; }
        return database.getCollection(collectionName);
    }

    public void createCollection(String collectionName) {
        if (database == null) { return; }
        database.createCollection(collectionName);
    }

    public <T> InsertOneResult insertDocument(MongoCollection<T> collection, T object) {
        if (collection == null) { return null; }
        return collection.insertOne(object);
    }

    public <T> InsertManyResult insertDocuments(MongoCollection<T> collection, List<T> objects) {
        if (collection == null) { return null; }
        return collection.insertMany(objects);
    }

    public <T> T upsertDocument(MongoCollection<T> collection, String key, Object value, T object) {
        if (collection == null) { return null; }
        FindOneAndReplaceOptions replaceOptions = new FindOneAndReplaceOptions().upsert(true);
        return collection.findOneAndReplace(Filters.eq(key, value), object, replaceOptions);
    }

    public <T> UpdateResult updateDocument(MongoCollection<T> collection, String key, Object value, Object clazz) {
        if (collection == null) { return null; }
        return collection.updateOne(Filters.eq(key, value), new Document("$set", clazz));
    }

    public UpdateResult updateDocuments(MongoCollection<Document> collection, String key, Object value, List<Document> documents) {
        if (collection == null) { return null; }
        return collection.updateMany(Filters.eq(key, value), documents);
    }

    public <T> FindIterable<T> findDocument(MongoCollection<T> collection, String key, Object value) {
        if (collection == null) { return null; }
        return collection.find(Filters.eq(key, value));
    }

    public <T> FindIterable<T> findAllDocuments(MongoCollection<T> collection) {
        if (collection == null) { return null; }
        return collection.find();
    }

    public <T> long countDocuments(MongoCollection<T> collection) {
        if (collection == null) { return -1; }
        return collection.countDocuments();
    }

    public <T> long countDocuments(MongoCollection<T> collection, Bson filter) {
        if (collection == null) { return -1; }
        return collection.countDocuments(filter);
    }

    public <T> DeleteResult deleteDocument(MongoCollection<T> collection, String key, Object value) {
        if (collection == null) { return null; }
        return collection.deleteOne(Filters.eq(key, value));
    }

    public <T> DeleteResult deleteDocuments(MongoCollection<T> collection, String key, Object value) {
        if (collection == null) { return null; }
        return collection.deleteMany(Filters.eq(key, value));
    }
}