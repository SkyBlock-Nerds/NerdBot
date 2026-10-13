package net.hypixel.nerdbot.app.feature;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.repository.Repository;

import java.time.Duration;
import java.util.Collection;

/**
 * Periodically flushes every repository cache to the database. Repositories are write-behind for a
 * lot of mutations (an in-memory {@code cacheObject} without an immediate save), and the only other
 * safety nets are cache-eviction on expiry and the graceful save in {@code onEnd}. An ungraceful
 * shutdown that bypasses {@code onEnd} (a SIGKILL, an OOM-kill or a crash) would otherwise lose
 * every write-behind change accumulated since startup; this bounds that loss to one interval.
 *
 * <p>Each run waits for its saves to finish, so a slow database can't leave one run still writing
 * older copies when the next starts.
 */
@Slf4j
public class RepositoryAutosaveFeature extends BotFeature {

    private static final Duration INTERVAL = Duration.ofMinutes(15);

    private final Database database;
    private final Provider<BotState> state;

    @Inject
    public RepositoryAutosaveFeature(Database database, Provider<BotState> state) {
        this.database = database;
        this.state = state;
    }

    @Override
    public void onFeatureStart() {
        scheduleAtFixedRate("RepositoryAutosaveFeature-task", this::flushRepositories, INTERVAL.toMillis(), INTERVAL.toMillis());
    }

    void flushRepositories() {
        if (state.get().isReadOnly() || !database.isConnected()) {
            log.debug("Not autosaving; the bot is in read-only mode or the database isn't connected");
            return;
        }

        Collection<Object> repositories = database.getRepositoryManager().getRepositories().values();
        log.debug("Autosaving {} repositories to the database", repositories.size());

        for (Object repositoryObject : repositories) {
            Repository<?> repository = (Repository<?>) repositoryObject;
            try {
                repository.saveAllToDatabase();
            } catch (RuntimeException exception) {
                log.error("Failed to autosave repository {}", repository.getClass().getSimpleName(), exception);
            }
        }
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
