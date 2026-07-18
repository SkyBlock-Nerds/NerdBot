package net.hypixel.nerdbot.app.feature;

import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests pinning the per-member reconciliation performed by
 * {@link UserGrabberFeature#reconcileMember}.
 */
class MemberReconciliationTest {

    private static final Predicate<String> ALL_KNOWN = badgeId -> true;

    @Test
    void createsNewUserWhenAbsentThenSavesIt() {
        FakeDiscordUserStore store = new FakeDiscordUserStore();

        DiscordUser result = UserGrabberFeature.reconcileMember(store, "123", "Nerd", ALL_KNOWN);

        assertEquals("123", result.getDiscordId());
        assertNotNull(result.getLastActivity(), "a new user should have default activity");
        assertNotNull(result.getBadges(), "a new user should have a (non-null) badge list");
        assertEquals(List.of("123"), store.savedIds(), "the new user should have been saved");
        assertTrue(store.findById("123").isPresent(), "the new user should now be in the store");
    }

    @Test
    void backfillsNullLastActivity() {
        DiscordUser existing = new DiscordUser("123");
        existing.setLastActivity(null);
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(existing);

        DiscordUser result = UserGrabberFeature.reconcileMember(store, "123", "Nerd", ALL_KNOWN);

        assertNotNull(result.getLastActivity(), "null activity should have been replaced with a default");
        assertEquals(List.of("123"), store.savedIds());
    }

    @Test
    void backfillsNullBadges() {
        DiscordUser existing = new DiscordUser("123");
        existing.setBadges(null);
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(existing);

        DiscordUser result = UserGrabberFeature.reconcileMember(store, "123", "Nerd", ALL_KNOWN);

        assertNotNull(result.getBadges(), "null badges should have been replaced with an empty list");
        assertTrue(result.getBadges().isEmpty());
        assertEquals(List.of("123"), store.savedIds());
    }

    @Test
    void prunesUnknownBadgesButKeepsKnownOnes() {
        DiscordUser existing = new DiscordUser("123");
        existing.setBadges(new ArrayList<>(List.of(new BadgeEntry("known"), new BadgeEntry("stale"))));
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(existing);

        DiscordUser result = UserGrabberFeature.reconcileMember(store, "123", "Nerd", badgeId -> badgeId.equals("known"));

        assertEquals(1, result.getBadges().size(), "the unrecognised badge should have been removed");
        assertEquals("known", result.getBadges().get(0).badgeId());
        assertEquals(List.of("123"), store.savedIds());
    }
}
