package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.badge.BadgeService;
import net.hypixel.nerdbot.app.badge.BadgeService.Change;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeCommandsTest {

    private static final String MEMBER = "100";
    private static final Badge HELPER = new Badge("helper", "Helper", ":star:");
    private static final TieredBadge VOTER = new TieredBadge("voter", "Voter", List.of(
        new TieredBadge.Tier("Voter I", null, 1),
        new TieredBadge.Tier("Voter II", null, 2)));

    private final CallRecorder recorder = new CallRecorder();
    private final DiscordUser user = new DiscordUser(MEMBER);
    private final FakeDiscordUserStore users = new FakeDiscordUserStore().seed(user);
    private final MapBadgeCatalog catalog = new MapBadgeCatalog().with(HELPER).with(VOTER);
    private final BadgeCommands commands = new BadgeCommands(new BadgeService(users, catalog), catalog);

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void givingShowsWhatHappened() {
        commands.give(DiscordFakes.slash(recorder, "1"), member(MEMBER), "voter", 2);

        assertEquals("Gave Tier 2 of the Voter badge to Steve!", DiscordFakes.lastEditText(recorder));
        assertTrue(user.hasBadge(VOTER, 2));
        assertEquals(List.of(MEMBER), users.savedIds());
    }

    @Test
    void revokingShowsWhatHappened() {
        user.addBadge(HELPER);

        commands.revoke(DiscordFakes.slash(recorder, "1"), member(MEMBER), "helper", 0);

        assertEquals("Removed the Helper badge from Steve!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aFailedSaveTellsTheModerator() {
        users.failSavesFor(MEMBER);

        commands.give(DiscordFakes.slash(recorder, "1"), member(MEMBER), "helper", 0);

        assertEquals("Failed to save the badge change. Please try again.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aMemberWithoutARecordIsLeftToTheCommandLibrary() {
        assertThrows(IllegalStateException.class, () -> commands.give(DiscordFakes.slash(recorder, "1"), member("999"), "helper", 0));
    }

    @Test
    void everyChangeHasAMessage() {
        assertEquals("Gave badge Helper to Steve!", BadgeCommands.describe(new Change.Given(HELPER, 0), "Steve"));
        assertEquals("Gave Tier 2 of the Voter badge to Steve!", BadgeCommands.describe(new Change.Given(VOTER, 2), "Steve"));
        assertEquals("Removed the Helper badge from Steve!", BadgeCommands.describe(new Change.Revoked(HELPER, 0), "Steve"));
        assertEquals("Removed Tier 1 of the Voter badge from Steve!", BadgeCommands.describe(new Change.Revoked(VOTER, 1), "Steve"));
        assertEquals("nope is an invalid Badge ID!", BadgeCommands.describe(new Change.UnknownBadge("nope"), "Steve"));
        assertEquals("Voter is an invalid Badge Tier! It only has 2 tiers.", BadgeCommands.describe(new Change.InvalidTier(VOTER, 2), "Steve"));
        assertEquals("Steve already has the Helper badge!", BadgeCommands.describe(new Change.AlreadyHeld(HELPER, 0), "Steve"));
        assertEquals("Steve already has Tier 1 of the Voter badge!", BadgeCommands.describe(new Change.AlreadyHeld(VOTER, 1), "Steve"));
        assertEquals("Steve does not have the Helper badge!", BadgeCommands.describe(new Change.NotHeld(HELPER, 0), "Steve"));
        assertEquals("Steve does not have Tier 2 of the Voter badge!", BadgeCommands.describe(new Change.NotHeld(VOTER, 2), "Steve"));
    }

    @Test
    void theListIsSortedAndShowsTiers() {
        assertEquals("""
            Available Badges (2):
            - :star: Helper (helper)
            - Voter (voter)
              - Voter I (tier: 1)
              - Voter II (tier: 2)
            """, BadgeCommands.listText(catalog.all()));
    }

    @Test
    void aShortListIsSentAsText() {
        commands.list(DiscordFakes.slash(recorder, "1"));

        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("Available Badges (2):"));
    }

    @Test
    void aListTooLongForOneMessageIsSentAsAFile() {
        MapBadgeCatalog big = new MapBadgeCatalog();
        IntStream.range(0, 100).forEach(i -> big.with(new Badge("badge-" + i, "A badge with a fairly long name " + i, null)));
        BadgeCommands bigCommands = new BadgeCommands(new BadgeService(users, big), big);

        bigCommands.list(DiscordFakes.slash(recorder, "1"));

        MessageEditData sent = assertInstanceOf(MessageEditData.class, recorder.last("editOriginal").arg(0));
        assertEquals("badges.txt", sent.getFiles().getFirst().getName());
    }

    private static Member member(String id) {
        return Fakes.of(Member.class, Map.of("getId", id, "getEffectiveName", "Steve"));
    }
}
