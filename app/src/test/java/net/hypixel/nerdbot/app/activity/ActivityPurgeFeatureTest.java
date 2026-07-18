package net.hypixel.nerdbot.app.activity;

import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests pinning the behaviour of {@link ActivityPurgeFeature#purgeOldHistory}
 * before any further refactoring of the nomination/activity subsystem.
 */
class ActivityPurgeFeatureTest {

    private static final int RETENTION_DAYS = 30;

    @Test
    void purgesStaleHistoryAndResavesOnlyChangedUsers() {
        DiscordUser stale = userWithSuggestionCreatedAt("stale", daysAgo(100));
        DiscordUser recent = userWithSuggestionCreatedAt("recent", daysAgo(1));
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(stale).seed(recent);

        int purged = ActivityPurgeFeature.purgeOldHistory(store, RETENTION_DAYS);

        assertEquals(1, purged, "only the stale user should be counted as purged");
        assertEquals(List.of("stale"), store.savedIds(), "only the changed user should be re-saved");
        assertTrue(stale.getLastActivity().getSuggestionCreationHistory().isEmpty(),
            "the stale entry should have been removed");
        assertEquals(1, recent.getLastActivity().getSuggestionCreationHistory().size(),
            "the recent entry should have been kept");
    }

    @Test
    void leavesStoreUntouchedWhenNothingIsStale() {
        DiscordUser recent = userWithSuggestionCreatedAt("recent", daysAgo(1));
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(recent);

        int purged = ActivityPurgeFeature.purgeOldHistory(store, RETENTION_DAYS);

        assertEquals(0, purged);
        assertTrue(store.savedIds().isEmpty(), "no user should be re-saved when nothing is stale");
        assertEquals(1, recent.getLastActivity().getSuggestionCreationHistory().size());
    }

    @Test
    void handlesEmptyStore() {
        FakeDiscordUserStore store = new FakeDiscordUserStore();

        int purged = ActivityPurgeFeature.purgeOldHistory(store, RETENTION_DAYS);

        assertEquals(0, purged);
        assertTrue(store.savedIds().isEmpty());
    }

    private static DiscordUser userWithSuggestionCreatedAt(String discordId, long timestamp) {
        DiscordUser user = new DiscordUser(discordId);
        user.getLastActivity().getSuggestionCreationHistory().add(timestamp);
        return user;
    }

    private static long daysAgo(int days) {
        return System.currentTimeMillis() - Duration.ofDays(days).toMillis();
    }
}
