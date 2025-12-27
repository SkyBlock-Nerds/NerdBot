package net.hypixel.nerdbot.discord.cache;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.forum.GenericForumTagEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ChannelCache {

    private static final Map<String, GuildChannel> CHANNEL_CACHE = new HashMap<>();

    /**
     * Cache of ticket forum tag IDs, keyed by tag name (case-insensitive, stored lowercase)
     */
    private static final Map<String, Long> TICKET_TAG_CACHE = new ConcurrentHashMap<>();

    public ChannelCache() {
        DiscordUtils.getMainGuild().getChannels().forEach(channel -> {
            CHANNEL_CACHE.put(channel.getId(), channel);
            log.debug("Cached channel '" + channel.getName() + "' (ID: " + channel.getId() + ")");
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
            .map(ForumChannel.class::cast);
    }

    public static Optional<ForumChannel> getForumChannelById(String channelId) {
        return getChannelById(channelId)
            .map(ForumChannel.class::cast);
    }

    public static Optional<ForumChannel> getTicketChannel() {
        return getChannelById(DiscordBotEnvironment.getBot().getConfig().getTicketConfig().getForumChannelId())
            .map(ForumChannel.class::cast);
    }

    /**
     * Get a cached ticket tag ID by name
     *
     * @param tagName the tag name (case-insensitive)
     *
     * @return the tag ID if cached, empty otherwise
     */
    public static Optional<Long> getTicketTagId(String tagName) {
        if (tagName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TICKET_TAG_CACHE.get(tagName.toLowerCase()));
    }

    /**
     * Refresh the ticket tag cache from the ticket forum channel
     */
    public static void refreshTicketTagCache() {
        getTicketChannel().ifPresent(forum -> {
            TICKET_TAG_CACHE.clear();
            for (ForumTag tag : forum.getAvailableTags()) {
                TICKET_TAG_CACHE.put(tag.getName().toLowerCase(), tag.getIdLong());
            }
            log.info("Refreshed ticket tag cache with {} tags", TICKET_TAG_CACHE.size());
        });
    }

    /**
     * Get a ForumTag from the ticket channel by its cached ID
     *
     * @param tagName the tag name to look up
     *
     * @return the ForumTag if found
     */
    public static Optional<ForumTag> getTicketTagByName(String tagName) {
        if (tagName == null) {
            return Optional.empty();
        }
        return getTicketTagId(tagName).flatMap(tagId ->
            getTicketChannel().flatMap(forum ->
                forum.getAvailableTags().stream()
                    .filter(t -> t.getIdLong() == tagId)
                    .findFirst()
            )
        );
    }

    public static Optional<TextChannel> getLogChannel() {
        return getChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getLogChannelId())
            .map(TextChannel.class::cast);
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

        // Refresh ticket tag cache if this is the ticket channel
        String ticketChannelId = DiscordBotEnvironment.getBot().getConfig().getTicketConfig().getForumChannelId();
        if (event.getChannel().getId().equals(ticketChannelId)) {
            refreshTicketTagCache();
        }
    }
}
