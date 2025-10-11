package net.hypixel.nerdbot.bot.config.objects;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.cache.EmojiCache;

import java.util.List;
import java.util.Optional;

public record ReactionChannel(String name, String discordChannelId, List<String> emojiIds, boolean thread) {

    public List<Emoji> getEmojis() {
        return emojiIds.stream()
            .map(EmojiCache::getEmojiById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }
}
