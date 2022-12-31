package net.hypixel.nerdbot.listener;

import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.user.DiscordUser;

@Log4j2
public class ActivityListener {

    @SubscribeEvent
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Database.getInstance().getOrAddUserToCache(event.getUser().getId());
        log.info("User " + event.getUser().getAsTag() + " joined guild " + event.getGuild().getName());
    }

    @SubscribeEvent
    public void onGuildMemberLeave(GuildMemberRemoveEvent event) {
        Database.getInstance().deleteUser("discordId", event.getUser().getId());
        log.info("User " + event.getUser().getAsTag() + " left guild " + event.getGuild().getName());
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        Member member = event.getMember();
        GuildChannel channel = event.getGuildChannel();

        if (!event.isFromGuild() || member == null || member.getUser().isBot()) {
            return;
        }

        DiscordUser discordUser = Database.getInstance().getOrAddUserToCache(member.getId());
        long time = System.currentTimeMillis();

        if (channel.getName().contains("alpha")) {
            discordUser.getLastActivity().setLastAlphaActivity(time);
            log.info("Updating last alpha activity date for " + member.getEffectiveName() + " to " + time);
        }

        discordUser.getLastActivity().setLastGlobalActivity(time);
        log.info("Updating last global activity date for " + member.getEffectiveName() + " to " + time);
    }

    @SubscribeEvent
    public void onVoiceChannelJoin(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();

        if (member.getUser().isBot()) {
            return;
        }

        DiscordUser discordUser = Database.getInstance().getOrAddUserToCache(member.getId());
        discordUser.getLastActivity().setLastVoiceChannelJoinDate(System.currentTimeMillis());
        log.info("Updating last voice channel activity date for " + member.getEffectiveName() + " to " + System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onSlashCommand(GuildSlashEvent event) {
        Member member = event.getMember();

        if (member.getUser().isBot()) {
            return;
        }

        DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());
        if (discordUser == null) {
            return;
        }

        discordUser.getLastActivity().setLastItemGenUsage(System.currentTimeMillis());
        log.info("Updating last item-gen usage date for " + member.getEffectiveName() + " to " + System.currentTimeMillis());
    }
}
