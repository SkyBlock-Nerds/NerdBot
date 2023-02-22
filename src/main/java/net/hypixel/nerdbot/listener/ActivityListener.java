package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.user.DiscordUser;
import net.hypixel.nerdbot.util.Util;

@Log4j2
public class ActivityListener {

    private final Database database = NerdBotApp.getBot().getDatabase();

    @SubscribeEvent
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Util.getOrAddUserToCache(database, event.getUser().getId());
        log.info("User " + event.getUser().getAsTag() + " joined guild " + event.getGuild().getName());
    }

    @SubscribeEvent
    public void onGuildMemberLeave(GuildMemberRemoveEvent event) {
        database.deleteDocument(database.getCollection("users"), "discordId", event.getUser().getId());
        log.info("User " + event.getUser().getAsTag() + " left guild " + event.getGuild().getName());
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild())
            return; // Ignore Non Guild

        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) {
            return;
        }

        DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());

        if (discordUser == null)
            return; // Ignore Empty User

        GuildChannel channel = event.getGuildChannel();
        long time = System.currentTimeMillis();
        if (channel.getName().toLowerCase().contains("alpha")) {
            discordUser.getLastActivity().setLastAlphaActivity(time);
            log.info("Updating last alpha activity date for " + member.getEffectiveName() + " to " + time);
        }

        discordUser.getLastActivity().setLastGlobalActivity(time);
        log.info("Updating last global activity date for " + member.getEffectiveName() + " to " + time);
    }

    @SubscribeEvent
    public void onVoiceChannelJoin(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();

        if (member.getUser().isBot())
            return; // Ignore Bots

        DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());
        if (discordUser == null)
            return; // Ignore Empty User

        long time = System.currentTimeMillis();
        if (event.getChannelJoined() instanceof VoiceChannel) {
            VoiceChannel channel = event.getChannelJoined().asVoiceChannel();

            if (channel.getName().toLowerCase().contains("alpha")) {
                discordUser.getLastActivity().setAlphaVoiceJoinDate(time);
                log.info("Updating last alpha voice activity date for " + member.getEffectiveName() + " to " + time);
            }
        }
        discordUser.getLastActivity().setLastVoiceChannelJoinDate(time);
        log.info("Updating last global voice activity date for " + member.getEffectiveName() + " to " + time);
    }
}
