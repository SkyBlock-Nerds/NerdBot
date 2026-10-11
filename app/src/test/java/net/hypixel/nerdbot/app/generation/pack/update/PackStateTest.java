package net.hypixel.nerdbot.app.generation.pack.update;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackStateTest {

    private static PackState.RejectedHash rejected(String sha1, String fingerprint, String deployKey) {
        return new PackState.RejectedHash(sha1, 48, "bad", 1L, fingerprint, deployKey);
    }

    @Test
    void versionsWithAPublishedSha1MatchOnIt() {
        PackState.RejectedHash entry = rejected("abc", "fp", "d1/48");

        assertTrue(entry.matches("abc", "other/48"));
        assertFalse(entry.matches("def", "d1/48"));
    }

    @Test
    void versionsWithoutASha1MatchOnTheDeployKey() {
        PackState.RejectedHash entry = rejected("d1/48", "fp", "d1/48");

        assertTrue(entry.matches(null, "d1/48"));
        assertFalse(entry.matches(null, "d2/48"));
    }

    @Test
    void entriesFromBeforeDeployKeysExistedNeverMatchByDeployKey() {
        assertFalse(rejected("abc", "fp", null).matches(null, "d1/48"));
    }

    @Test
    void aRejectionOnlyAppliesUnderTheSettingsItWasMadeWith() {
        PackState.RejectedHash entry = rejected("abc", "fp-1", "d1/48");

        assertTrue(entry.appliesTo("fp-1"));
        assertFalse(entry.appliesTo("fp-2"));
    }

    @Test
    void anAdminRejectionWithoutAFingerprintAlwaysApplies() {
        assertTrue(rejected("abc", null, "d1/48").appliesTo("anything"));
    }

    @Test
    void slotFindsTheRejectionThatStillApplies() {
        PackState.Slot slot = new PackState.Slot(null, null, List.of(
            rejected("abc", "old-settings", "d1/48"),
            rejected("abc", "current", "d1/48")
        ));

        assertEquals("current", slot.rejection("abc", "d1/48", "current").orElseThrow().settingsFingerprint());
        assertTrue(slot.rejection("abc", "d1/48", "newer").isEmpty());
    }

    @Test
    void nullCollectionsBecomeEmpty() {
        assertEquals(List.of(), new PackState.Slot(null, null, null).rejected());
        assertTrue(new PackState(null).packs().isEmpty());
    }

    @Test
    void deployKeyCombinesDeployIdAndFormat() {
        assertEquals("d1/48", PackState.deployKey("d1", 48));
        assertEquals("null/48", new PackState.AppliedPack(48, "abc", null, "abc.zip", 1L).deployKey());
    }

    @Test
    void aReleaseNeedsAtLeastOneVersion() {
        assertThrows(IllegalArgumentException.class, () -> new PackRelease("d1", 0, List.of()));
    }

    @Test
    void shortHashOnlyShortensRealSha1s() {
        assertEquals("0123456789ab", PackUpdateNotifier.shortHash("0123456789abcdef0123456789abcdef01234567"));
        assertEquals("d1/48", PackUpdateNotifier.shortHash("d1/48"));
    }
}
