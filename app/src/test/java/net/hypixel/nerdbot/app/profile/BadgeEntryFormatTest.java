package net.hypixel.nerdbot.app.profile;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BadgeEntryFormatTest {

    // Midday UTC so the formatted date is the same in any default time zone the tests run under
    private static final long MARCH_8_2024 = Instant.parse("2024-03-08T12:00:00Z").toEpochMilli();

    private final ProfileEmbeds embeds = new ProfileEmbeds(NerdBotConfig::new, new MapBadgeCatalog()
        .with(new TieredBadge("bug_hunter", "Bug Hunter", List.of(
            new TieredBadge.Tier("Tier 1", "🥇", 1),
            new TieredBadge.Tier("Tier 3", "🌟", 3))))
        .with(new TieredBadge("contest", "Contest Winner", List.of(
            new TieredBadge.Tier("Tier 1", "🥇", 1))))
        .with(new Badge("early", "Early Supporter", "🐣"))
        .with(new Badge("plain", "Plain", null)));

    @Test
    void tieredBadgeShowsBadgeNameAlongsideTier() {
        assertEquals("**🌟 Bug Hunter (Tier 3)**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("bug_hunter", 3, MARCH_8_2024)));
    }

    @Test
    void sameTierOfDifferentBadgesIsDistinguishable() {
        String bugHunter = embeds.formatBadgeEntry(new BadgeEntry("bug_hunter", 1, MARCH_8_2024));
        String contest = embeds.formatBadgeEntry(new BadgeEntry("contest", 1, MARCH_8_2024));

        assertEquals("**🥇 Bug Hunter (Tier 1)**\nObtained on March 08, 2024", bugHunter);
        assertEquals("**🥇 Contest Winner (Tier 1)**\nObtained on March 08, 2024", contest);
    }

    @Test
    void tierMissingFromConfigFallsBackToTierNumber() {
        assertEquals("**Bug Hunter (Tier 2)**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("bug_hunter", 2, MARCH_8_2024)));
    }

    @Test
    void tieredBadgeWithoutTierShowsBadgeName() {
        assertEquals("**Bug Hunter**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("bug_hunter", null, MARCH_8_2024)));
    }

    @Test
    void tieredBadgeWithTierBelowOneShowsBadgeName() {
        assertEquals("**Bug Hunter**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("bug_hunter", 0, MARCH_8_2024)));
    }

    @Test
    void nonTieredBadgeShowsEmojiAndName() {
        assertEquals("**🐣 Early Supporter**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("early", null, MARCH_8_2024)));
    }

    @Test
    void nonTieredBadgeWithoutEmojiShowsName() {
        assertEquals("**Plain**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("plain", null, MARCH_8_2024)));
    }

    @Test
    void nonTieredBadgeIgnoresStrayTier() {
        assertEquals("**🐣 Early Supporter**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("early", 2, MARCH_8_2024)));
    }

    @Test
    void badgeMissingFromConfigShowsItsId() {
        assertEquals("**removed_badge**\nObtained on March 08, 2024",
            embeds.formatBadgeEntry(new BadgeEntry("removed_badge", 1, MARCH_8_2024)));
    }
}
