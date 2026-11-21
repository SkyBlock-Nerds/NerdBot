package net.hypixel.nerdbot.app.drive;

import com.mongodb.client.MongoCollection;
import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pending revocations against a real MongoDB. */
class MongoPendingRevocationsIT {

    private MongoCollection<Document> collection;
    private MongoPendingRevocations pending;

    @BeforeEach
    void setUp() {
        collection = MongoTestDatabase.client().getDatabase(MongoTestDatabase.freshDatabaseName()).getCollection(MongoPendingRevocations.COLLECTION);
        pending = new MongoPendingRevocations(() -> collection);
    }

    @Test
    void nothingIsPendingAtFirst() {
        assertEquals(List.of(), pending.all());
    }

    @Test
    void everyGrantIsKeptUntilItIsRemoved() {
        pending.add("42", List.of(new DriveGrant("fA", "perm-1", "READER"), new DriveGrant("fB", "perm-2", "WRITER")));

        PendingRevocations.Entry first = new PendingRevocations.Entry("42", "fA", "perm-1");
        PendingRevocations.Entry second = new PendingRevocations.Entry("42", "fB", "perm-2");
        assertEquals(List.of(first, second), pending.all());

        pending.remove(first);

        assertEquals(List.of(second), pending.all());
    }

    @Test
    void theSamePermissionIsOnlyQueuedOnce() {
        pending.add("42", List.of(new DriveGrant("fA", "perm-1", "READER")));
        pending.add("42", List.of(new DriveGrant("fA", "perm-1", "READER")));

        assertEquals(1, collection.countDocuments());
    }
}
