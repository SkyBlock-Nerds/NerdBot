package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class UserGrabberFeature extends BotFeature {

    @Override
    public void onStart() {
        if (NerdBotApp.getBot().isReadOnly()) {
            log.error("Bot is in read-only mode, skipping user grabber task!");
            return;
        }

        Guild guild = Util.getGuild(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            log.error("Couldn't find the guild specified in the bot config!");
            return;
        }

        Database database = NerdBotApp.getBot().getDatabase();
        if (!database.isConnected()) {
            log.error("Can't initiate feature as the database is not connected!");
            return;
        }

        log.info("Grabbing users from guild " + guild.getName());
        List<DiscordUser> users = database.getCollection("users", DiscordUser.class).find().into(new ArrayList<>());

        guild.loadMembers(member -> {
            if (member.getUser().isBot()) {
                return;
            }

            if (users.isEmpty()) {
                return;
            }

            DiscordUser discordUser = getUser(users, member.getId());
            if (discordUser == null) {
                discordUser = new DiscordUser(member.getId(), new ArrayList<>(), new ArrayList<>(), new LastActivity());
            }

            if (discordUser.getLastActivity() == null) {
                log.info("Last activity for " + member.getEffectiveName() + " was null. Setting to default values!");
                discordUser.setLastActivity(new LastActivity());
            }

            database.upsertDocument(database.getCollection("users", DiscordUser.class), "discordId", discordUser.getDiscordId(), discordUser);
        }).onSuccess(aVoid -> log.info("Finished grabbing users from guild " + guild.getName())).onError(Throwable::printStackTrace);
    }

    @Override
    public void onEnd() {
        log.info("Finished grabbing all users from guild " + Util.getGuild(NerdBotApp.getBot().getConfig().getGuildId()).getName());
    }

    private DiscordUser getUser(List<DiscordUser> users, String id) {
        if (users != null) {
            return users.stream().filter(user -> user.getDiscordId().equalsIgnoreCase(id)).findFirst().orElse(null);
        }
        return null;
    }
}
