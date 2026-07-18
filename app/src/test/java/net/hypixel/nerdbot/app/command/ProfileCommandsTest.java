package net.hypixel.nerdbot.app.command;

import net.hypixel.nerdbot.app.badge.BadgeManager;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for pure logic extracted from {@link ProfileCommands}: the Hypixel
 * social-media verification and the badge display ordering.
 */
class ProfileCommandsTest {

    @Test
    void failsWhenNoSocialMediaIsLinked() {
        Optional<String> error = ProfileCommands.socialVerificationError("Notch", null, "Notch", false);

        assertTrue(error.isPresent());
        assertEquals("The Hypixel profile for `Notch` does not have any social media linked!", error.get());
    }

    @Test
    void passesWhenLinkedDiscordMatchesCaseInsensitively() {
        assertTrue(ProfileCommands.socialVerificationError("Notch", "notch", "Notch", true).isEmpty());
        assertTrue(ProfileCommands.socialVerificationError("Notch", "Notch", "Notch", true).isEmpty());
    }

    @Test
    void failsWhenLinkedDiscordDoesNotMatch() {
        Optional<String> error = ProfileCommands.socialVerificationError("Notch", "Herobrine", "Notch", true);

        assertTrue(error.isPresent());
        assertEquals("The Discord account `Notch` does not match the social media linked on the Hypixel profile for `Notch`! It is currently set to `Herobrine`", error.get());
    }

    @Test
    void failsWhenSocialMediaIsPresentButNoDiscordLinkIsSet() {
        Optional<String> error = ProfileCommands.socialVerificationError("Notch", null, "Notch", true);

        assertTrue(error.isPresent());
        assertEquals("The Discord account `Notch` does not match the social media linked on the Hypixel profile for `Notch`! It is currently set to `null`", error.get());
    }

    @AfterEach
    void clearBadges() {
        BadgeManager.getBadgeMap().clear();
    }

    private static void registerTieredBadge() {
        BadgeManager.getBadgeMap().put("tiered", new TieredBadge("tiered", "Tiered", List.of(
            new TieredBadge.Tier("one", "e", 1),
            new TieredBadge.Tier("two", "e", 2),
            new TieredBadge.Tier("three", "e", 3))));
    }

    @Test
    void tieredBadgesSortBeforeNonTiered() {
        registerTieredBadge();
        BadgeEntry plain = new BadgeEntry("plain", null, 1L);
        BadgeEntry tiered = new BadgeEntry("tiered", 1, 1L);

        List<BadgeEntry> sorted = Stream.of(plain, tiered).sorted(ProfileCommands.badgeDisplayComparator()).toList();

        assertEquals(List.of(tiered, plain), sorted);
    }

    @Test
    void higherTierSortsBeforeLowerTier() {
        registerTieredBadge();
        BadgeEntry low = new BadgeEntry("tiered", 1, 1L);
        BadgeEntry high = new BadgeEntry("tiered", 3, 1L);

        List<BadgeEntry> sorted = Stream.of(low, high).sorted(ProfileCommands.badgeDisplayComparator()).toList();

        assertEquals(List.of(high, low), sorted);
    }

    @Test
    void nonTieredBadgesSortByMostRecentlyObtained() {
        BadgeEntry older = new BadgeEntry("a", null, 100L);
        BadgeEntry newer = new BadgeEntry("b", null, 200L);

        List<BadgeEntry> sorted = Stream.of(older, newer).sorted(ProfileCommands.badgeDisplayComparator()).toList();

        assertEquals(List.of(newer, older), sorted);
    }

    @Test
    void tieredEntryWithTierSortsBeforeTieredEntryWithoutTier() {
        registerTieredBadge();
        BadgeEntry withoutTier = new BadgeEntry("tiered", null, 1L);
        BadgeEntry withTier = new BadgeEntry("tiered", 2, 1L);

        List<BadgeEntry> sorted = Stream.of(withoutTier, withTier).sorted(ProfileCommands.badgeDisplayComparator()).toList();

        assertEquals(List.of(withTier, withoutTier), sorted);
    }
}
