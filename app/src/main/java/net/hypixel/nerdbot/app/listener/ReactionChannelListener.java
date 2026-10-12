package net.hypixel.nerdbot.app.listener;

import com.vdurmont.emoji.EmojiParser;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.objects.ReactionChannel;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Reacts to every message in the configured reaction channels, and with the emojis a poll message contains. */
@Slf4j
public class ReactionChannelListener {

    // Discord allows a message at most 20 different reactions
    static final int MAX_REACTIONS = 20;

    private final GuildScope guildScope;
    private final Provider<NerdBotConfig> config;

    @Inject
    public ReactionChannelListener(GuildScope guildScope, Provider<NerdBotConfig> config) {
        this.guildScope = guildScope;
        this.config = config;
    }

    @SubscribeEvent
    public void onMessageReceive(MessageReceivedEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild()) && !event.getAuthor().isBot()
            && !event.isWebhookMessage() && !event.getMessage().getType().isSystem()) {
            react(event.getMessage(), event.getChannel().getId());
        }
    }

    void react(Message message, String channelId) {
        ChannelConfig channelConfig = config.get().getChannelConfig();
        Optional<ReactionChannel> reactionChannel = Optional.ofNullable(channelConfig.getReactionChannels()).orElse(List.of()).stream()
            .filter(channel -> channelId.equals(channel.discordChannelId()))
            .findFirst();

        if (reactionChannel.isPresent()) {
            ReactionChannel channel = reactionChannel.get();
            addReactions(message, Optional.ofNullable(channel.emojiIds()).orElse(List.of()).stream()
                .map(ReactionChannelListener::resolveEmoji)
                .flatMap(Optional::stream), channel.name());

            if (channel.thread()) {
                message.createThreadChannel("Related messages here").queue(
                    thread -> log.debug("Created a thread for message {} in reaction channel {}", message.getId(), channel.name()),
                    error -> log.error("Failed to create a thread for message {} in reaction channel {}", message.getId(), channel.name(), error));
            }
        } else if (channelId.equals(channelConfig.getPollChannelId())) {
            addReactions(message, EmojiParser.extractEmojis(message.getContentRaw()).stream().map(Emoji::fromUnicode), "polls");
        }
    }

    private static void addReactions(Message message, Stream<Emoji> emojis, String channelName) {
        emojis.distinct()
            .limit(MAX_REACTIONS)
            .forEach(emoji -> message.addReaction(emoji).queue(
                success -> log.debug("Added '{}' to message {} in {}", emoji.getName(), message.getId(), channelName),
                error -> log.warn("Failed to add '{}' to message {} in {}", emoji.getName(), message.getId(), channelName, error)));
    }

    static Optional<Emoji> resolveEmoji(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String trimmed = identifier.trim();

        if (trimmed.matches("\\d+")) {
            Optional<Emoji> cached = EmojiCache.getEmojiById(trimmed);
            if (cached.isPresent()) {
                return cached;
            }
        }

        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            String possibleId = trimmed.replaceAll("[^0-9]", "");
            if (!possibleId.isBlank()) {
                Optional<Emoji> cached = EmojiCache.getEmojiById(possibleId);
                if (cached.isPresent()) {
                    return cached;
                }
            }
        }

        if (EmojiParser.removeAllEmojis(trimmed).isEmpty()) {
            return Optional.of(Emoji.fromUnicode(trimmed));
        }

        return Optional.empty();
    }
}
