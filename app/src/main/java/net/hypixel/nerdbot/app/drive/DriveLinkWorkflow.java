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

    public enum Status {
        LINKED,
        INVALID_EMAIL,
        DUPLICATE_EMAIL
    }

    public record LinkResult(Status status, DrivePermissionService.SyncOutcome outcome) {
    }

    public DriveLinkWorkflow(DrivePermissionService service, GoogleDriveConfig config) {
        this.service = service;
        this.config = config;
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
                log.error("Partial revoke while relinking {}, continuing; reconcile cannot heal the old address, check Drive manually", user.getDiscordId());
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
     * @return false when the member had nothing linked
     */
    public boolean unlink(DiscordUser user) {
        if (user.getDriveAccess() == null) {
            return false;
        }
        revokeAndForget(user);
        log.info("Member {} unlinked their Drive email", user.getDiscordId());
        return true;
    }

    /**
     * Leave/kick/ban path (spec §5): revoke with retries, then delete state
     * regardless; a failed revoke is logged loudly because nothing can heal it
     * once the permission ids are gone.
     */
    public void revokeAndForget(DiscordUser user) {
        if (user.getDriveAccess() == null) {
            return;
        }
        if (!service.revokeAll(user.getDiscordId(), user.getDriveAccess())) {
            log.error("Could not revoke every Drive permission for departing member {}; REMAINING GRANTS MUST BE REMOVED MANUALLY IN DRIVE", user.getDiscordId());
        }
        user.setDriveAccess(null);
        log.info("Cleared Drive link state for {}", user.getDiscordId());
    }
}
