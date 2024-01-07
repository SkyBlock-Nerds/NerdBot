package net.hypixel.nerdbot.cache;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.forum.GenericForumTagEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Log4j2
public class ChannelCache {

    private static final Map<String, GuildChannel> CHANNEL_CACHE = new HashMap<>();

    public ChannelCache() {
        Util.getMainGuild().getChannels().forEach(channel -> {
            CHANNEL_CACHE.put(channel.getId(), channel);
            log.debug("Cached channel '" + channel.getName() + "' (ID: " + channel.getId() + ")");
        });
    }

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        CHANNEL_CACHE.put(event.getChannel().getId(), event.getChannel().asGuildChannel());
        log.debug("Cached channel '" + event.getChannel().getName() + "' (ID: " + event.getChannel().getId() + ") because it was created");
    }

    @SubscribeEvent
    public void onChannelUpdate(GenericChannelUpdateEvent<?> event) {
        CHANNEL_CACHE.put(event.getChannel().getId(), event.getChannel().asGuildChannel());
        log.debug("Cached channel '" + event.getChannel().getName() + "' (ID: " + event.getChannel().getId() + ") because it was updated");
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        CHANNEL_CACHE.remove(event.getChannel().getId());
        log.debug("Removed channel from cache '" + event.getChannel().getName() + "' (ID: " + event.getChannel().getId() + ") because it was deleted");
    }

    @SubscribeEvent
    public void onForumChannelUpdate(GenericForumTagEvent event) {
        CHANNEL_CACHE.put(event.getChannel().getId(), event.getChannel());
        log.debug("Cached channel '" + event.getChannel().getName() + "' (ID: " + event.getChannel().getId() + ") because it was updated");
    }

    public static Optional<GuildChannel> getChannelById(String channelId) {
        return Optional.ofNullable(CHANNEL_CACHE.get(channelId));
    }

    public static Optional<GuildChannel> getChannelByName(String channelName) {
        return CHANNEL_CACHE.values().stream()
            .filter(guildChannel -> guildChannel.getName().equals(channelName))
            .findFirst();
    }

    public static Optional<TextChannel> getTextChannelByName(String channelName) {
        return getChannelByName(channelName)
            .map(channel -> (TextChannel) channel);
    }

    public static Optional<TextChannel> getTextChannelById(String channelId) {
        return getChannelById(channelId)
            .map(channel -> (TextChannel) channel);
    }

    public static Optional<ForumChannel> getForumChannelByName(String channelName) {
        return getChannelByName(channelName)
            .map(channel -> (ForumChannel) channel);
    }

    public static Optional<ForumChannel> getForumChannelById(String channelId) {
        return getChannelById(channelId)
            .map(channel -> (ForumChannel) channel);
    }

    public static Optional<ForumChannel> getModMailChannel() {
        return getChannelById(NerdBotApp.getBot().getConfig().getModMailConfig().getChannelId())
            .map(channel -> (ForumChannel) channel);
    }

    public static Optional<TextChannel> getLogChannel() {
        return getChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getLogChannelId())
            .map(channel -> (TextChannel) channel);
    }

    public static Optional<TextChannel> getVerifyLogChannel() {
        return getChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getVerifyLogChannelId())
            .map(channel -> (TextChannel) channel);
    }

    public static Optional<TextChannel> getRequestedReviewChannel() {
        return getChannelById(NerdBotApp.getBot().getConfig().getSuggestionConfig().getRequestedReviewForumId())
            .map(channel -> (TextChannel) channel);
    }
}
