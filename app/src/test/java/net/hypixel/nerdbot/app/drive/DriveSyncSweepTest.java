package net.hypixel.nerdbot.app.drive;

import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;
import net.hypixel.nerdbot.marmalade.security.AesGcmCipher;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveSyncSweepTest {

    private static final AesGcmCipher CIPHER = new AesGcmCipher("0123456789abcdef0123456789abcdef".getBytes());

    private FakeDrivePermissionClient client;
    private DrivePermissionService service;
    private GoogleDriveConfig config;
    private List<DiscordUser> saved;

    @BeforeEach
    void setUp() {
        client = new FakeDrivePermissionClient();
        service = new DrivePermissionService(CIPHER, client);
        config = new GoogleDriveConfig();
        config.setEnabled(true);
        DriveFolderMapping mapping = new DriveFolderMapping();
        mapping.setRoleId("111");
        mapping.setFolderIds(List.of("fA"));
        mapping.setAccessLevel("READER");
        config.setFolderMappings(List.of(mapping));
        saved = new ArrayList<>();
    }

    private DiscordUser linkedUser(String id) {
        DiscordUser user = new DiscordUser(id);
        DriveLinkWorkflow workflow = new DriveLinkWorkflow(service, config);
        workflow.link(user, id + "@example.com", List.of(), List.of(user));
        return user;
    }

    @Test
    void syncsLinkedMembersAgainstLiveRoles() {
        DiscordUser user = linkedUser("42");
        Map<String, List<String>> guildRoles = Map.of("42", List.of("111"));

        DriveSyncSweep.Result result = DriveSyncSweep.reconcileUsers(
            List.of(user), id -> Optional.ofNullable(guildRoles.get(id)), service, config, saved::add);

        assertEquals(1, result.synced());
        assertEquals(1, user.getDriveAccess().getGrants().size());
        assertEquals(List.of(user), saved);
    }

    @Test
    void revokesAndForgetsDepartedMembers() {
        DiscordUser user = linkedUser("42");
        DriveSyncSweep.Result result = DriveSyncSweep.reconcileUsers(
            List.of(user), id -> Optional.empty(), service, config, saved::add);

        assertEquals(1, result.departed());
        assertNull(user.getDriveAccess());
        assertEquals(List.of(user), saved);
    }

    @Test
    void skipsUnlinkedUsersEntirely() {
        DiscordUser user = new DiscordUser("42");
        DriveSyncSweep.Result result = DriveSyncSweep.reconcileUsers(
            List.of(user), id -> Optional.of(List.of("111")), service, config, saved::add);

        assertEquals(0, result.synced());
        assertTrue(saved.isEmpty());
    }

    @Test
    void healsGrantDeletedManuallyInDrive() {
        DiscordUser user = linkedUser("42");
        DriveSyncSweep.Result first = DriveSyncSweep.reconcileUsers(
            List.of(user), id -> Optional.of(List.of("111")), service, config, saved::add);
        assertEquals(1, first.synced());

        // Someone deletes the permission directly in Drive; our stored id now dangles
        client.folders.get("fA").clear();

        DriveSyncSweep.Result second = DriveSyncSweep.reconcileUsers(
            List.of(user), id -> Optional.of(List.of("111")), service, config, saved::add);

        assertEquals(1, second.synced());
        assertEquals(1, client.folders.get("fA").size()); // regranted
    }

    @Test
    void countsFailuresWithoutAborting() {
        DiscordUser userA = linkedUser("1");
        DiscordUser userB = linkedUser("2");
        client.failPermanently("fA", 403);

        DriveSyncSweep.Result result = DriveSyncSweep.reconcileUsers(
            List.of(userA, userB), id -> Optional.of(List.of("111")), service, config, saved::add);

        assertEquals(2, result.failed());
        assertEquals(2, saved.size()); // state still persisted for both
    }

    @Test
    void lookupFailureLeavesUserUntouched() {
        // Link with the mapped role so we get a grant
        DiscordUser user = new DiscordUser("42");
        DriveLinkWorkflow workflow = new DriveLinkWorkflow(service, config);
        workflow.link(user, "42@example.com", List.of("111"), List.of(user));

        // Verify user is linked and has a grant stored
        assertNotNull(user.getDriveAccess());
        assertTrue(user.getDriveAccess().getGrants().size() > 0);
        int initialGrantCount = user.getDriveAccess().getGrants().size();

        DriveSyncSweep.Result result = DriveSyncSweep.reconcileUsers(
            List.of(user), id -> {
                throw new RuntimeException("discord hiccup");
            }, service, config, saved::add);

        // Failed lookup must not revoke or forget
        assertEquals(0, result.departed());
        assertEquals(1, result.failed());
        // User's drive access must be intact (not null, grants untouched in count)
        assertNotNull(user.getDriveAccess());
        assertEquals(initialGrantCount, user.getDriveAccess().getGrants().size());
        // Saver must not have been called
        assertTrue(saved.isEmpty());
    }
}
