package net.hypixel.nerdbot.feature.impl;

import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.database.Database;
import net.hypixel.nerdbot.database.DiscordUser;
import net.hypixel.nerdbot.feature.BotFeature;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;

public class UserGrabberFeature extends BotFeature {

    @Override
    public void onStart() {
        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null)
            throw new RuntimeException("Couldn't find the guild specified in the bot config!");

        Logger.info("Grabbing users from guild " + guild.getName());
        List<DiscordUser> existingUsers = Database.getInstance().getUsers();
        guild.loadMembers(member -> {
            if (!member.getUser().isBot() && !containsUser(existingUsers, member.getId())) {
                Logger.info("Adding DiscordUser " + member.getUser().getAsTag() + " to database");
                DiscordUser discordUser = new DiscordUser(member.getId(), 0, 0, 0, null);
                Database.getInstance().insertUser(discordUser);
            }
        });
        Logger.info("Finished grabbing users from guild " + guild.getName());
    }

    @Override
    public void onEnd() {

    }

    private boolean containsUser(List<DiscordUser> users, String id) {
        for (DiscordUser user : users) {
            if (user.getDiscordId().equals(id))
                return true;
        }
        return false;
    }
}
