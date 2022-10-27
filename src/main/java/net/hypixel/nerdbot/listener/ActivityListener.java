package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.user.DiscordUser;

public class ActivityListener {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        Member member = event.getMember();
        GuildChannel channel = event.getGuildChannel();

        if (!event.isFromGuild() || member == null || member.getUser().isBot()) {
            return;
        }

        DiscordUser discordUser = Database.getInstance().getOrAddUserToCache(member.getId());
        long time = System.currentTimeMillis();

        if (channel.getName().contains("alpha")) {
            discordUser.getLastActivity().setLastAlphaActivity(time);
            NerdBotApp.LOGGER.info("Updating last alpha activity date for " + member.getEffectiveName() + " to " + time);
        }

        discordUser.getLastActivity().setLastGlobalActivity(time);
        NerdBotApp.LOGGER.info("Updating last global activity date for " + member.getEffectiveName() + " to " + time);
    }

    @SubscribeEvent
    public void onVoiceChannelJoin(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();

        if (member.getUser().isBot()) {
            return;
        }

        DiscordUser discordUser = Database.getInstance().getOrAddUserToCache(member.getId());
        discordUser.getLastActivity().setLastVoiceChannelJoinDate(System.currentTimeMillis());
        NerdBotApp.LOGGER.info("Updating last voice channel activity date for " + member.getEffectiveName() + " to " + System.currentTimeMillis());
    }
}
