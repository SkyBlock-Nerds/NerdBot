package net.hypixel.nerdbot.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.hypixel.nerdbot.util.Logger;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.ArrayList;
import java.util.List;

public class Database {

    private static Database instance;

    private final MongoCollection<GreenlitMessage> greenlitCollection;

    private final MongoClient mongoClient;

    private boolean connected;

    private Database() {
        ConnectionString connectionString = new ConnectionString(System.getProperty("mongodb.uri"));
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .build();

        mongoClient = MongoClients.create(clientSettings);
        connected = true;
        greenlitCollection = mongoClient.getDatabase("skyblockNerds").getCollection("greenlitMessages", GreenlitMessage.class);
    }

    public static Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    public void disconnect() {
        if (connected) {
            mongoClient.close();
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public List<Document> get(String collection, String field, Object value) {
        return mongoClient.getDatabase("skyblockNerds").getCollection(collection).find(Filters.eq(field, value)).into(new ArrayList<>());
    }


    public void insertGreenlitMessage(GreenlitMessage greenlitMessage) {
        greenlitCollection.insertOne(greenlitMessage);
        Logger.info("Inserted greenlit message " + greenlitMessage.getId());
    }

    public void insertGreenlitMessages(List<GreenlitMessage> greenlitMessages) {
        greenlitCollection.insertMany(greenlitMessages);
        Logger.info("Inserted " + greenlitMessages.size() + " greenlit messages");
    }

    public void deleteGreenlitMessage(String field, Object value) {
        greenlitCollection.deleteOne(Filters.eq(field, value));
        Logger.info("Deleted greenlit message " + field + ":" + value);
    }

    public List<GreenlitMessage> getGreenlitCollection() {
        return new ArrayList<>(this.greenlitCollection.find().into(new ArrayList<>()));
    }

}
