package net.hypixel.nerdbot.discord.listener;

import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.config.objects.ReactionChannel;
import net.hypixel.nerdbot.cache.EmojiCache;

import java.util.List;
import java.util.Optional;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

@Slf4j
public class ReactionChannelListener {

    @SubscribeEvent
    public void onMessageReceive(MessageReceivedEvent event) {
        List<ReactionChannel> reactionChannels = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getReactionChannels();

        if (reactionChannels == null) {
            return;
        }

        Optional<ReactionChannel> reactionChannel = reactionChannels.stream()
            .filter(channel -> channel.discordChannelId().equals(event.getChannel().getId()))
            .findFirst();

        if (reactionChannel.isPresent()) {
            Message message = event.getMessage();

            reactionChannel.get().emojiIds()
                .stream()
                .map(this::resolveEmoji)
                .flatMap(Optional::stream)
                .forEach(emoji -> {
                    message.addReaction(emoji).queue();
                    log.info("[Reaction Channel] Added reaction '" + emoji.getName() + "' to message " + message.getId() + " in reaction channel " + reactionChannel.get().name());
                });

            if (reactionChannel.get().thread()) {
                message.createThreadChannel("Related messages here").queue(thread -> {
                    log.info("[Reaction Channel] Created thread for message " + message.getId() + " in reaction channel " + reactionChannel.get().name());
                });
            }
            return;
        }

        String pollChannelId = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getPollChannelId();

        if (event.getChannel().getId().equalsIgnoreCase(pollChannelId)) {
            EmojiParser.extractEmojis(event.getMessage().getContentRaw()).stream()
                .map(Emoji::fromUnicode)
                .forEach(emoji -> {
                    event.getMessage().addReaction(emoji).queue();
                    log.info("[Polls] [" + pollChannelId + "] Added reaction '" + emoji.getName() + "' to message " + event.getMessage().getId());
                });
        }
    }

    private Optional<Emoji> resolveEmoji(String identifier) {
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
