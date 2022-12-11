package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
     * The ID of the reaction for the greenlit emoji
     */
    private String greenlitEmojiId;
}
