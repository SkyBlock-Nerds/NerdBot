package net.hypixel.nerdbot.app.storage;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveAccess;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The user store the injected commands use, against a real MongoDB. */
class RepositoryDiscordUserStoreIT {

    private static final String MEMBER = "100000000000000001";

    private MongoClient client;
    private String database;
    private RepositoryDiscordUserStore store;

    @BeforeEach
    void setUp() {
        client = MongoTestDatabase.client();
        database = MongoTestDatabase.freshDatabaseName();
        store = storeWithEmptyCache();
    }

    private RepositoryDiscordUserStore storeWithEmptyCache() {
        DiscordUserRepository repository = new DiscordUserRepository(client, database);
        return new RepositoryDiscordUserStore(() -> repository);
    }

    @Test
    void clearingDriveAccessRemovesItFromTheStoredCopy() {
        DiscordUser user = new DiscordUser(MEMBER);
        user.setDriveAccess(new DriveAccess("encrypted", "hash"));
        store.save(user).join();
        assertNotNull(storeWithEmptyCache().require(MEMBER).getDriveAccess());

        user.setDriveAccess(null);
        store.clearDriveAccess(user);

        assertNull(storeWithEmptyCache().require(MEMBER).getDriveAccess());
        assertNull(store.require(MEMBER).getDriveAccess());
    }

    @Test
    void getAllFromDatabaseIncludesUsersTheCacheHasNotSeen() {
        store.save(new DiscordUser(MEMBER)).join();
        store.save(new DiscordUser("100000000000000002")).join();

        List<String> ids = storeWithEmptyCache().getAllFromDatabase().stream().map(DiscordUser::getDiscordId).sorted().toList();

        assertEquals(List.of(MEMBER, "100000000000000002"), ids);
    }

    @Test
    void deletingRemovesTheRecordFromTheDatabaseAndTheCache() {
        store.save(new DiscordUser(MEMBER)).join();
        store.save(new DiscordUser("100000000000000002")).join();

        store.delete(MEMBER).join();

        assertEquals(Optional.empty(), store.findById(MEMBER));
        assertEquals(Optional.empty(), storeWithEmptyCache().findById(MEMBER));
        assertEquals(List.of("100000000000000002"), storeWithEmptyCache().getAllFromDatabase().stream().map(DiscordUser::getDiscordId).toList());
    }

    @Test
    void requireFailsForAMemberWithoutARecord() {
        assertThrows(IllegalStateException.class, () -> store.require("100000000000000009"));
    }
}
