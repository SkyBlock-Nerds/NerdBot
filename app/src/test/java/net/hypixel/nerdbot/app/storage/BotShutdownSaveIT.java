package net.hypixel.nerdbot.app.storage;

import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import net.hypixel.nerdbot.discord.AbstractDiscordBot;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shutdown has to write whatever is still only in the repository caches. */
class BotShutdownSaveIT {

    private static final long LAST_ACTIVITY = 1_790_000_000_000L;

    private static final class FailingFeature extends BotFeature {
        @Override
        public void onFeatureStart() {
        }

        @Override
        public void onFeatureEnd() {
            throw new IllegalStateException("feature blew up while stopping");
        }
    }

    private static final class ShutdownBot extends AbstractDiscordBot {
        // Static because createDatabase() runs inside the super constructor, before instance fields are set
        private static String uri;
        private static String databaseName;

        @Override
        protected @NotNull Database createDatabase() {
            return new Database(uri, databaseName);
        }

        @Override
        protected @NotNull Collection<Object> getEventListeners() {
            return List.of();
        }

        @Override
        protected @NotNull Collection<? extends BotFeature> createFeatures() {
            return List.of(new FailingFeature());
        }
    }

    @Test
    void cachedChangesAreSavedEvenWhenAFeatureFailsToStop() throws Exception {
        String databaseName = MongoTestDatabase.freshDatabaseName();
        ShutdownBot.uri = MongoTestDatabase.connectionString();
        ShutdownBot.databaseName = databaseName;
        ShutdownBot bot = new ShutdownBot();
        bot.onStart();

        DiscordUserRepository repository = bot.getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findOrCreateById("100000000000000009");
        user.getLastActivity().setLastGlobalActivity(LAST_ACTIVITY);

        bot.onEnd();

        DiscordUserRepository fresh = new DiscordUserRepository(MongoTestDatabase.client(), databaseName);
        assertEquals(LAST_ACTIVITY, fresh.findById("100000000000000009").orElseThrow().getLastActivity().getLastGlobalActivity());
    }
}
