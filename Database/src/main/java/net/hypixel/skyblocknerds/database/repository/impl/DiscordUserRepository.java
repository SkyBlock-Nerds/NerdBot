package net.hypixel.skyblocknerds.database.repository.impl;

import com.mongodb.client.MongoClient;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.database.repository.Repository;

import java.util.concurrent.TimeUnit;

public class DiscordUserRepository extends Repository<DiscordUser> {

    public DiscordUserRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "users", "discordId", 1, TimeUnit.DAYS);
    }

    @Override
    protected String getId(DiscordUser object) {
        return object.getDiscordId();
    }

    @Override
    public void loadAllDocumentsIntoCache() {
        super.loadAllDocumentsIntoCache();

        // TODO other stuff
    }
}
