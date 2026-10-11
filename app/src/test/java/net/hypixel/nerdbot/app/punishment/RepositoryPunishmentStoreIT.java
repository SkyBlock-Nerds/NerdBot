package net.hypixel.nerdbot.app.punishment;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.app.inject.StorageModule;
import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import net.hypixel.nerdbot.marmalade.storage.database.repository.PunishmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Punishments against a real MongoDB, through the same store the bot uses. */
class RepositoryPunishmentStoreIT {

    private static final String TARGET = "100000000000000001";
    private static final String MOD = "100000000000000002";

    private MongoClient client;
    private String database;
    private RepositoryPunishmentStore store;

    @BeforeEach
    void setUp() {
        client = MongoTestDatabase.client();
        database = MongoTestDatabase.freshDatabaseName();
        PunishmentRepository repository = new PunishmentRepository(client, database);
        store = new RepositoryPunishmentStore(() -> repository);
    }

    /** A second store with an empty cache, so reads have to come from the database. */
    private RepositoryPunishmentStore freshStore() {
        PunishmentRepository repository = new PunishmentRepository(client, database);
        return new RepositoryPunishmentStore(() -> repository);
    }

    @Test
    void savedPunishmentReadsBackFromTheDatabase() {
        Punishment punishment = new Punishment(TARGET, MOD, PunishmentType.DEMOTION, "abused perms", null);

        assertTrue(store.save(punishment).join());

        Punishment reloaded = freshStore().findById(punishment.getPunishmentId()).orElseThrow();
        assertEquals(TARGET, reloaded.getTargetUserId());
        assertEquals(MOD, reloaded.getModeratorUserId());
        assertEquals(PunishmentType.DEMOTION, reloaded.getType());
        assertEquals("abused perms", reloaded.getReason());
        assertNull(reloaded.getNotes());
        assertEquals(punishment.getCreatedAt(), reloaded.getCreatedAt());
    }

    @Test
    void unknownIdIsEmptyRatherThanAnError() {
        assertTrue(freshStore().findById("missing").isEmpty());
    }

    @Test
    void queriesFilterByTargetModeratorAndType() {
        Punishment ban = new Punishment(TARGET, MOD, PunishmentType.BAN, "a", null);
        Punishment warning = new Punishment(TARGET, MOD, PunishmentType.WARNING, "b", null);
        Punishment otherTarget = new Punishment("100000000000000003", MOD, PunishmentType.BAN, "c", null);
        Punishment otherMod = new Punishment(TARGET, "100000000000000004", PunishmentType.BAN, "d", null);
        for (Punishment punishment : List.of(ban, warning, otherTarget, otherMod)) {
            assertTrue(store.save(punishment).join());
        }

        RepositoryPunishmentStore fresh = freshStore();
        assertEquals(3, fresh.findByTargetUserId(TARGET).size());
        assertEquals(3, fresh.findByModeratorUserId(MOD).size());
        assertEquals(3, fresh.countByTargetUserId(TARGET));
        assertEquals(2, fresh.countByTargetAndType(TARGET, PunishmentType.BAN));
        assertEquals(0, fresh.countByTargetAndType(TARGET, PunishmentType.MUTE));
    }

    @Test
    void deleteRemovesItFromTheDatabaseAndTheCache() {
        Punishment punishment = new Punishment(TARGET, MOD, PunishmentType.KICK, "rude", "first time");
        assertTrue(store.save(punishment).join());

        assertTrue(store.delete(punishment.getPunishmentId()).join());

        assertTrue(store.findById(punishment.getPunishmentId()).isEmpty());
        assertTrue(freshStore().findById(punishment.getPunishmentId()).isEmpty());
    }

    @Test
    void deletingAnUnknownIdIsStillAcknowledged() {
        assertTrue(store.delete("missing").join());
    }

    @Test
    void connectedDatabaseRegistersTheRepositoryTheBotInjects() {
        Database connected = new Database(MongoTestDatabase.connectionString(), database);
        try {
            assertNotNull(StorageModule.repository(connected, PunishmentRepository.class));
        } finally {
            connected.disconnect();
        }
    }
}
