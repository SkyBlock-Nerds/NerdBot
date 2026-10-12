package net.hypixel.nerdbot.discord.util;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.discord.config.EmojiConfig;

import java.util.Objects;
import java.util.function.Function;

@UtilityClass
public class EmojiConfigUtils {

    public static boolean isReactionEquals(EmojiConfig config, MessageReaction reaction, Function<EmojiConfig, String> emojiIdSupplier) {
        if (reaction.getEmoji().getType() != Emoji.Type.CUSTOM) {
            return false;
        }

        return Objects.equals(reaction.getEmoji().asCustom().getId(), emojiIdSupplier.apply(config));
    }

    /** Whether the reaction is an agree, disagree or neutral vote. */
    public static boolean isVote(EmojiConfig config, MessageReaction reaction) {
        return isReactionEquals(config, reaction, EmojiConfig::getAgreeEmojiId)
            || isReactionEquals(config, reaction, EmojiConfig::getDisagreeEmojiId)
            || isReactionEquals(config, reaction, EmojiConfig::getNeutralEmojiId);
    }
}