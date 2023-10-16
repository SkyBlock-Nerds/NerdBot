package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.repository.CachedMongoRepository;

public class GreenlitMessageRepository extends CachedMongoRepository<GreenlitMessage> {

    public GreenlitMessageRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "greenlit_messages");
    }

    @Override
    protected String getId(GreenlitMessage entity) {
        return entity.getMessageId();
    }
}
