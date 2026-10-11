package net.hypixel.nerdbot.app.suggestion;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.AGREE;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.DISAGREE;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.NEUTRAL;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.NOW;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.config;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.firstMessage;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.reaction;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.tag;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.thread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionTest {

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = config("10");

    @Test
    void votesAreCountedFromTheFirstMessage() {
        ThreadChannel thread = thread(recorder, 42, "10");

        Suggestion suggestion = Suggestion.of(thread, Suggestion.ChannelType.NORMAL,
            firstMessage(42, 500, NOW, reaction(AGREE, 7), reaction(DISAGREE, 2), reaction(NEUTRAL, 1), reaction("999", 50)), config);

        assertEquals(7, suggestion.getAgrees());
        assertEquals(2, suggestion.getDisagrees());
        assertEquals(1, suggestion.getNeutrals());
        assertFalse(suggestion.isDeleted());
        assertEquals("https://discord.com/channels/1/42", suggestion.getJumpUrl());
        assertEquals("10", suggestion.getParentId());
    }

    @Test
    void noFirstMessageMeansDeletedWithNoVotes() {
        Suggestion suggestion = Suggestion.of(thread(recorder, 42, "10"), Suggestion.ChannelType.NORMAL, null, config);

        assertTrue(suggestion.isDeleted());
        assertEquals(0, suggestion.getAgrees());
    }

    @Test
    void aFirstMessageThatIsNotTheStarterMeansTheOriginalWasDeleted() {
        Suggestion suggestion = Suggestion.of(thread(recorder, 42, "10"), Suggestion.ChannelType.NORMAL,
            firstMessage(43, 500, NOW, reaction(AGREE, 7)), config);

        assertTrue(suggestion.isDeleted());
        assertEquals(0, suggestion.getAgrees());
    }

    @Test
    void onlyNormalSuggestionsWithTheGreenlitTagAreGreenlit() {
        ThreadChannel tagged = thread(recorder, 42, "10", NOW, false, false, List.of(tag("Greenlit")));

        assertTrue(Suggestion.of(tagged, Suggestion.ChannelType.NORMAL, null, config).isGreenlit());
        assertFalse(Suggestion.of(tagged, Suggestion.ChannelType.ALPHA, null, config).isGreenlit());
        assertFalse(Suggestion.of(thread(recorder, 43, "10"), Suggestion.ChannelType.NORMAL, null, config).isGreenlit());
    }

    @Test
    void buildingASuggestionChangesNothingOnDiscord() {
        Suggestion.of(thread(recorder, 42, "10", NOW.minusYears(1), false, false, List.of()), Suggestion.ChannelType.NORMAL, null, config);

        assertFalse(recorder.called("getManager"));
        assertFalse(recorder.called("setArchived"));
        assertFalse(recorder.called("setLocked"));
    }

    @Test
    void ratioIsAgreesOverAgreesAndDisagrees() {
        Suggestion suggestion = Suggestion.of(thread(recorder, 42, "10"), Suggestion.ChannelType.NORMAL,
            firstMessage(42, 500, NOW, reaction(AGREE, 3), reaction(DISAGREE, 1)), config);

        assertEquals(75.0, suggestion.getRatio());
        assertEquals(0.0, Suggestion.of(thread(recorder, 43, "10"), Suggestion.ChannelType.NORMAL, null, config).getRatio());
    }
}
