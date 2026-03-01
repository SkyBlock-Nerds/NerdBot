package net.hypixel.nerdbot.discord.cache;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.GenericEmojiUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class EmojiCache {

    private static final Map<String, Emoji> EMOJI_CACHE = new HashMap<>();

    public EmojiCache() {
        DiscordBotEnvironment.getBot().getJDA().getGuilds().forEach(guild -> {
            guild.retrieveEmojis().complete().forEach(richCustomEmoji -> {
                EMOJI_CACHE.put(richCustomEmoji.getId(), richCustomEmoji);
                log.debug("Cached emoji '{}' (ID: {})", richCustomEmoji.getName(), richCustomEmoji.getId());
            });
        });
    }

    public static Optional<Emoji> getEmojiById(String emojiId) {
        return Optional.ofNullable(EMOJI_CACHE.get(emojiId));
    }

    public static Optional<Emoji> getEmojiByName(String emojiName) {
        return EMOJI_CACHE.values().stream()
            .filter(emoji -> emoji.getName().equals(emojiName))
            .findFirst();
    }

    public static Map<String, Emoji> getEmojiCache() {
        return EMOJI_CACHE;
    }

    public static String getFormattedEmoji(Function<EmojiConfig, String> emojiIdFunction) {
        return getEmojiById(emojiIdFunction.apply(DiscordBotEnvironment.getBot().getConfig().getEmojiConfig()))
            .orElseGet(() -> Emoji.fromUnicode("❓"))
            .getFormatted();
    }

    @SubscribeEvent
    public void onEmojiCreate(EmojiAddedEvent event) {
        EMOJI_CACHE.put(event.getEmoji().getId(), event.getEmoji());
        log.debug("Cached emoji '{}' (ID: {}) because it was created", event.getEmoji().getName(), event.getEmoji().getId());
    }

    @SubscribeEvent
    public void onEmojiDelete(EmojiRemovedEvent event) {
        EMOJI_CACHE.remove(event.getEmoji().getId());
        log.debug("Removed emoji from cache '{}' (ID: {}) because it was deleted", event.getEmoji().getName(), event.getEmoji().getId());
    }

    @SubscribeEvent
    public void onEmojiUpdate(GenericEmojiUpdateEvent<?> event) {
        EMOJI_CACHE.put(event.getEmoji().getId(), event.getEmoji());
        log.debug("Cached emoji '{}' (ID: {}) because it was updated", event.getEmoji().getName(), event.getEmoji().getId());
    }
}