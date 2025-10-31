package net.hypixel.nerdbot.discord.database.model.repository;

import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.discord.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.discord.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.core.api.repository.Repository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DiscordUserRepository extends Repository<DiscordUser> {

    public DiscordUserRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "users", "discordId", 1, TimeUnit.DAYS);
    }

    @Override
    protected String getId(DiscordUser entity) {
        return entity.getDiscordId();
    }

    @Override
    public CompletableFuture<Void> loadAllDocumentsIntoCacheAsync() {
        return super.loadAllDocumentsIntoCacheAsync().thenCompose(unused ->
            CompletableFuture.runAsync(() -> {
                getAll().forEach(discordUser -> {
                    if (discordUser.getLastActivity() == null) {
                        log.info("Last activity for " + discordUser.getDiscordId() + " was null. Setting to default values!");
                        discordUser.setLastActivity(new LastActivity());
                    }

                    if (discordUser.getBirthdayData() == null) {
                        log.info("Birthday data for " + discordUser.getDiscordId() + " was null. Setting to default values!");
                        discordUser.setBirthdayData(new BirthdayData());
                    }

                    cacheObject(discordUser);
                });
            })
        );
    }
}