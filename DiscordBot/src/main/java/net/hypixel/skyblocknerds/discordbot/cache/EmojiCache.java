package net.hypixel.skyblocknerds.discordbot.cache;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.GenericEmojiUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Log4j2
public class EmojiCache {

    private static final Map<String, Emoji> EMOJI_CACHE = new HashMap<>();

    // TODO move this to API and generalize it so other modules can use it
    public EmojiCache() {
    }

    public static void initialize() {
        DiscordBot.getJda().getGuilds().forEach(guild -> {
            guild.retrieveEmojis().complete().forEach(richCustomEmoji -> {
                EMOJI_CACHE.put(richCustomEmoji.getId(), richCustomEmoji);
                log.info("Cached emoji " + StringUtils.formatNameWithId(richCustomEmoji.getName(), richCustomEmoji.getId()));
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
        log.debug("Cached emoji " + StringUtils.formatNameWithId(event.getEmoji().getName(), event.getEmoji().getId()));
    }

    @SubscribeEvent
    public void onEmojiDelete(EmojiRemovedEvent event) {
        EMOJI_CACHE.remove(event.getEmoji().getId());
        log.debug("Removed emoji from cache " + StringUtils.formatNameWithId(event.getEmoji().getName(), event.getEmoji().getId()));
    }

    @SubscribeEvent
    public void onEmojiUpdate(GenericEmojiUpdateEvent<?> event) {
        EMOJI_CACHE.put(event.getEmoji().getId(), event.getEmoji());
        log.debug("Cached emoji " + StringUtils.formatNameWithId(event.getEmoji().getName(), event.getEmoji().getId()));
    }
}

