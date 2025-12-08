package net.hypixel.nerdbot.discord.util;

import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.discord.config.EmojiConfig;

import java.util.Objects;
import java.util.function.Function;

public final class EmojiConfigUtils {

    private EmojiConfigUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static boolean isReactionEquals(EmojiConfig config, MessageReaction reaction, Function<EmojiConfig, String> emojiIdSupplier) {
        if (reaction.getEmoji().getType() != Emoji.Type.CUSTOM) {
            return false;
        }

        return Objects.equals(reaction.getEmoji().asCustom().getId(), emojiIdSupplier.apply(config));
    }
}