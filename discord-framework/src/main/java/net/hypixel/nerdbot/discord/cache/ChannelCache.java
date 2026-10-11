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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ChannelCache {

    // Written from JDA event threads and read from everywhere else
    private static final Map<String, GuildChannel> CHANNEL_CACHE = new ConcurrentHashMap<>();

    public ChannelCache() {
        DiscordUtils.getMainGuild().getChannels().forEach(channel -> {
            cache(channel);
            log.debug("Cached channel '{}' (ID: {})", channel.getName(), channel.getId());
        });
    }

    public static Optional<GuildChannel> getChannelById(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CHANNEL_CACHE.get(channelId));
    }

    public static Optional<GuildChannel> getChannelByName(String channelName) {
        return CHANNEL_CACHE.values().stream()
            .filter(guildChannel -> guildChannel.getName().equals(channelName))
            .findFirst();
    }

    public static Optional<TextChannel> getTextChannelByName(String channelName) {
        return ofType(getChannelByName(channelName), TextChannel.class);
    }

    public static Optional<TextChannel> getTextChannelById(String channelId) {
        return ofType(getChannelById(channelId), TextChannel.class);
    }

    public static Optional<ForumChannel> getForumChannelByName(String channelName) {
        return ofType(getChannelByName(channelName), ForumChannel.class);
    }

    public static Optional<ForumChannel> getForumChannelById(String channelId) {
        return ofType(getChannelById(channelId), ForumChannel.class);
    }

    public static Optional<Category> getCategoryByName(String categoryName) {
        return ofType(getChannelByName(categoryName), Category.class);
    }

    public static Optional<Category> getCategoryById(String categoryId) {
        return ofType(getChannelById(categoryId), Category.class);
    }

    private static <T> Optional<T> ofType(Optional<GuildChannel> channel, Class<T> type) {
        return channel.filter(type::isInstance).map(type::cast);
    }

    public static Optional<TextChannel> getLogChannel() {
        return getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getLogChannelId());
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
        return getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getVerifyLogChannelId());
    }

    static void cache(GuildChannel channel) {
        CHANNEL_CACHE.put(channel.getId(), channel);
    }

    static void uncache(String channelId) {
        CHANNEL_CACHE.remove(channelId);
    }

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        cache(event.getChannel().asGuildChannel());
        log.debug("Cached channel '{}' (ID: {}) because it was created", event.getChannel().getName(), event.getChannel().getId());
    }

    @SubscribeEvent
    public void onChannelUpdate(GenericChannelUpdateEvent<?> event) {
        cache(event.getChannel().asGuildChannel());
        log.debug("Cached channel '{}' (ID: {}) because it was updated", event.getChannel().getName(), event.getChannel().getId());
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        uncache(event.getChannel().getId());
        log.debug("Removed channel from cache '{}' (ID: {}) because it was deleted", event.getChannel().getName(), event.getChannel().getId());
    }
}