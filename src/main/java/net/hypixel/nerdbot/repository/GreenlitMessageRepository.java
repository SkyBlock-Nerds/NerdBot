package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.repository.Repository;

public class GreenlitMessageRepository extends Repository<GreenlitMessage> {

    public GreenlitMessageRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "greenlit_messages", "messageId");
    }

    @Override
    protected String getId(GreenlitMessage entity) {
        return entity.getMessageId();
    }
}
