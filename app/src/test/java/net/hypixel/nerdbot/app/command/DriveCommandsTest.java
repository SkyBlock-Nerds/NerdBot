package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.drive.DrivePermissionService.FailedFolder;
import net.hypixel.nerdbot.app.drive.DrivePermissionService.SyncOutcome;
import net.hypixel.nerdbot.app.drive.DriveSyncSweep;
import net.hypixel.nerdbot.app.drive.FakeDrivePermissionClient;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.security.AesGcmCipher;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveCommandsTest {

    private static final AesGcmCipher CIPHER = new AesGcmCipher("0123456789abcdef0123456789abcdef".getBytes());
    private static final String MEMBER = "42";
    private static final String OTHER = "43";

    private final CallRecorder recorder = new CallRecorder();
    private final FakeDrivePermissionClient client = new FakeDrivePermissionClient();
    private final NerdBotConfig config = new NerdBotConfig();
    private final DiscordUser user = new DiscordUser(MEMBER);
    private final FakeDiscordUserStore users = new FakeDiscordUserStore().seed(user);
    private final List<MessageEmbed> logged = new ArrayList<>();
    private Optional<DrivePermissionService> service = Optional.of(new DrivePermissionService(CIPHER, client));
    private DriveSyncSweep.Result sweepResult = new DriveSyncSweep.Result(3, 1, 0);
    private final DriveCommands commands = new DriveCommands(() -> service, () -> config, users, logged::add, () -> new DriveSyncSweep() {
        @Override
        public Result run() {
            return sweepResult;
        }
    });

    @BeforeEach
    void mapRoleToFolder() {
        GoogleDriveConfig drive = config.getGoogleDriveConfig();
        drive.setEnabled(true);
        DriveFolderMapping mapping = new DriveFolderMapping();
        mapping.setRoleId("111");
        mapping.setFolderIds(List.of("fA"));
        mapping.setAccessLevel("READER");
        drive.setFolderMappings(List.of(mapping));
    }

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void linkOpensTheEmailModal() {
        commands.openLinkModal(slash(MEMBER));

        assertEquals("drive-email-modal-" + MEMBER, ((Modal) recorder.last("replyModal").arg(0)).getId());
    }

    @Test
    void nothingWorksWhenDriveIsNotConfigured() {
        service = Optional.empty();

        commands.openLinkModal(slash(MEMBER));
        assertEquals(DriveCommands.NOT_CONFIGURED_MESSAGE, recorder.last("reply").arg(0));

        commands.unlink(slash(MEMBER));
        commands.sync(slash(MEMBER));
        assertEquals(3, recorder.calls("reply").size());
        assertFalse(recorder.called("replyModal"));
    }

    @Test
    void submittingAnEmailLinksSavesAndLogs() {
        commands.submitEmail(modal(MEMBER, MEMBER, "  User@Example.com "));

        assertNotNull(user.getDriveAccess());
        assertEquals(List.of(MEMBER), users.savedIds());
        assertEquals("user@example.com", client.folders.get("fA").getFirst().emailAddress());
        assertEquals(1, logged.size());
        assertEquals("Linked! You now have access to 1 folder(s).", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void anInvalidEmailSavesNothing() {
        commands.submitEmail(modal(MEMBER, MEMBER, "not-an-email"));

        assertNull(user.getDriveAccess());
        assertTrue(users.savedIds().isEmpty());
        assertEquals("That doesn't look like a valid email address, so nothing was saved.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void anEmailLinkedToSomeoneElseIsRefused() {
        users.seed(new DiscordUser(OTHER));
        commands.submitEmail(modal(OTHER, OTHER, "user@example.com"));

        commands.submitEmail(modal(MEMBER, MEMBER, "user@example.com"));

        assertNull(user.getDriveAccess());
        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("That email is already linked to another member."));
    }

    @Test
    void aFailedSaveIsReported() {
        users.failSavesFor(MEMBER);

        commands.submitEmail(modal(MEMBER, MEMBER, "user@example.com"));

        assertTrue(DiscordFakes.lastEditText(recorder).contains("saving the link failed"));
    }

    @Test
    void someoneElsesModalIsRefused() {
        commands.submitEmail(modal(OTHER, MEMBER, "user@example.com"));

        assertEquals(DriveCommands.NOT_YOUR_MODAL, recorder.last("reply").arg(0));
        assertNull(user.getDriveAccess());
    }

    @Test
    void aMemberWithoutARecordIsLeftToTheCommandLibrary() {
        assertThrows(IllegalStateException.class, () -> commands.submitEmail(modal("999", "999", "user@example.com")));
    }

    @Test
    void unlinkingClearsTheLinkAndLogsTheRevokedFolders() {
        commands.submitEmail(modal(MEMBER, MEMBER, "user@example.com"));
        logged.clear();

        commands.unlink(slash(MEMBER));

        assertNull(user.getDriveAccess());
        assertEquals(List.of(MEMBER), users.clearedDriveAccessIds());
        assertEquals(1, logged.size());
        assertEquals("Unlinked. Your Drive access has been revoked and your email deleted.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void unlinkingWithoutALinkSaysSo() {
        commands.unlink(slash(MEMBER));

        assertEquals("You don't have a linked email.", DiscordFakes.lastEditText(recorder));
        assertTrue(users.clearedDriveAccessIds().isEmpty());
    }

    @Test
    void statusWithoutALink() {
        commands.showStatus(slash(MEMBER));

        assertEquals("No email linked. Use `/drive link` to get Drive access.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void statusListsTheGrants() {
        commands.submitEmail(modal(MEMBER, MEMBER, "user@example.com"));

        commands.showStatus(slash(MEMBER));

        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("Email linked. Folder access: fA (reader)"));
    }

    @Test
    void syncReportsTheSweep() {
        commands.sync(slash(MEMBER));

        assertEquals("Reconcile finished: " + sweepResult.summary(), DiscordFakes.lastEditText(recorder));
    }

    @Test
    void linkSummariesExplainPartialFailures() {
        assertEquals("Linked! You now have access to 2 folder(s).",
            DriveCommands.linkSummary(new SyncOutcome(List.of("a", "b"), List.of(), List.of())));
        assertEquals("Linked! 1 folder(s) granted now and 1 folder(s) queued while Google rate limits us; they'll arrive within the hour.",
            DriveCommands.linkSummary(new SyncOutcome(List.of("a"), List.of(), List.of(new FailedFolder("b", 403, "userRateLimitExceeded")))));
        assertEquals("Linked! Your 1 folder(s) queued while Google rate limits us; they'll arrive within the hour.",
            DriveCommands.linkSummary(new SyncOutcome(List.of(), List.of(), List.of(new FailedFolder("b", 429, "rateLimitExceeded")))));
        assertEquals("Google rejected that address for 1 folder(s). Make sure it's a Google account email.",
            DriveCommands.linkSummary(new SyncOutcome(List.of(), List.of(), List.of(new FailedFolder("b", 400, "invalid")))));
        assertEquals("Linked! 1 folder(s) couldn't be granted right now; retrying within the hour.",
            DriveCommands.linkSummary(new SyncOutcome(List.of(), List.of(), List.of(new FailedFolder("b", 500, "backendError")))));
    }

    private SlashCommandInteraction slash(String userId) {
        return DiscordFakes.slash(recorder, userId);
    }

    private ModalInteraction modal(String userId, String ownerId, String email) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getUser", DiscordFakes.user(userId));
        answers.put("getModalId", DriveCommands.MODAL.build(ownerId));
        answers.put("getMember", Fakes.of(Member.class, Map.of("getRoles", List.of(Fakes.of(Role.class, Map.of("getId", "111"))))));
        answers.put("getValues", List.of(DiscordFakes.modalValue("email", email)));
        return recorder.fake(ModalInteraction.class, answers);
    }
}
