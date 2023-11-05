package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.repository.Repository;

@Log4j2
public class DiscordUserRepository extends Repository<DiscordUser> {

    public DiscordUserRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "users");
    }

    @Override
    protected String getId(DiscordUser entity) {
        return entity.getDiscordId();
    }
}
