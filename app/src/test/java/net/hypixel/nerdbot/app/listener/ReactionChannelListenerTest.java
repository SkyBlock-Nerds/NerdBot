package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.objects.ReactionChannel;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionChannelListenerTest {

    private static final String THUMBS_UP = Character.toString(0x1F44D);
    private static final String PIZZA = Character.toString(0x1F355);

    private final NerdBotConfig config = new NerdBotConfig();
    private final ReactionChannelListener listener = new ReactionChannelListener(new GuildScope(() -> config), () -> config);
    private final CallRecorder recorder = new CallRecorder();

    private Message message(String content) {
        return recorder.fake(Message.class, Map.of("getId", "5", "getContentRaw", content));
    }

    private List<String> reactions() {
        return recorder.calls("addReaction").stream().map(call -> ((Emoji) call.arg(0)).getName()).toList();
    }

    @Test
    void messagesInAReactionChannelGetItsEmojisOnce() {
        config.getChannelConfig().setReactionChannels(List.of(new ReactionChannel("ideas", "10", List.of(THUMBS_UP, THUMBS_UP, PIZZA), false)));

        listener.react(message("an idea"), "10");

        assertEquals(List.of(THUMBS_UP, PIZZA), reactions());
        assertFalse(recorder.called("createThreadChannel"));
    }

    @Test
    void noMoreThanDiscordsLimitOfReactionsIsAdded() {
        List<String> emojis = IntStream.range(0, 25).mapToObj(offset -> Character.toString(0x1F600 + offset)).toList();
        config.getChannelConfig().setReactionChannels(List.of(new ReactionChannel("ideas", "10", emojis, false)));

        listener.react(message("an idea"), "10");

        assertEquals(ReactionChannelListener.MAX_REACTIONS, reactions().size());
    }

    @Test
    void aReactionChannelCanStartAThreadPerMessage() {
        config.getChannelConfig().setReactionChannels(List.of(new ReactionChannel("ideas", "10", List.of(), true)));

        listener.react(message("an idea"), "10");

        assertTrue(recorder.called("createThreadChannel"));
    }

    @Test
    void aReactionChannelWithoutAChannelIdOrEmojisMatchesNothing() {
        config.getChannelConfig().setReactionChannels(List.of(new ReactionChannel("broken", null, null, true)));

        listener.react(message("hello"), "10");

        assertTrue(reactions().isEmpty());
        assertFalse(recorder.called("createThreadChannel"));
    }

    @Test
    void pollsGetTheEmojisTheyMention() {
        config.getChannelConfig().setPollChannelId("20");

        listener.react(message("burgers " + THUMBS_UP + " or pizza " + PIZZA + " or " + THUMBS_UP), "20");

        assertEquals(List.of(THUMBS_UP, PIZZA), reactions());
    }

    @Test
    void withNoReactionChannelsConfiguredPollsStillWork() {
        config.getChannelConfig().setReactionChannels(null);
        config.getChannelConfig().setPollChannelId("20");

        listener.react(message("yes " + THUMBS_UP), "20");

        assertEquals(List.of(THUMBS_UP), reactions());
    }

    @Test
    void otherChannelsAreLeftAlone() {
        config.getChannelConfig().setPollChannelId("20");

        listener.react(message("hello " + THUMBS_UP), "30");

        assertTrue(reactions().isEmpty());
    }

    @Test
    void unknownEmojiIdentifiersAreSkipped() {
        assertEquals(Optional.empty(), ReactionChannelListener.resolveEmoji(null));
        assertEquals(Optional.empty(), ReactionChannelListener.resolveEmoji("  "));
        assertEquals(Optional.empty(), ReactionChannelListener.resolveEmoji("not an emoji"));
        assertEquals(Optional.empty(), ReactionChannelListener.resolveEmoji("123456"));
    }
}
