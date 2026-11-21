package net.hypixel.nerdbot.app.drive;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** In-memory {@link PendingRevocations}. */
public class FakePendingRevocations implements PendingRevocations {

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void add(String memberId, Collection<DriveGrant> grants) {
        grants.forEach(grant -> entries.add(new Entry(memberId, grant.folderId(), grant.permissionId())));
    }

    @Override
    public List<Entry> all() {
        return List.copyOf(entries);
    }

    @Override
    public void remove(Entry entry) {
        entries.remove(entry);
    }
}
