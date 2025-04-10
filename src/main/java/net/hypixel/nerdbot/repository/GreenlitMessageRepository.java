package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.internalapi.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.internalapi.repository.Repository;

import java.util.concurrent.TimeUnit;

public class GreenlitMessageRepository extends Repository<GreenlitMessage> {

    public GreenlitMessageRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "greenlit_messages", "messageId", 1, TimeUnit.DAYS);
    }

    @Override
    protected String getId(GreenlitMessage entity) {
        return entity.getMessageId();
    }
}
