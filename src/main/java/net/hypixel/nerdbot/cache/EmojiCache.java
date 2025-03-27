package net.hypixel.nerdbot.cache;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.GenericEmojiUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class EmojiCache {

    private static final Map<String, Emoji> EMOJI_CACHE = new HashMap<>();

    public EmojiCache() {
        NerdBotApp.getBot().getJDA().getGuilds().forEach(guild -> {
            guild.retrieveEmojis().complete().forEach(richCustomEmoji -> {
                EMOJI_CACHE.put(richCustomEmoji.getId(), richCustomEmoji);
                log.debug("Cached emoji '" + richCustomEmoji.getName() + "' (ID: " + richCustomEmoji.getId() + ")");
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

    @SubscribeEvent
    public void onEmojiCreate(EmojiAddedEvent event) {
        EMOJI_CACHE.put(event.getEmoji().getId(), event.getEmoji());
        log.debug("Cached emoji '" + event.getEmoji().getName() + "' (ID: " + event.getEmoji().getId() + ") because it was created");
    }

    @SubscribeEvent
    public void onEmojiDelete(EmojiRemovedEvent event) {
        EMOJI_CACHE.remove(event.getEmoji().getId());
        log.debug("Removed emoji from cache '" + event.getEmoji().getName() + "' (ID: " + event.getEmoji().getId() + ") because it was deleted");
    }

    @SubscribeEvent
    public void onEmojiUpdate(GenericEmojiUpdateEvent<?> event) {
        EMOJI_CACHE.put(event.getEmoji().getId(), event.getEmoji());
        log.debug("Cached emoji '" + event.getEmoji().getName() + "' (ID: " + event.getEmoji().getId() + ") because it was updated");
    }
}
