package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.user.DiscordUser;
import net.hypixel.nerdbot.api.database.user.LastActivity;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class UserGrabberFeature extends BotFeature {

    @Override
    public void onStart() {
        Guild guild = Util.getGuild(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            log.error("Couldn't find the guild specified in the bot config!");
            return;
        }

        log.info("Grabbing users from guild " + guild.getName());
        List<DiscordUser> users = Database.getInstance().getUsers();

        guild.loadMembers(member -> {
            if (member.getUser().isBot()) {
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

            if (users.contains(discordUser)) {
                Database.getInstance().updateUser(discordUser);
            } else {
                Database.getInstance().insertUser(discordUser);
            }
        }).onSuccess(v -> log.info("Finished grabbing users from guild " + guild.getName()));
    }

    @Override
    public void onEnd() {
    }

    private boolean containsUser(List<DiscordUser> users, String id) {
        return users.stream().anyMatch(user -> user.getDiscordId().equals(id));
    }

    private DiscordUser getUser(List<DiscordUser> users, String id) {
        return users.stream().filter(user -> user.getDiscordId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }
}
