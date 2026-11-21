package net.hypixel.nerdbot.app.drive;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;

import java.util.Collection;
import java.util.List;

/**
 * Drive permissions that should have been revoked but weren't, because Drive was failing or the
 * integration was switched off at the time. A member's record, which holds their permission ids,
 * is cleared or deleted when they unlink or leave, so without this list nothing would know the
 * permission still exists. {@link DriveSyncSweep} retries every entry each time it runs.
 */
public interface PendingRevocations {

    /** One permission still to revoke; {@code memberId} is only for the logs. */
    record Entry(String memberId, String folderId, String permissionId) {
    }

    void add(String memberId, Collection<DriveGrant> grants);

    List<Entry> all();

    void remove(Entry entry);
}
