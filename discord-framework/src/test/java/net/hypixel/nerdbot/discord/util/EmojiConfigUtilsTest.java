package net.hypixel.nerdbot.discord.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiConfigUtilsTest {

    private final EmojiConfig emojis = new EmojiConfig();

    @BeforeEach
    void setUp() {
        emojis.setAgreeEmojiId("1");
        emojis.setDisagreeEmojiId("2");
        emojis.setNeutralEmojiId("3");
        emojis.setGreenlitEmojiId("4");
    }

    private static MessageReaction reaction(Emoji emoji) {
        return new MessageReaction(Fakes.of(JDA.class, Map.of()), Fakes.of(MessageChannel.class, Map.of()), (EmojiUnion) emoji, 1, 2,
            new boolean[]{false, false}, new int[]{1, 0});
    }

    @Test
    void agreeDisagreeAndNeutralAreVotes() {
        assertTrue(EmojiConfigUtils.isVote(emojis, reaction(Emoji.fromCustom("agree", 1, false))));
        assertTrue(EmojiConfigUtils.isVote(emojis, reaction(Emoji.fromCustom("disagree", 2, false))));
        assertTrue(EmojiConfigUtils.isVote(emojis, reaction(Emoji.fromCustom("neutral", 3, false))));
    }

    @Test
    void otherEmojisAreNotVotes() {
        assertFalse(EmojiConfigUtils.isVote(emojis, reaction(Emoji.fromCustom("greenlit", 4, false))));
        assertFalse(EmojiConfigUtils.isVote(emojis, reaction(Emoji.fromUnicode("👍"))));
    }
}
