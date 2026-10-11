package net.hypixel.nerdbot.app.profile;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ordering a user's badges for display: tiered badges before non-tiered, higher tiers before lower,
 * a tiered entry with a tier before one without, then most-recently-obtained among equal tiers and non-tiered.
 */
class BadgeDisplayOrderTest {

    private final MapBadgeCatalog catalog = new MapBadgeCatalog();
    private final ProfileEmbeds embeds = new ProfileEmbeds(NerdBotConfig::new, catalog);

    private void registerTieredBadge() {
        catalog.with(new TieredBadge("tiered", "Tiered", List.of(
            new TieredBadge.Tier("one", "e", 1),
            new TieredBadge.Tier("two", "e", 2),
            new TieredBadge.Tier("three", "e", 3))));
    }

    @Test
    void tieredBadgesSortBeforeNonTiered() {
        registerTieredBadge();
        BadgeEntry plain = new BadgeEntry("plain", null, 1L);
        BadgeEntry tiered = new BadgeEntry("tiered", 1, 1L);

        List<BadgeEntry> sorted = Stream.of(plain, tiered).sorted(embeds.badgeDisplayOrder()).toList();

        assertEquals(List.of(tiered, plain), sorted);
    }

    @Test
    void higherTierSortsBeforeLowerTier() {
        registerTieredBadge();
        BadgeEntry low = new BadgeEntry("tiered", 1, 1L);
        BadgeEntry high = new BadgeEntry("tiered", 3, 1L);

        List<BadgeEntry> sorted = Stream.of(low, high).sorted(embeds.badgeDisplayOrder()).toList();

        assertEquals(List.of(high, low), sorted);
    }

    @Test
    void tierMissingFromConfigStillSortsByTierNumber() {
        registerTieredBadge();
        BadgeEntry configured = new BadgeEntry("tiered", 2, 1L);
        BadgeEntry unconfigured = new BadgeEntry("tiered", 5, 1L);

        List<BadgeEntry> sorted = Stream.of(configured, unconfigured).sorted(embeds.badgeDisplayOrder()).toList();

        assertEquals(List.of(unconfigured, configured), sorted);
    }

    @Test
    void sameTierFallsBackToMostRecentlyObtained() {
        registerTieredBadge();
        BadgeEntry older = new BadgeEntry("tiered", 1, 100L);
        BadgeEntry newer = new BadgeEntry("tiered", 1, 200L);

        List<BadgeEntry> sorted = Stream.of(older, newer).sorted(embeds.badgeDisplayOrder()).toList();

        assertEquals(List.of(newer, older), sorted);
    }

    @Test
    void nonTieredBadgesSortByMostRecentlyObtained() {
        BadgeEntry older = new BadgeEntry("a", null, 100L);
        BadgeEntry newer = new BadgeEntry("b", null, 200L);

        List<BadgeEntry> sorted = Stream.of(older, newer).sorted(embeds.badgeDisplayOrder()).toList();

        assertEquals(List.of(newer, older), sorted);
    }

    @Test
    void tieredEntryWithTierSortsBeforeTieredEntryWithoutTier() {
        registerTieredBadge();
        BadgeEntry withoutTier = new BadgeEntry("tiered", null, 1L);
        BadgeEntry withTier = new BadgeEntry("tiered", 2, 1L);

        List<BadgeEntry> sorted = Stream.of(withoutTier, withTier).sorted(embeds.badgeDisplayOrder()).toList();

        assertEquals(List.of(withTier, withoutTier), sorted);
    }
}
