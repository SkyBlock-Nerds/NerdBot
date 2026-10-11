package net.hypixel.nerdbot.app.suggestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoArchiveDecisionTest {

    private static final SuggestionAutoArchiver.Decision NOTHING = new SuggestionAutoArchiver.Decision(false, false);
    private static final SuggestionAutoArchiver.Decision ARCHIVE = new SuggestionAutoArchiver.Decision(true, false);
    private static final SuggestionAutoArchiver.Decision LOCK = new SuggestionAutoArchiver.Decision(false, true);
    private static final SuggestionAutoArchiver.Decision BOTH = new SuggestionAutoArchiver.Decision(true, true);

    @Test
    void archivesOnceTheThresholdIsReached() {
        assertEquals(NOTHING, SuggestionAutoArchiver.decide(167, 168, 0, false, false));
        assertEquals(ARCHIVE, SuggestionAutoArchiver.decide(168, 168, 0, false, false));
    }

    @Test
    void locksOnceTheThresholdIsReached() {
        assertEquals(NOTHING, SuggestionAutoArchiver.decide(335, 0, 336, false, false));
        assertEquals(LOCK, SuggestionAutoArchiver.decide(336, 0, 336, false, false));
    }

    @Test
    void archivesAndLocksTogetherWhenBothThresholdsAreReached() {
        assertEquals(BOTH, SuggestionAutoArchiver.decide(400, 168, 336, false, false));
    }

    @Test
    void defaultLockThresholdOfMinusOneIsDisabledRatherThanAlwaysTrue() {
        assertEquals(NOTHING, SuggestionAutoArchiver.decide(0, 168, -1, false, false));
        assertEquals(ARCHIVE, SuggestionAutoArchiver.decide(10_000, 168, -1, false, false));
    }

    @Test
    void zeroThresholdsAreDisabled() {
        assertEquals(NOTHING, SuggestionAutoArchiver.decide(10_000, 0, 0, false, false));
    }

    @Test
    void alreadyArchivedThreadsAreLeftAlone() {
        assertEquals(NOTHING, SuggestionAutoArchiver.decide(10_000, 168, 336, true, false));
    }

    @Test
    void alreadyLockedThreadsAreOnlyArchived() {
        assertEquals(ARCHIVE, SuggestionAutoArchiver.decide(10_000, 168, 336, false, true));
    }
}
