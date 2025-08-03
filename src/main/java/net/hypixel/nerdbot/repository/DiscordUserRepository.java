package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.api.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.repository.Repository;
import net.hypixel.nerdbot.util.DiscordUtils;

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

                    if (discordUser.getLanguage() == null) {
                        log.info("Language for " + discordUser.getDiscordId() + " was null. Setting to default values!");
                        discordUser.setLanguage(UserLanguage.ENGLISH);
                    }

                    cacheObject(discordUser);

                    if (discordUser.getBirthdayData().isBirthdaySet()) {
                        discordUser.scheduleBirthdayReminder(discordUser.getBirthdayData().getBirthdayThisYear());
                    }
                });
            })
        );
    }

    public Member getMemberById(String id) {
        return DiscordUtils.getMainGuild().getMemberById(id);
    }

    public User getUserById(String id) {
        return DiscordUtils.getMainGuild().getJDA().getUserById(id);
    }
}
