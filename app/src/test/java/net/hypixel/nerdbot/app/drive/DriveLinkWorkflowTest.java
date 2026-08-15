package net.hypixel.nerdbot.app.drive;

import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;
import net.hypixel.nerdbot.app.drive.DriveLinkWorkflow.LinkResult;
import net.hypixel.nerdbot.marmalade.security.AesGcmCipher;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveLinkWorkflowTest {

    private static final AesGcmCipher CIPHER = new AesGcmCipher("0123456789abcdef0123456789abcdef".getBytes());

    private FakeDrivePermissionClient client;
    private DrivePermissionService service;
    private GoogleDriveConfig config;
    private DriveLinkWorkflow workflow;

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
        workflow = new DriveLinkWorkflow(service, config);
    }

    @Test
    void linkEncryptsStoresAndSyncs() {
        DiscordUser user = new DiscordUser("42");

        LinkResult result = workflow.link(user, "  User@Example.com ", List.of("111"), List.of(user));

        assertEquals(DriveLinkWorkflow.Status.LINKED, result.status());
        assertNotNull(user.getDriveAccess());
        assertEquals(CIPHER.hmac("user@example.com"), user.getDriveAccess().getEmailHash());
        assertFalse(user.getDriveAccess().getEncryptedEmail().contains("example.com"));
        assertEquals(1, user.getDriveAccess().getGrants().size());
        assertEquals("user@example.com", client.folders.get("fA").getFirst().emailAddress());
    }

    @Test
    void linkRejectsInvalidEmail() {
        DiscordUser user = new DiscordUser("42");
        LinkResult result = workflow.link(user, "not-an-email", List.of("111"), List.of(user));
        assertEquals(DriveLinkWorkflow.Status.INVALID_EMAIL, result.status());
        assertNull(user.getDriveAccess());
    }

    @Test
    void linkRejectsEmailLinkedToAnotherMember() {
        DiscordUser other = new DiscordUser("99");
        workflow.link(other, "user@example.com", List.of(), List.of(other));

        DiscordUser user = new DiscordUser("42");
        LinkResult result = workflow.link(user, "USER@example.com", List.of("111"), List.of(other, user));

        assertEquals(DriveLinkWorkflow.Status.DUPLICATE_EMAIL, result.status());
        assertNull(user.getDriveAccess());
    }

    @Test
    void relinkingSameMemberWithNewEmailRevokesOldGrantsFirst() {
        DiscordUser user = new DiscordUser("42");
        workflow.link(user, "old@example.com", List.of("111"), List.of(user));
        String oldPermissionId = user.getDriveAccess().getGrants().getFirst().permissionId();

        LinkResult result = workflow.link(user, "new@example.com", List.of("111"), List.of(user));

        assertEquals(DriveLinkWorkflow.Status.LINKED, result.status());
        assertEquals(1, client.folders.get("fA").size());
        assertEquals("new@example.com", client.folders.get("fA").getFirst().emailAddress());
        assertFalse(user.getDriveAccess().getGrants().getFirst().permissionId().equals(oldPermissionId));
    }

    @Test
    void relinkingSameEmailIsAllowedForSameUser() {
        DiscordUser user = new DiscordUser("42");
        workflow.link(user, "user@example.com", List.of("111"), List.of(user));
        LinkResult result = workflow.link(user, "user@example.com", List.of("111"), List.of(user));
        assertEquals(DriveLinkWorkflow.Status.LINKED, result.status());
    }

    @Test
    void unlinkRevokesAndDeletes() {
        DiscordUser user = new DiscordUser("42");
        workflow.link(user, "user@example.com", List.of("111"), List.of(user));

        assertTrue(workflow.unlink(user));

        assertNull(user.getDriveAccess());
        assertTrue(client.folders.get("fA").isEmpty());
    }

    @Test
    void unlinkWhenNotLinkedReturnsFalse() {
        assertFalse(workflow.unlink(new DiscordUser("42")));
    }

    @Test
    void revokeAndForgetAlwaysClearsStateEvenOnRevokeFailure() {
        DiscordUser user = new DiscordUser("42");
        workflow.link(user, "user@example.com", List.of("111"), List.of(user));
        client.failPermanently("fA", 403);

        workflow.revokeAndForget(user);

        assertNull(user.getDriveAccess());
    }
}
