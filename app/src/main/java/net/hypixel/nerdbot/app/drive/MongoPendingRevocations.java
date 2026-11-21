package net.hypixel.nerdbot.app.drive;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import net.hypixel.nerdbot.app.inject.StorageModule;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * One document per permission in {@code drive_pending_revocations}:
 * {@code {_id: "<folderId>/<permissionId>", memberId, folderId, permissionId, since}}.
 */
public class MongoPendingRevocations implements PendingRevocations {

    static final String COLLECTION = "drive_pending_revocations";

    private final Supplier<MongoCollection<Document>> collection;

    @Inject
    public MongoPendingRevocations(Database database) {
        // Looked up on each use, so this can be built before the database is connected
        this(() -> {
            if (database.getMongoClient() == null) {
                throw new IllegalStateException(COLLECTION + " is unavailable; is the database connected?");
            }
            return database.getMongoClient().getDatabase(StorageModule.DATABASE_NAME).getCollection(COLLECTION);
        });
    }

    MongoPendingRevocations(Supplier<MongoCollection<Document>> collection) {
        this.collection = collection;
    }

    @Override
    public void add(String memberId, Collection<DriveGrant> grants) {
        for (DriveGrant grant : grants) {
            String id = id(grant.folderId(), grant.permissionId());
            Document document = new Document("_id", id)
                .append("memberId", memberId)
                .append("folderId", grant.folderId())
                .append("permissionId", grant.permissionId())
                .append("since", System.currentTimeMillis());
            collection().replaceOne(Filters.eq("_id", id), document, new ReplaceOptions().upsert(true));
        }
    }

    @Override
    public List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        for (Document document : collection().find()) {
            entries.add(new Entry(document.getString("memberId"), document.getString("folderId"), document.getString("permissionId")));
        }
        return entries;
    }

    @Override
    public void remove(Entry entry) {
        collection().deleteOne(Filters.eq("_id", id(entry.folderId(), entry.permissionId())));
    }

    private static String id(String folderId, String permissionId) {
        return folderId + "/" + permissionId;
    }

    private MongoCollection<Document> collection() {
        return collection.get();
    }
}
