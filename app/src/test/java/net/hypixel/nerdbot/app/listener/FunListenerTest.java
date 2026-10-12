package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.app.config.FunConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunListenerTest {

    private static final String THUMBS_UP = Character.toString(0x1F44D);
    private static final String PARTY = Character.toString(0x1F389);

    private final NerdBotConfig config = new NerdBotConfig();
    private final FunConfig fun = config.getFunConfig();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-04-01T12:00:00Z"));
    private final FunListener listener = new FunListener(new GuildScope(() -> config), () -> config, clock);
    private final CallRecorder recorder = new CallRecorder();
    private final Message message = recorder.fake(Message.class, Map.of("getId", "5"));

    private List<String> reactions() {
        return recorder.calls("addReaction").stream().map(call -> ((Emoji) call.arg(0)).getName()).toList();
    }

    private void alwaysReactOnAprilFools() {
        fun.setAprilFoolsReactionChance(1.0);
        fun.setAprilFoolsReactionEmoji(PARTY);
        fun.setAprilFoolsTimezone("UTC");
    }

    @Test
    void configuredMembersGetTheirReaction() {
        fun.getAutoReactions().put("1", THUMBS_UP);

        listener.react(message, "1");
        listener.react(message, "2");

        assertEquals(List.of(THUMBS_UP), reactions());
    }

    @Test
    void onAprilFoolsEveryoneMightGetOne() {
        alwaysReactOnAprilFools();

        listener.react(message, "2");

        assertEquals(List.of(PARTY), reactions());
    }

    @Test
    void outsideAprilFoolsNobodyDoes() {
        alwaysReactOnAprilFools();
        clock.advance(Duration.ofDays(1));

        listener.react(message, "2");

        assertTrue(reactions().isEmpty());
    }

    @Test
    void theDayIsJudgedInTheConfiguredZone() {
        clock.advance(Duration.ofHours(-10));

        assertTrue(listener.isAprilFools("UTC"));
        assertFalse(listener.isAprilFools("America/New_York"));
    }

    @Test
    void anInvalidZoneFallsBackToTheSystemZone() {
        alwaysReactOnAprilFools();
        fun.setAprilFoolsTimezone("Not/AZone");

        listener.react(message, "2");
        listener.react(message, "2");

        assertEquals(List.of(PARTY, PARTY), reactions());
    }

    @Test
    void withoutAChanceOrEmojiThereAreNoAprilFoolsReactions() {
        fun.setAprilFoolsTimezone("UTC");
        fun.setAprilFoolsReactionChance(1.0);

        listener.react(message, "2");

        assertTrue(reactions().isEmpty());
    }
}
