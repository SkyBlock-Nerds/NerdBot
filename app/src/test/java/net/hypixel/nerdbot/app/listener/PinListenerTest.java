package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.entities.Message;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.forumParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.textParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.thread;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinListenerTest {

    private final NerdBotConfig config = new NerdBotConfig();
    private final PinListener listener = new PinListener(new GuildScope(() -> config), () -> config);
    private final CallRecorder recorder = new CallRecorder();

    @BeforeEach
    void setUp() {
        config.getChannelConfig().setAutoPinFirstMessage(true);
    }

    @Test
    void theFirstMessageOfAForumPostIsPinned() {
        assertTrue(listener.shouldPinStarter(thread("20", forumParent("10", "forum"))));
    }

    @Test
    void threadsInTextChannelsArePinnedToo() {
        assertTrue(listener.shouldPinStarter(thread("20", textParent("10", "general"))));
    }

    @Test
    void blacklistedChannelsAreNotPinned() {
        config.getChannelConfig().setAutoPinBlacklistedChannels(new String[]{"10"});

        assertFalse(listener.shouldPinStarter(thread("20", forumParent("10", "forum"))));
    }

    @Test
    void nothingIsPinnedWhenAutoPinIsOff() {
        config.getChannelConfig().setAutoPinFirstMessage(false);

        assertFalse(listener.shouldPinStarter(thread("20", forumParent("10", "forum"))));
    }

    private Message message(boolean pinned) {
        return recorder.fake(Message.class, Map.of("getId", "5", "isPinned", pinned));
    }

    @Test
    void anUnpinnedMessageIsPinned() {
        PinListener.setPinned(message(false), true);

        assertTrue(recorder.called("pin"));
    }

    @Test
    void aPinnedMessageIsUnpinned() {
        PinListener.setPinned(message(true), false);

        assertTrue(recorder.called("unpin"));
    }

    @Test
    void aMessageAlreadyAsAskedIsLeftAlone() {
        PinListener.setPinned(message(true), true);
        PinListener.setPinned(message(false), false);

        assertFalse(recorder.called("pin"));
        assertFalse(recorder.called("unpin"));
    }
}
