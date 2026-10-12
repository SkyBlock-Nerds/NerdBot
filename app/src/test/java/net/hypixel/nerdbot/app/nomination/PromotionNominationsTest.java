package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static net.hypixel.nerdbot.app.nomination.SweepFixture.MEMBER_ROLE;
import static net.hypixel.nerdbot.app.nomination.SweepFixture.MODERATOR_ROLE;
import static net.hypixel.nerdbot.app.nomination.SweepFixture.NEW_MEMBER_ROLE;
import static net.hypixel.nerdbot.app.nomination.SweepFixture.active;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionNominationsTest {

    private final SweepFixture fixture = new SweepFixture();
    private final PromotionNominations nominations = fixture.nominations();

    private static NominationInfo nominationsOf(DiscordUser user) {
        return user.getLastActivity().getNominationInfo();
    }

    @Test
    void anActiveMemberIsNominatedRecordedAndSaved() {
        DiscordUser user = fixture.member("1", MEMBER_ROLE);
        active(user, 1, 1, 1);

        SweepReport report = nominations.nominateMembers();

        assertEquals(List.of("Promotion Nomination"), fixture.votingChannel.titles());
        assertEquals(1, nominationsOf(user).getTotalNominations());
        assertEquals(Optional.of(fixture.clock.millis()), nominationsOf(user).getLastNominationTimestamp());
        assertEquals(List.of("1"), fixture.users.savedIds());
        assertEquals(new SweepReport("Member nominations", 1, 1, 0, 0, 0, 0, 0, 0), report);
    }

    @Test
    void twoOfTheThreeThresholdsAreEnough() {
        active(fixture.member("1", MEMBER_ROLE), 1, 1, 0);

        assertEquals(1, nominations.nominateMembers().acted());
    }

    @Test
    void meetingOneThresholdIsNotEnough() {
        active(fixture.member("1", MEMBER_ROLE), 1, 0, 0);

        SweepReport report = nominations.nominateMembers();

        assertEquals(1, report.notDue());
        assertTrue(fixture.votingChannel.sent.isEmpty());
    }

    @Test
    void aMemberIsNominatedOnceAMonth() {
        DiscordUser user = fixture.member("1", MEMBER_ROLE);
        active(user, 1, 1, 1);

        nominations.nominateMembers();
        SweepReport second = nominations.nominateMembers();

        assertEquals(1, second.alreadyThisMonth());
        assertEquals(1, fixture.votingChannel.sent.size());
        assertEquals(1, nominationsOf(user).getTotalNominations());
    }

    @Test
    void aNominationLastMonthDoesNotCount() {
        DiscordUser user = fixture.member("1", MEMBER_ROLE);
        active(user, 1, 1, 1);
        nominationsOf(user).setTotalNominations(1);
        nominationsOf(user).setLastNominationTimestamp(fixture.lastMonth());

        assertEquals(1, nominations.nominateMembers().acted());
        assertEquals(2, nominationsOf(user).getTotalNominations());
    }

    @Test
    void onlyMembersWhoseHighestRoleIsTheMemberRoleAreConsidered() {
        active(fixture.member("1", NEW_MEMBER_ROLE), 1, 1, 1);
        active(fixture.member("2", MODERATOR_ROLE, MEMBER_ROLE), 1, 1, 1);
        active(fixture.member("3"), 1, 1, 1);

        SweepReport report = nominations.nominateMembers();

        assertEquals(3, report.ineligible());
        assertTrue(fixture.votingChannel.sent.isEmpty());
    }

    @Test
    void aNominationDiscordRejectsIsNotRecorded() {
        DiscordUser user = fixture.member("1", MEMBER_ROLE);
        active(user, 1, 1, 1);
        fixture.votingChannel.rejects = true;

        SweepReport report = nominations.nominateMembers();

        assertEquals(1, report.failed());
        assertEquals(0, nominationsOf(user).getTotalNominations());
        assertTrue(fixture.users.savedIds().isEmpty());
    }

    @Test
    void anUnsavedNominationIsStillCounted() {
        DiscordUser user = fixture.member("1", MEMBER_ROLE);
        active(user, 1, 1, 1);
        fixture.users.failSavesFor("1");

        assertEquals(1, nominations.nominateMembers().acted());
        assertEquals(1, nominationsOf(user).getTotalNominations());
    }

    @Test
    void withoutAVotingChannelNobodyIsChecked() {
        active(fixture.member("1", MEMBER_ROLE), 1, 1, 1);
        fixture.votingChannel.available = false;

        assertThrows(IllegalStateException.class, nominations::nominateMembers);
        assertTrue(fixture.users.savedIds().isEmpty());
    }

    @Test
    void storedMembersWhoLeftAreCountedAsMissing() {
        fixture.member("1", MEMBER_ROLE);
        fixture.guildMembers.remove("1");

        assertEquals(1, nominations.nominateMembers().missingMember());
    }

    @Test
    void aRecordWithoutActivityCountsAsNoActivity() {
        DiscordUser user = fixture.member("1", MEMBER_ROLE);
        user.setLastActivity(null);

        assertEquals(1, nominations.nominateMembers().notDue());
    }

    @Test
    void newMembersAreNominatedOnceTheyHaveBeenHereLongEnough() {
        active(fixture.member("1", OffsetDateTime.now(ZoneOffset.UTC).minusDays(31), NEW_MEMBER_ROLE), 1, 1, 1);
        active(fixture.member("2", OffsetDateTime.now(ZoneOffset.UTC).minusDays(5), NEW_MEMBER_ROLE), 1, 1, 1);
        active(fixture.member("3", MEMBER_ROLE), 1, 1, 1);

        SweepReport report = nominations.nominateNewMembers();

        assertEquals(1, report.acted());
        assertEquals(2, report.ineligible());
        assertEquals("New Member nominations", report.label());
    }
}
