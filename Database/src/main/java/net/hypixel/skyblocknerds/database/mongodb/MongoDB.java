package net.hypixel.skyblocknerds.database.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

public class MongoDB {

    /**
     * Creates a {@link MongoClient} instance using the provided URI
     *
     * @param uri The URI to use
     *
     * @return The {@link MongoClient} instance
     */
    public static MongoClient createMongoClient(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("MongoDB URI cannot be null");
        }

        ConnectionString connectionString = new ConnectionString(uri);

        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .codecRegistry(codecRegistry)
            .build();

        return MongoClients.create(clientSettings);
    }
}
