package net.hypixel.nerdbot.app.storage;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.ChannelActivityEntry;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** User records against a real MongoDB, where Gson-to-BSON conversion can lose or mangle data. */
class DiscordUserRepositoryIT {

    private static final String USER_ID = "100000000000000001";
    private static final long BIG_TIMESTAMP = 1_790_000_000_000L;

    private MongoClient client;
    private String database;

    @BeforeEach
    void setUp() {
        client = MongoTestDatabase.client();
        database = MongoTestDatabase.freshDatabaseName();
    }

    private DiscordUserRepository repository() {
        return new DiscordUserRepository(client, database);
    }

    private static DiscordUser activeUser() {
        DiscordUser user = new DiscordUser(USER_ID);
        LastActivity activity = user.getLastActivity();
        activity.setLastGlobalActivity(BIG_TIMESTAMP);
        activity.getSuggestionCreationHistory().add(BIG_TIMESTAMP + 1);
        activity.getSuggestionVoteHistoryMap().put("200000000000000001", BIG_TIMESTAMP + 2);
        activity.getChannelActivityHistory().add(new ChannelActivityEntry("300000000000000001", "general", 3, BIG_TIMESTAMP + 3,
            new HashMap<>(Map.of("10-2026", 3))));
        activity.getRoleRestrictedChannelVoteHistory().put("alpha-testers", new HashMap<>(Map.of("200000000000000002", 5L)));
        activity.getNominationInfo().increaseInactivityWarnings();

        MojangProfile profile = new MojangProfile();
        profile.setUniqueId(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        profile.setUsername("Notch");
        user.setMojangProfile(profile);
        user.setRoleIds(List.of("400000000000000001", "400000000000000002"));
        return user;
    }

    @Test
    void aUserReadsBackWithEveryFieldIntact() throws Exception {
        repository().saveToDatabase(activeUser());

        DiscordUser reloaded = repository().findById(USER_ID).orElseThrow();
        LastActivity activity = reloaded.getLastActivity();

        assertEquals(BIG_TIMESTAMP, activity.getLastGlobalActivity());
        assertEquals(List.of(BIG_TIMESTAMP + 1), activity.getSuggestionCreationHistory());
        assertEquals(Map.of("200000000000000001", BIG_TIMESTAMP + 2), activity.getSuggestionVoteHistoryMap());
        ChannelActivityEntry channel = activity.getChannelActivityHistory().getFirst();
        assertEquals("general", channel.getLastKnownDisplayName());
        assertEquals(BIG_TIMESTAMP + 3, channel.getLastMessageTimestamp());
        assertEquals(Map.of("10-2026", 3), channel.getMonthlyMessageCount());
        assertEquals(Map.of("200000000000000002", 5L), activity.getRoleRestrictedChannelVoteHistory().get("alpha-testers"));
        assertEquals(1, activity.getNominationInfo().getTotalInactivityWarnings());
        assertTrue(activity.getNominationInfo().getLastInactivityWarningTimestamp().isPresent());
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), reloaded.getMojangProfile().getUniqueId());
        assertEquals("Notch", reloaded.getMojangProfile().getUsername());
        assertEquals(List.of("400000000000000001", "400000000000000002"), reloaded.getRoleIds());
    }

    @Test
    void changesMadeInTheCacheAreSavedBySaveAll() throws Exception {
        DiscordUserRepository repository = repository();
        repository.findOrCreateById(USER_ID).getLastActivity().setLastGlobalActivity(BIG_TIMESTAMP);

        repository.saveAllToDatabase();

        assertEquals(BIG_TIMESTAMP, repository().findById(USER_ID).orElseThrow().getLastActivity().getLastGlobalActivity());
    }

    @Test
    void savingTheSameUserTwiceUpdatesOneDocument() {
        DiscordUserRepository repository = repository();
        DiscordUser user = activeUser();
        repository.saveToDatabase(user);
        user.getLastActivity().setLastGlobalActivity(BIG_TIMESTAMP + 99);

        repository.saveToDatabase(user);

        assertEquals(1, client.getDatabase(database).getCollection("users").countDocuments());
    }

    @Test
    void aDeletedUserIsGoneFromTheDatabaseAndTheCache() {
        DiscordUserRepository repository = repository();
        repository.saveToDatabase(activeUser());
        repository.findById(USER_ID);

        repository.deleteFromDatabase(USER_ID);

        assertInstanceOf(Result.Failure.class, repository.findById(USER_ID));
        assertInstanceOf(Result.Failure.class, repository().findById(USER_ID));
    }

    @Test
    void loadingEverythingFillsTheCache() {
        repository().saveToDatabase(activeUser());
        repository().saveToDatabase(new DiscordUser("100000000000000002"));

        DiscordUserRepository fresh = repository();
        fresh.loadAllDocumentsIntoCache();

        assertEquals(2, fresh.getAll().size());
    }
}
