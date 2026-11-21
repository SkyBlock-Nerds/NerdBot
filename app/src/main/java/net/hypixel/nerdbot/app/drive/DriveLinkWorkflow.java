package net.hypixel.nerdbot.app.drive;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveAccess;

import java.util.Collection;

/**
 * The link/unlink lifecycle around {@link DrivePermissionService}, kept free of
 * JDA types so every rule (validation, duplicate emails, relink semantics,
 * delete-on-forget) is unit-testable. The command and listener layers are thin
 * glue over this class; persistence stays with the caller.
 */
@Slf4j
public class DriveLinkWorkflow {

    private final DrivePermissionService service;
    private final GoogleDriveConfig config;
    private final PendingRevocations pending;

    /** What {@link #unlink} did. */
    public enum Unlinked {
        NOT_LINKED,
        REVOKED,
        /** The email is forgotten, but Drive refused at least one revoke; it is queued to be retried. */
        REVOKE_QUEUED
    }

    public enum Status {
        LINKED,
        INVALID_EMAIL,
        DUPLICATE_EMAIL
    }

    public record LinkResult(Status status, DrivePermissionService.SyncOutcome outcome) {
    }

    public DriveLinkWorkflow(DrivePermissionService service, GoogleDriveConfig config, PendingRevocations pending) {
        this.service = service;
        this.config = config;
        this.pending = pending;
    }

    /**
     * Links (or relinks) a member's email and immediately syncs their grants.
     * A relink revokes all grants made under the previous address before the
     * new one is stored: the old email must lose access atomically with being
     * forgotten.
     */
    public LinkResult link(DiscordUser user, String rawEmail, Collection<String> roleIds, Collection<DiscordUser> allUsers) {
        String email = DrivePermissionService.normalizeEmail(rawEmail);
        if (!DrivePermissionService.isValidEmail(email)) {
            return new LinkResult(Status.INVALID_EMAIL, null);
        }

        String emailHash = service.hashEmail(email);
        boolean linkedElsewhere = allUsers.stream()
            .filter(other -> !other.getDiscordId().equals(user.getDiscordId()))
            .anyMatch(other -> other.getDriveAccess() != null && emailHash.equals(other.getDriveAccess().getEmailHash()));
        if (linkedElsewhere) {
            return new LinkResult(Status.DUPLICATE_EMAIL, null);
        }

        if (user.getDriveAccess() != null) {
            int existingGrantCount = user.getDriveAccess().getGrants().size();
            log.info("Member {} is relinking their Drive email; revoking {} existing grant(s) first", user.getDiscordId(), existingGrantCount);
            if (!service.revokeAll(user.getDiscordId(), user.getDriveAccess())) {
                queueRemaining(user);
                log.error("Partial revoke while relinking {}, continuing; the old address's remaining grants are queued to be retried", user.getDiscordId());
            }
        }

        user.setDriveAccess(new DriveAccess(service.encryptEmail(email), emailHash));
        DrivePermissionService.SyncOutcome outcome = service.syncGrants(user.getDiscordId(), user.getDriveAccess(), roleIds, config);
        log.info("Linked Drive access for {}: {} granted, {} failed", user.getDiscordId(),
            outcome.grantedFolders().size(), outcome.failedFolders().size());
        return new LinkResult(Status.LINKED, outcome);
    }

    /**
     * User-initiated unlink: revoke everything and erase the stored email.
     *
     */
    public Unlinked unlink(DiscordUser user) {
        if (user.getDriveAccess() == null) {
            return Unlinked.NOT_LINKED;
        }
        boolean revoked = revokeAndForget(user);
        log.info("Member {} unlinked their Drive email", user.getDiscordId());
        return revoked ? Unlinked.REVOKED : Unlinked.REVOKE_QUEUED;
    }

    /**
     * Leave/kick/ban path (spec §5): revoke with retries, then delete state
     * regardless. Grants that couldn't be revoked go to {@link PendingRevocations},
     * since the permission ids are about to be gone from the member's record.
     *
     * @return whether every grant was revoked now
     */
    public boolean revokeAndForget(DiscordUser user) {
        if (user.getDriveAccess() == null) {
            return true;
        }
        boolean revoked = service.revokeAll(user.getDiscordId(), user.getDriveAccess());
        if (!revoked) {
            queueRemaining(user);
            log.error("Could not revoke every Drive permission for member {}; the remaining grants are queued to be retried", user.getDiscordId());
        }
        user.setDriveAccess(null);
        log.info("Cleared Drive link state for {}", user.getDiscordId());
        return revoked;
    }

    /**
     * Records every grant the member still holds as one to revoke later, for when the revoke can't
     * even be attempted because the Drive integration is switched off or not configured.
     */
    public static void queueWithoutRevoking(DiscordUser user, PendingRevocations pending) {
        if (user.getDriveAccess() != null && !user.getDriveAccess().getGrants().isEmpty()) {
            pending.add(user.getDiscordId(), user.getDriveAccess().getGrants());
        }
    }

    // revokeAll leaves the grants it couldn't revoke on the access object
    private void queueRemaining(DiscordUser user) {
        pending.add(user.getDiscordId(), user.getDriveAccess().getGrants());
    }
}
