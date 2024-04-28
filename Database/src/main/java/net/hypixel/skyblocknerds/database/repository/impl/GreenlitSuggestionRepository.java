package net.hypixel.skyblocknerds.database.repository.impl;

import com.mongodb.client.MongoClient;
import net.hypixel.skyblocknerds.database.objects.suggestion.GreenlitSuggestion;
import net.hypixel.skyblocknerds.database.repository.Repository;

import java.util.concurrent.TimeUnit;

public class GreenlitSuggestionRepository extends Repository<GreenlitSuggestion> {

    public GreenlitSuggestionRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "greenlit_suggestions", "messageId", 1, TimeUnit.DAYS);
    }

    @Override
    protected String getId(GreenlitSuggestion entity) {
        return entity.getMessageId();
    }
}