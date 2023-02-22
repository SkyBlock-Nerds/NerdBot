package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.Objects;
import java.util.function.Function;

@Getter
@Setter
@ToString
public class EmojiConfig {

    /**
     * The ID of the reaction for the agree emoji
     */
    private String agreeEmojiId;

    /**
     * The ID of the reaction for the disagree emoji
     */
    private String disagreeEmojiId;

    /**
     * The ID of the reaction for the neutral emoji
     */
    private String neutralEmojiId;

    /**
     * The ID of the reaction for the greenlit emoji
     */
    private String greenlitEmojiId;

    public boolean isEquals(MessageReaction reaction, Function<EmojiConfig, String> function) {
        if (reaction.getEmoji().getType() != Emoji.Type.CUSTOM)
            return false;

        return Objects.equals(reaction.getEmoji().asCustom().getId(), function.apply(this));
    }
}
