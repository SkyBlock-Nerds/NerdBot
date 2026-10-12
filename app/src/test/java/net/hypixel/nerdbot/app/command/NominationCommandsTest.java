package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.app.nomination.NominationSweeps;
import net.hypixel.nerdbot.app.nomination.SweepReport;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NominationCommandsTest {

    private static final SweepReport NOMINATIONS = new SweepReport("Member nominations", 50, 4, 2, 3, 30, 10, 1, 1234);
    private static final SweepReport INACTIVITY = new SweepReport("Member inactivity", 40, 5, 2, 20, 10, 3, 0, 987);

    private final CallRecorder recorder = new CallRecorder();
    private final List<String> ran = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private boolean sweepFails;
    private boolean anotherSweepRunning;
    private List<SweepReport> groupReports = List.of();

    private final NominationSweeps sweeps = new NominationSweeps() {
        @Override
        public Optional<SweepReport> nominateMembers() {
            return record("members", NOMINATIONS);
        }

        @Override
        public Optional<SweepReport> nominateNewMembers() {
            return record("new members", NOMINATIONS);
        }

        @Override
        public Optional<SweepReport> checkMemberInactivity() {
            return record("member inactivity", INACTIVITY);
        }

        @Override
        public Optional<SweepReport> checkNewMemberInactivity() {
            return record("new member inactivity", INACTIVITY);
        }

        @Override
        public Optional<List<SweepReport>> checkRestrictedChannelInactivity() {
            return record("restricted inactivity", groupReports);
        }

        private <T> Optional<T> record(String sweep, T report) {
            if (sweepFails) {
                throw new IllegalStateException("guild not loaded");
            }
            if (anotherSweepRunning) {
                return Optional.empty();
            }
            ran.add(sweep);
            return Optional.of(report);
        }
    };

    private final NominationCommands commands = new NominationCommands(sweeps, users, queued::add);

    @Test
    void theSweepRunsAfterTheReplyIsDeferred() {
        commands.nominate(DiscordFakes.slash(recorder, "1"), false);

        assertTrue(recorder.called("deferReply"));
        assertTrue(ran.isEmpty());

        queued.getFirst().run();
        assertEquals(List.of("members"), ran);
        MessageEmbed report = DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst();
        assertEquals("Nomination Sweep Complete (Member)", report.getTitle());
        assertEquals("1234ms", report.getFields().getLast().getValue());
    }

    @Test
    void newMembersOnlyRunsTheNewMemberSweep() {
        commands.nominate(DiscordFakes.slash(recorder, "1"), true);
        queued.getFirst().run();

        assertEquals(List.of("new members"), ran);
    }

    @Test
    void inactivitySweepsReportTheirOwnFields() {
        commands.checkInactivity(DiscordFakes.slash(recorder, "1"), true);
        queued.getFirst().run();

        assertEquals(List.of("new member inactivity"), ran);
        MessageEmbed report = DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst();
        assertEquals("Inactivity Sweep Complete (New Member)", report.getTitle());
        assertEquals(List.of("Scanned", "Warned", "Active", "Already Warned This Month", "Ineligible", "Missing Member", "Failed", "Duration"),
            report.getFields().stream().map(MessageEmbed.Field::getName).toList());
        assertEquals("5", report.getFields().get(1).getValue());
        assertEquals("20", report.getFields().get(2).getValue());
    }

    @Test
    void nominationReportsCountTheEligible() {
        MessageEmbed report = NominationCommands.nominationReport("title", NOMINATIONS);

        assertEquals("Eligible", report.getFields().get(1).getName());
        assertEquals("10", report.getFields().get(1).getValue());
        assertEquals("4", report.getFields().get(2).getValue());
    }

    @Test
    void aSweepAlreadyRunningSaysSo() {
        anotherSweepRunning = true;

        commands.nominate(DiscordFakes.slash(recorder, "1"), false);
        queued.getFirst().run();

        assertTrue(ran.isEmpty());
        assertEquals(NominationCommands.SWEEP_BUSY_MESSAGE, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aSweepThatFailsSaysSo() {
        sweepFails = true;

        commands.checkInactivity(DiscordFakes.slash(recorder, "1"), false);
        queued.getFirst().run();

        assertEquals("The sweep failed. Check the logs for details.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void theRestrictedSweepReportsEachGroup() {
        groupReports = List.of(new SweepReport("Staff inactivity", 40, 2, 1, 5, 32, 0, 0, 10),
            new SweepReport("Testers inactivity", 40, 0, 0, 3, 37, 0, 1, 10));

        commands.checkRestrictedInactivity(DiscordFakes.slash(recorder, "1"));
        queued.getFirst().run();

        assertEquals(List.of("restricted inactivity"), ran);
        MessageEmbed report = DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst();
        assertEquals(List.of("Staff inactivity", "Testers inactivity"), report.getFields().stream().map(MessageEmbed.Field::getName).toList());
        assertTrue(report.getFields().getFirst().getValue().startsWith("Warned: 2"));
    }

    @Test
    void withNoGroupsTheRestrictedSweepSaysSo() {
        commands.checkRestrictedInactivity(DiscordFakes.slash(recorder, "1"));
        queued.getFirst().run();

        MessageEmbed report = DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst();
        assertEquals("No restricted channel groups have inactivity warnings turned on.", report.getDescription());
    }

    @Test
    void resettingClearsWarningsForEveryStoredMemberAndSaves() {
        DiscordUser warned = warned("1");
        DiscordUser alsoWarned = warned("2");
        users.seed(warned).seed(alsoWarned);

        commands.resetInactivityWarnings(DiscordFakes.slash(recorder, "1"));
        queued.getFirst().run();

        for (DiscordUser user : List.of(warned, alsoWarned)) {
            NominationInfo info = user.getLastActivity().getNominationInfo();
            assertEquals(0, info.getTotalInactivityWarnings());
            assertEquals(Optional.empty(), info.getLastInactivityWarningTimestamp());
        }
        assertEquals(List.of("1", "2"), users.savedIds());
        assertEquals("Reset inactivity check data for 2 users.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aResetThatCantSaveSaysSo() {
        users.seed(warned("1")).failSavesFor("1");

        commands.resetInactivityWarnings(DiscordFakes.slash(recorder, "1"));
        queued.getFirst().run();

        assertEquals("Resetting the inactivity data failed. Check the logs for details.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aRecordWithoutActivityIsLeftAsItIs() {
        DiscordUser bare = new DiscordUser("3");
        bare.setLastActivity(null);
        users.seed(bare);

        commands.resetInactivityWarnings(DiscordFakes.slash(recorder, "1"));
        queued.getFirst().run();

        assertFalse(DiscordFakes.lastEditText(recorder).startsWith("Resetting"));
    }

    private static DiscordUser warned(String id) {
        DiscordUser user = new DiscordUser(id);
        user.getLastActivity().getNominationInfo().setTotalInactivityWarnings(3);
        user.getLastActivity().getNominationInfo().setLastInactivityWarningTimestamp(1_000L);
        return user;
    }
}
