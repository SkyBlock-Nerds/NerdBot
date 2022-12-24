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
public class MongoDatabase implements ServerMonitorListener {

    private final MongoClient mongoClient;
    private final com.mongodb.client.MongoDatabase database;
    private final ConnectionString connectionString;
    private boolean connected;

    public MongoDatabase(String uri, String databaseName) {
        connectionString = new ConnectionString(uri);
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .applyToServerSettings(builder -> builder.addServerMonitorListener(this))
                .build();

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

    public com.mongodb.client.MongoDatabase getDatabase() {
        return database;
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        mongoClient.close();
        connected = false;
        log.info("Database is now disconnected!");
    }

    public <T> MongoCollection<T> getCollection(String collectionName, Class<T> clazz) {
        return database.getCollection(collectionName, clazz);
    }

    public MongoCollection<Document> getCollection(String collectionName) {
        return database.getCollection(collectionName);
    }

    public void createCollection(String collectionName) {
        database.createCollection(collectionName);
    }

    public <T> InsertOneResult insertDocument(MongoCollection<T> collection, T object) {
        return collection.insertOne(object);
    }

    public <T> InsertManyResult insertDocuments(MongoCollection<T> collection, List<T> objects) {
        return collection.insertMany(objects);
    }

    public <T> T upsertDocument(MongoCollection<T> collection, String key, Object value, T object) {
        FindOneAndReplaceOptions replaceOptions = new FindOneAndReplaceOptions().upsert(true);
        return collection.findOneAndReplace(Filters.eq(key, value), object, replaceOptions);
    }

    public <T> UpdateResult updateDocument(MongoCollection<T> collection, String key, Object value, Object object) {
        return collection.updateOne(Filters.eq(key, value), new Document("$set", object));
    }

    public UpdateResult updateDocuments(MongoCollection<Document> collection, String key, Object value, List<Document> documents) {
        return collection.updateMany(Filters.eq(key, value), documents);
    }

    public <T> FindIterable<T> findDocument(MongoCollection<T> collection, String key, Object value) {
        return collection.find(Filters.eq(key, value));
    }

    public <T> FindIterable<T> findAllDocuments(MongoCollection<T> collection) {
        return collection.find();
    }

    public <T> long countDocuments(MongoCollection<T> collection) {
        return collection.countDocuments();
    }

    public <T> long countDocuments(MongoCollection<T> collection, Bson filter) {
        return collection.countDocuments(filter);
    }

    public <T> DeleteResult deleteDocument(MongoCollection<T> collection, String key, Object value) {
        return collection.deleteOne(Filters.eq(key, value));
    }

    public <T> DeleteResult deleteDocuments(MongoCollection<T> collection, String key, Object value) {
        return collection.deleteMany(Filters.eq(key, value));
    }
}
