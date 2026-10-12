package net.hypixel.nerdbot.app.nomination;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static net.hypixel.nerdbot.app.nomination.SweepFixture.MEMBER_ROLE;
import static net.hypixel.nerdbot.app.nomination.SweepFixture.MODERATOR_ROLE;
import static net.hypixel.nerdbot.app.nomination.SweepFixture.NEW_MEMBER_ROLE;
import static net.hypixel.nerdbot.app.nomination.SweepFixture.active;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InactivityWarningsTest {

    private final SweepFixture fixture = new SweepFixture();
    private final InactivityWarnings warnings = fixture.warnings();

    private static NominationInfo warningsOf(DiscordUser user) {
        return user.getLastActivity().getNominationInfo();
    }

    private static List<String> fieldNames(MessageEmbed embed) {
        return embed.getFields().stream().map(MessageEmbed.Field::getName).toList();
    }

    @Nested
    class MembersAndNewMembers {

        @Test
        void anInactiveMemberIsWarnedRecordedAndSaved() {
            DiscordUser user = fixture.member("1", MEMBER_ROLE);
            active(user, 1, 0, 0);

            SweepReport report = warnings.checkMembers();

            assertEquals(List.of("Member Inactivity Warning"), fixture.votingChannel.titles());
            assertEquals(1, warningsOf(user).getTotalInactivityWarnings());
            assertEquals(Optional.of(fixture.clock.millis()), warningsOf(user).getLastInactivityWarningTimestamp());
            assertEquals(List.of("1"), fixture.users.savedIds());
            assertEquals(new SweepReport("Member inactivity", 1, 1, 0, 0, 0, 0, 0, 0), report);
        }

        @Test
        void aMemberMeetingTwoThresholdsIsActive() {
            active(fixture.member("1", MEMBER_ROLE), 1, 1, 0);

            assertEquals(1, warnings.checkMembers().notDue());
            assertTrue(fixture.votingChannel.sent.isEmpty());
        }

        @Test
        void newMembersNeedAllThree() {
            active(fixture.member("1", NEW_MEMBER_ROLE), 1, 1, 0);

            assertEquals(1, warnings.checkNewMembers().acted());
            assertEquals(List.of("New Member Inactivity Warning"), fixture.votingChannel.titles());
        }

        @Test
        void eachMemberIsOnlyCheckedByTheSweepForTheirHighestRole() {
            fixture.member("1", NEW_MEMBER_ROLE);
            fixture.member("2", MEMBER_ROLE);
            fixture.member("3", MODERATOR_ROLE, MEMBER_ROLE);

            SweepReport members = warnings.checkMembers();
            SweepReport newMembers = warnings.checkNewMembers();

            assertEquals(1, members.acted());
            assertEquals(2, members.ineligible());
            assertEquals(1, newMembers.acted());
            assertEquals(2, newMembers.ineligible());
        }

        @Test
        void aMemberIsWarnedOnceAMonth() {
            DiscordUser user = fixture.member("1", MEMBER_ROLE);

            warnings.checkMembers();
            SweepReport second = warnings.checkMembers();

            assertEquals(1, second.alreadyThisMonth());
            assertEquals(1, warningsOf(user).getTotalInactivityWarnings());
        }

        @Test
        void aWarningLastMonthDoesNotCount() {
            DiscordUser user = fixture.member("1", MEMBER_ROLE);
            warningsOf(user).setLastInactivityWarningTimestamp(fixture.lastMonth());

            assertEquals(1, warnings.checkMembers().acted());
        }

        @Test
        void aWarningDiscordRejectsIsNotRecorded() {
            DiscordUser user = fixture.member("1", MEMBER_ROLE);
            fixture.votingChannel.rejects = true;

            SweepReport report = warnings.checkMembers();

            assertEquals(1, report.failed());
            assertEquals(0, warningsOf(user).getTotalInactivityWarnings());
            assertTrue(fixture.users.savedIds().isEmpty());
        }

        @Test
        void openTicketsAreListed() {
            fixture.member("1", MEMBER_ROLE);
            fixture.tickets.put("1", List.of("#0001: <#900>"));

            warnings.checkMembers();

            MessageEmbed warning = fixture.votingChannel.sent.getFirst();
            assertEquals("Open Tickets", warning.getFields().getLast().getName());
            assertEquals("#0001: <#900>", warning.getFields().getLast().getValue());
        }

        @Test
        void withoutAVotingChannelNobodyIsChecked() {
            fixture.member("1", MEMBER_ROLE);
            fixture.votingChannel.available = false;

            assertThrows(IllegalStateException.class, warnings::checkMembers);
            assertTrue(fixture.users.savedIds().isEmpty());
        }
    }

    @Nested
    class RestrictedGroups {

        private RoleRestrictedChannelGroup group(String identifier, String roleId) {
            RoleRestrictedChannelGroup group = new RoleRestrictedChannelGroup();
            group.setIdentifier(identifier);
            group.setDisplayName(identifier.toUpperCase());
            group.setChannelIds(new String[]{"700"});
            group.setRequiredRoleIds(new String[]{roleId});
            group.setMinimumMessagesForActivity(1);
            group.setMinimumVotesForActivity(1);
            group.setMinimumCommentsForActivity(1);
            return group;
        }

        @Test
        void membersInactiveInAGroupAreWarnedForThatGroup() {
            fixture.config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(group("staff", MODERATOR_ROLE)));
            DiscordUser user = fixture.member("1", MODERATOR_ROLE);
            active(user, 5, 5, 5);

            List<SweepReport> reports = warnings.checkRestrictedGroups();

            assertEquals(1, reports.getFirst().acted());
            MessageEmbed warning = fixture.votingChannel.sent.getFirst();
            assertEquals("Role-Restricted Inactivity Warning - STAFF", warning.getTitle());
            assertEquals("Channel Group", warning.getFields().getFirst().getName());
            assertEquals(Optional.of(fixture.clock.millis()), warningsOf(user).getLastRoleRestrictedInactivityWarningTimestamp("staff"));
            assertEquals(1, warningsOf(user).getTotalRoleRestrictedInactivityWarnings());
            assertEquals(0, warningsOf(user).getTotalInactivityWarnings());
        }

        @Test
        void activityInTheGroupCounts() {
            fixture.config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(group("staff", MODERATOR_ROLE)));
            DiscordUser user = fixture.member("1", MODERATOR_ROLE);
            long now = System.currentTimeMillis();
            user.getLastActivity().addRoleRestrictedChannelActivity("staff", "700", "staff-chat", 1, now);
            user.getLastActivity().addRoleRestrictedChannelVote("staff", "800", now);

            assertEquals(1, warnings.checkRestrictedGroups().getFirst().notDue());
        }

        @Test
        void eachGroupHasItsOwnMonthlyLimit() {
            fixture.config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(group("staff", MODERATOR_ROLE), group("testers", MODERATOR_ROLE)));
            DiscordUser user = fixture.member("1", MODERATOR_ROLE);
            warningsOf(user).getLastRoleRestrictedInactivityWarningTimestamps().put("staff", fixture.clock.millis());

            List<SweepReport> reports = warnings.checkRestrictedGroups();

            assertEquals(1, reports.get(0).alreadyThisMonth());
            assertEquals(1, reports.get(1).acted());
            assertEquals(List.of("Role-Restricted Inactivity Warning - TESTERS"), fixture.votingChannel.titles());
        }

        @Test
        void membersWithoutTheGroupsRoleAreLeftOut() {
            fixture.config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(group("staff", MODERATOR_ROLE)));
            fixture.member("1", MEMBER_ROLE);

            assertEquals(1, warnings.checkRestrictedGroups().getFirst().ineligible());
        }

        @Test
        void groupsWithWarningsOffAreSkipped() {
            RoleRestrictedChannelGroup quiet = group("staff", MODERATOR_ROLE);
            quiet.setVotingNotificationsEnabled(false);
            fixture.config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(quiet));
            fixture.member("1", MODERATOR_ROLE);

            assertEquals(List.of(), warnings.checkRestrictedGroups());
            assertTrue(fixture.votingChannel.sent.isEmpty());
        }

        @Test
        void withNoGroupsTheVotingChannelIsntNeeded() {
            fixture.votingChannel.available = false;

            assertEquals(List.of(), warnings.checkRestrictedGroups());
        }

        @Test
        void theWarningListsTheGroupFirst() {
            fixture.config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(group("staff", MODERATOR_ROLE)));
            fixture.member("1", MODERATOR_ROLE);

            warnings.checkRestrictedGroups();

            assertEquals(List.of("Channel Group", "Activity Summary (last 30 days)", "Messages", "Votes", "Comments"),
                fieldNames(fixture.votingChannel.sent.getFirst()));
        }
    }
}
