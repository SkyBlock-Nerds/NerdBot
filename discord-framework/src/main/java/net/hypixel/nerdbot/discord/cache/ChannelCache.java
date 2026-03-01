package net.hypixel.nerdbot.discord.cache;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ChannelCache {

    private static final Map<String, GuildChannel> CHANNEL_CACHE = new HashMap<>();

    public ChannelCache() {
        DiscordUtils.getMainGuild().getChannels().forEach(channel -> {
            CHANNEL_CACHE.put(channel.getId(), channel);
            log.debug("Cached channel '{}' (ID: {})", channel.getName(), channel.getId());
        });
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
            .map(TextChannel.class::cast);
    }

    public static Optional<TextChannel> getTextChannelById(String channelId) {
        return getChannelById(channelId)
            .map(TextChannel.class::cast);
    }

    public static Optional<ForumChannel> getForumChannelByName(String channelName) {
        return getChannelByName(channelName)
            .filter(channel -> channel instanceof ForumChannel)
            .map(ForumChannel.class::cast);
    }

    public static Optional<ForumChannel> getForumChannelById(String channelId) {
        return getChannelById(channelId)
            .filter(channel -> channel instanceof ForumChannel)
            .map(ForumChannel.class::cast);
    }

    public static Optional<Category> getCategoryByName(String categoryName) {
        return getChannelByName(categoryName)
            .filter(channel -> channel instanceof Category)
            .map(Category.class::cast);
    }

    public static Optional<Category> getCategoryById(String categoryId) {
        return getChannelById(categoryId)
            .filter(channel -> channel instanceof Category)
            .map(Category.class::cast);
    }

    public static Optional<TextChannel> getLogChannel() {
        return getChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getLogChannelId())
            .map(TextChannel.class::cast);
    }

    public static void sendToLogChannel(MessageEmbed embed, MessageEmbed... additional) {
        getLogChannel().ifPresentOrElse(
            channel -> channel.sendMessageEmbeds(embed, additional).queue(),
            () -> log.warn("Log channel not found!")
        );
    }

    public static void sendMessageToLogChannel(String message) {
        getLogChannel().ifPresentOrElse(
            channel -> channel.sendMessage(message).queue(),
            () -> log.warn("Log channel not found!")
        );
    }

    public static Optional<TextChannel> getVerifyLogChannel() {
        return getChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getVerifyLogChannelId())
            .map(TextChannel.class::cast);
    }

    public static Optional<TextChannel> getRequestedReviewChannel() {
        return getChannelById(DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig().getReviewRequestConfig().getChannelId())
            .map(TextChannel.class::cast);
    }

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        CHANNEL_CACHE.put(event.getChannel().getId(), event.getChannel().asGuildChannel());
        log.debug("Cached channel '{}' (ID: {}) because it was created", event.getChannel().getName(), event.getChannel().getId());
    }

    @SubscribeEvent
    public void onChannelUpdate(GenericChannelUpdateEvent<?> event) {
        CHANNEL_CACHE.put(event.getChannel().getId(), event.getChannel().asGuildChannel());
        log.debug("Cached channel '{}' (ID: {}) because it was updated", event.getChannel().getName(), event.getChannel().getId());
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        CHANNEL_CACHE.remove(event.getChannel().getId());
        log.debug("Removed channel from cache '{}' (ID: {}) because it was deleted", event.getChannel().getName(), event.getChannel().getId());
    }
}