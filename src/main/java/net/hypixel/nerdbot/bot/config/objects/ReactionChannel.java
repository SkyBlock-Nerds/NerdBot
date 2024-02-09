package net.hypixel.nerdbot.bot.config.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.cache.EmojiCache;

import java.util.List;
import java.util.Optional;

@Getter
@ToString
@AllArgsConstructor
public class ReactionChannel {

    private final String name;
    private final String discordChannelId;
    private final List<String> emojiIds;

    public List<Emoji> getEmojis() {
        return emojiIds.stream()
            .map(EmojiCache::getEmojiById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }
}
