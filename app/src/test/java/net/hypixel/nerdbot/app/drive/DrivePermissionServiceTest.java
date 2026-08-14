package net.hypixel.nerdbot.app.drive;

import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;
import net.hypixel.nerdbot.app.drive.DrivePermissionService.SyncOutcome;
import net.hypixel.nerdbot.marmalade.google.drive.DriveAccessLevel;
import net.hypixel.nerdbot.marmalade.security.AesGcmCipher;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveAccess;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrivePermissionServiceTest {

    private static final AesGcmCipher CIPHER = new AesGcmCipher("0123456789abcdef0123456789abcdef".getBytes());

    private static GoogleDriveConfig config(DriveFolderMapping... mappings) {
        GoogleDriveConfig config = new GoogleDriveConfig();
        config.setEnabled(true);
        config.setFolderMappings(List.of(mappings));
        return config;
    }

    private static DriveFolderMapping mapping(String roleId, String accessLevel, String... folderIds) {
        DriveFolderMapping mapping = new DriveFolderMapping();
        mapping.setRoleId(roleId);
        mapping.setFolderIds(List.of(folderIds));
        mapping.setAccessLevel(accessLevel);
        return mapping;
    }

    private static DriveAccess linkedAccess() {
        return new DriveAccess(CIPHER.encrypt("user@example.com"), CIPHER.hmac("user@example.com"));
    }

    private DrivePermissionService service(FakeDrivePermissionClient client) {
        return new DrivePermissionService(CIPHER, client);
    }

    // ── computeDesiredGrants ────────────────────────────────────────────

    @Test
    void noMappedRolesMeansNoGrants() {
        Map<String, DriveAccessLevel> desired = DrivePermissionService.computeDesiredGrants(
            List.of("999"), config(mapping("111", "READER", "fA")));
        assertTrue(desired.isEmpty());
    }

    @Test
    void unionsAcrossHeldRoles() {
        Map<String, DriveAccessLevel> desired = DrivePermissionService.computeDesiredGrants(
            List.of("111", "222"),
            config(mapping("111", "READER", "fA", "fB"), mapping("222", "COMMENTER", "fC")));
        assertEquals(Map.of("fA", DriveAccessLevel.READER, "fB", DriveAccessLevel.READER, "fC", DriveAccessLevel.COMMENTER), desired);
    }

    @Test
    void mostPermissiveLevelWinsPerFolder() {
        Map<String, DriveAccessLevel> desired = DrivePermissionService.computeDesiredGrants(
            List.of("111", "222"),
            config(mapping("111", "WRITER", "fA"), mapping("222", "READER", "fA")));
        assertEquals(Map.of("fA", DriveAccessLevel.WRITER), desired);
    }

    @Test
    void invalidAccessLevelInConfigSkipsThatMapping() {
        Map<String, DriveAccessLevel> desired = DrivePermissionService.computeDesiredGrants(
            List.of("111", "222"),
            config(mapping("111", "OWNER", "fA"), mapping("222", "READER", "fB")));
        assertEquals(Map.of("fB", DriveAccessLevel.READER), desired);
    }

    @Test
    void disabledConfigYieldsNothing() {
        GoogleDriveConfig disabled = config(mapping("111", "READER", "fA"));
        disabled.setEnabled(false);
        assertTrue(DrivePermissionService.computeDesiredGrants(List.of("111"), disabled).isEmpty());
    }

    // ── syncGrants ──────────────────────────────────────────────────────

    @Test
    void grantsMissingFolders() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        DriveAccess access = linkedAccess();

        SyncOutcome outcome = service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA", "fB")));

        assertEquals(List.of("fA", "fB"), outcome.grantedFolders().stream().sorted().toList());
        assertEquals(2, access.getGrants().size());
        assertTrue(access.getLastSyncedAt() > 0);
    }

    @Test
    void revokesStaleFolders() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        DriveAccess access = linkedAccess();
        service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA", "fB")));

        SyncOutcome outcome = service(client).syncGrants(access, List.of(), config(mapping("111", "READER", "fA", "fB")));

        assertEquals(2, outcome.revokedFolders().size());
        assertTrue(access.getGrants().isEmpty());
        assertTrue(client.folders.get("fA").isEmpty());
    }

    @Test
    void levelChangeRevokesThenRegrants() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        DriveAccess access = linkedAccess();
        service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA")));

        service(client).syncGrants(access, List.of("222"), config(mapping("111", "READER", "fA"), mapping("222", "WRITER", "fA")));

        assertEquals(1, access.getGrants().size());
        assertEquals("WRITER", access.getGrants().getFirst().accessLevel());
        assertEquals(1, client.folders.get("fA").size());
        assertEquals("writer", client.folders.get("fA").getFirst().role());
    }

    @Test
    void transientFailureIsRetriedThenSucceeds() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        client.failTransiently("fA", 2); // fewer than max attempts
        DriveAccess access = linkedAccess();

        SyncOutcome outcome = service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA")));

        assertEquals(List.of("fA"), outcome.grantedFolders());
        assertTrue(outcome.failedFolders().isEmpty());
    }

    @Test
    void permanentFailureLandsInFailedWithoutRecordingGrant() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        client.failPermanently("fA", 400);
        DriveAccess access = linkedAccess();

        SyncOutcome outcome = service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA", "fB")));

        assertEquals(List.of("fA"), outcome.failedFolders());
        assertEquals(List.of("fB"), outcome.grantedFolders());
        assertEquals(1, access.getGrants().size()); // only fB recorded — reconcile will retry fA later
    }

    @Test
    void failedRevokeKeepsGrantRecordedForReconcile() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        DriveAccess access = linkedAccess();
        service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA")));
        client.failPermanently("fA", 403);

        SyncOutcome outcome = service(client).syncGrants(access, List.of(), config(mapping("111", "READER", "fA")));

        assertEquals(List.of("fA"), outcome.failedFolders());
        assertEquals(1, access.getGrants().size());
    }

    @Test
    void undecryptableEmailSyncsNothing() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        DriveAccess access = new DriveAccess("garbage-not-ciphertext", "hash");

        SyncOutcome outcome = service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA")));

        assertTrue(outcome.grantedFolders().isEmpty());
        assertEquals(List.of("fA"), outcome.failedFolders());
        assertTrue(client.operations.isEmpty());
    }

    // ── revokeAll ───────────────────────────────────────────────────────

    @Test
    void revokeAllClearsEverything() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        DriveAccess access = linkedAccess();
        service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA", "fB")));

        assertTrue(service(client).revokeAll(access));
        assertTrue(access.getGrants().isEmpty());
        assertTrue(client.folders.get("fA").isEmpty());
    }

    @Test
    void revokeAllReportsFailureButRemovesWhatItCan() {
        FakeDrivePermissionClient client = new FakeDrivePermissionClient();
        DriveAccess access = linkedAccess();
        service(client).syncGrants(access, List.of("111"), config(mapping("111", "READER", "fA", "fB")));
        client.failPermanently("fA", 403);

        assertFalse(service(client).revokeAll(access));
        assertTrue(client.folders.get("fB").isEmpty());
    }

    // ── email helpers ───────────────────────────────────────────────────

    @Test
    void normalizesAndValidatesEmails() {
        assertEquals("user@example.com", DrivePermissionService.normalizeEmail("  User@Example.COM "));
        assertTrue(DrivePermissionService.isValidEmail("user@example.com"));
        assertFalse(DrivePermissionService.isValidEmail("not-an-email"));
        assertFalse(DrivePermissionService.isValidEmail("a@b"));
        assertFalse(DrivePermissionService.isValidEmail("two@at@signs.com"));
        assertFalse(DrivePermissionService.isValidEmail(""));
    }
}
