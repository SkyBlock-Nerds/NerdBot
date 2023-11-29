package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.api.database.model.user.BirthdayData;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.repository.Repository;

@Log4j2
public class DiscordUserRepository extends Repository<DiscordUser> {

    public DiscordUserRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "users", "discordId");
    }

    @Override
    protected String getId(DiscordUser entity) {
        return entity.getDiscordId();
    }

    @Override
    public void loadAllDocumentsIntoCache() {
        super.loadAllDocumentsIntoCache();

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

            if (discordUser.getBirthdayData().isBirthdaySet()) {
                discordUser.scheduleBirthdayReminder(discordUser.getBirthdayData().getBirthdayThisYear());
            }
        });
    }
}
