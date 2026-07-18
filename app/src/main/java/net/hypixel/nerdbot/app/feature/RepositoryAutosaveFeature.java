package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.storage.repository.Repository;

import java.time.Duration;
import java.util.Map;

/**
 * Periodically flushes every repository cache to the database. Repositories are write-behind for a
 * lot of mutations (an in-memory {@code cacheObject} without an immediate save), and the only other
 * safety nets are cache-eviction on expiry and the graceful save in {@code onEnd}. An ungraceful
 * shutdown that bypasses {@code onEnd} (a SIGKILL, an OOM-kill or a crash) would otherwise lose
 * every write-behind change accumulated since startup; this bounds that loss to one interval.
 *
 * <p>Registered unconditionally in code rather than through the feature config so the safety net
 * cannot be turned off by omitting a config entry.
 */
@Slf4j
public class RepositoryAutosaveFeature extends BotFeature {

    private static final long AUTOSAVE_INTERVAL_MS = Duration.ofMinutes(15).toMillis();

    @Override
    public void onFeatureStart() {
        scheduleAtFixedRate("RepositoryAutosaveFeature-task", this::flushRepositories, AUTOSAVE_INTERVAL_MS, AUTOSAVE_INTERVAL_MS);
    }

    private void flushRepositories() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.debug("Bot is in read-only mode, skipping repository autosave");
            return;
        }

        Map<Class<?>, Object> repositories = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepositories();
        log.debug("Autosaving {} repositories to the database", repositories.size());

        repositories.values().forEach(repositoryObject -> {
            Repository<?> repository = (Repository<?>) repositoryObject;
            repository.saveAllToDatabaseAsync()
                .exceptionally(throwable -> {
                    log.error("Failed to autosave repository {}", repository.getClass().getSimpleName(), throwable);
                    return null;
                });
        });
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
