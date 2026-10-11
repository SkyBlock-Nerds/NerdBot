package net.hypixel.nerdbot.app.badge;

import net.hypixel.nerdbot.app.badge.BadgeService.Change;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeServiceTest {

    private static final String MEMBER = "100";
    private static final Badge HELPER = new Badge("helper", "Helper", null);
    private static final TieredBadge VOTER = new TieredBadge("voter", "Voter", List.of(
        new TieredBadge.Tier("Voter I", null, 1),
        new TieredBadge.Tier("Voter II", null, 2),
        new TieredBadge.Tier("Voter III", null, 3)));

    private final DiscordUser user = new DiscordUser(MEMBER);
    private final FakeDiscordUserStore users = new FakeDiscordUserStore().seed(user);
    private final BadgeService service = new BadgeService(users, new MapBadgeCatalog().with(HELPER).with(VOTER));

    @Test
    void givingABadgeSavesItStraightAway() {
        assertEquals(new Change.Given(HELPER, 0), service.give(MEMBER, "helper", 0).join());

        assertTrue(user.hasBadge(HELPER));
        assertEquals(List.of(MEMBER), users.savedIds());
    }

    @Test
    void givingABadgeTheyAlreadyHaveSavesNothing() {
        user.addBadge(HELPER);

        assertEquals(new Change.AlreadyHeld(HELPER, 0), service.give(MEMBER, "helper", 0).join());
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void tierBelowOneMeansTierOne() {
        assertEquals(new Change.Given(VOTER, 1), service.give(MEMBER, "voter", 0).join());
        assertTrue(user.hasBadge(VOTER, 1));
    }

    @Test
    void aNewTierReplacesTheOldOne() {
        service.give(MEMBER, "voter", 1).join();

        assertEquals(new Change.Given(VOTER, 3), service.give(MEMBER, "voter", 3).join());
        assertTrue(user.hasBadge(VOTER, 3));
        assertFalse(user.hasBadge(VOTER, 1));
    }

    @Test
    void theSameTierAgainIsAlreadyHeld() {
        service.give(MEMBER, "voter", 2).join();

        assertEquals(new Change.AlreadyHeld(VOTER, 2), service.give(MEMBER, "voter", 2).join());
    }

    @Test
    void aTierPastTheLastIsInvalid() {
        assertEquals(new Change.InvalidTier(VOTER, 3), service.give(MEMBER, "voter", 4).join());
        assertFalse(user.hasBadge(VOTER));
    }

    @Test
    void unknownBadgesAreReportedBothWays() {
        assertEquals(new Change.UnknownBadge("nope"), service.give(MEMBER, "nope", 0).join());
        assertEquals(new Change.UnknownBadge("nope"), service.revoke(MEMBER, "nope", 0).join());
    }

    @Test
    void revokingRemovesAndSaves() {
        user.addBadge(HELPER);

        assertEquals(new Change.Revoked(HELPER, 0), service.revoke(MEMBER, "helper", 0).join());
        assertFalse(user.hasBadge(HELPER));
        assertEquals(List.of(MEMBER), users.savedIds());
    }

    @Test
    void revokingATierTheyDoNotHaveIsNotHeld() {
        service.give(MEMBER, "voter", 2).join();

        assertEquals(new Change.NotHeld(VOTER, 1), service.revoke(MEMBER, "voter", 1).join());
        assertEquals(new Change.Revoked(VOTER, 2), service.revoke(MEMBER, "voter", 2).join());
    }

    @Test
    void revokingABadgeTheyDoNotHaveIsNotHeld() {
        assertEquals(new Change.NotHeld(HELPER, 0), service.revoke(MEMBER, "helper", 0).join());
    }

    @Test
    void aFailedSaveFailsTheChange() {
        users.failSavesFor(MEMBER);

        assertThrows(CompletionException.class, () -> service.give(MEMBER, "helper", 0).join());
    }

    @Test
    void aMemberWithoutARecordIsAnError() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.give("999", "helper", 0));

        assertEquals("Member 999 has no user record", thrown.getMessage());
    }
}
