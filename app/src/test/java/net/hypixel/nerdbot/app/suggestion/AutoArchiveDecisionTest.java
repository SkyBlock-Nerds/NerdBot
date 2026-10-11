package net.hypixel.nerdbot.app.suggestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoArchiveDecisionTest {

    private static final Suggestion.AutoArchiveDecision NOTHING = new Suggestion.AutoArchiveDecision(false, false);
    private static final Suggestion.AutoArchiveDecision ARCHIVE = new Suggestion.AutoArchiveDecision(true, false);
    private static final Suggestion.AutoArchiveDecision LOCK = new Suggestion.AutoArchiveDecision(false, true);
    private static final Suggestion.AutoArchiveDecision BOTH = new Suggestion.AutoArchiveDecision(true, true);

    @Test
    void archivesOnceTheThresholdIsReached() {
        assertEquals(NOTHING, Suggestion.decideAutoArchive(167, 168, 0, false, false));
        assertEquals(ARCHIVE, Suggestion.decideAutoArchive(168, 168, 0, false, false));
    }

    @Test
    void locksOnceTheThresholdIsReached() {
        assertEquals(NOTHING, Suggestion.decideAutoArchive(335, 0, 336, false, false));
        assertEquals(LOCK, Suggestion.decideAutoArchive(336, 0, 336, false, false));
    }

    @Test
    void archivesAndLocksTogetherWhenBothThresholdsAreReached() {
        assertEquals(BOTH, Suggestion.decideAutoArchive(400, 168, 336, false, false));
    }

    @Test
    void defaultLockThresholdOfMinusOneIsDisabledRatherThanAlwaysTrue() {
        assertEquals(NOTHING, Suggestion.decideAutoArchive(0, 168, -1, false, false));
        assertEquals(ARCHIVE, Suggestion.decideAutoArchive(10_000, 168, -1, false, false));
    }

    @Test
    void zeroThresholdsAreDisabled() {
        assertEquals(NOTHING, Suggestion.decideAutoArchive(10_000, 0, 0, false, false));
    }

    @Test
    void alreadyArchivedThreadsAreLeftAlone() {
        assertEquals(NOTHING, Suggestion.decideAutoArchive(10_000, 168, 336, true, false));
    }

    @Test
    void alreadyLockedThreadsAreOnlyArchived() {
        assertEquals(ARCHIVE, Suggestion.decideAutoArchive(10_000, 168, 336, false, true));
    }
}
