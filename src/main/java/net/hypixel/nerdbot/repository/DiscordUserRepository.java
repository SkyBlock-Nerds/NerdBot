package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.api.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.repository.Repository;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Log4j2
public class DiscordUserRepository extends Repository<DiscordUser> {

    public DiscordUserRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "users", "discordId", 1, TimeUnit.DAYS);
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

            if (discordUser.getLastActivity().getChannelActivityHistory() == null) {
                log.info("Channel activity history for " + discordUser.getDiscordId() + " was null. Setting to default values!");
                discordUser.getLastActivity().setChannelActivityHistory(new ArrayList<>());
            }

            discordUser.getLastActivity().getChannelActivityHistory().forEach(channelActivityEntry -> {
                if (channelActivityEntry.getMonthlyMessageCount() == null) {
                    log.info("Monthly message count for " + discordUser.getDiscordId() + " was null. Setting to default values!");
                    channelActivityEntry.setMonthlyMessageCount(new HashMap<>());
                }
            });

            if (!discordUser.getLastActivity().getChannelActivity().isEmpty()) {
                log.info("Old channel activity for " + discordUser.getDiscordId() + " was not empty! (size: " + discordUser.getLastActivity().getChannelActivity().size() + ")");
                discordUser.getLastActivity().getChannelActivity().forEach((channelId, messageCount) -> {
                    if (Util.getMainGuild().getTextChannelById(channelId) == null) {
                        log.info("Channel " + channelId + " was not found in the guild! Skipping...");
                        return;
                    }

                    discordUser.getLastActivity().addChannelHistory(Util.getMainGuild().getTextChannelById(channelId), messageCount, System.currentTimeMillis());
                });
                discordUser.getLastActivity().getChannelActivity().clear();
                discordUser.getLastActivity().setChannelActivity(null);
            }

            discordUser.getLastActivity().purgeOldHistory();

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
    }

    public Member getMemberById(String id) {
        return Util.getMainGuild().getMemberById(id);
    }

    public User getUserById(String id) {
        return Util.getMainGuild().getJDA().getUserById(id);
    }
}
