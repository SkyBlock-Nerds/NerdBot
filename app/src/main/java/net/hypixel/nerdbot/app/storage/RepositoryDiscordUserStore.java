package net.hypixel.nerdbot.app.storage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Production {@link DiscordUserStore} backed by the real {@link DiscordUserRepository}.
 *
 * <p>{@link #cache(DiscordUser)} only touches the in-memory copy, {@link #save(DiscordUser)} writes to
 * Mongo straight away, and {@link #findIdsWithBirthdaySet()} queries Mongo rather than the cache.
 */
public class RepositoryDiscordUserStore implements DiscordUserStore {

    private final Provider<DiscordUserRepository> repository;

    /** Looks the repository up on each call, so the bot still starts without a database. */
    @Inject
    public RepositoryDiscordUserStore(Provider<DiscordUserRepository> repository) {
        this.repository = repository;
    }

    public RepositoryDiscordUserStore(DiscordUserRepository repository) {
        this(() -> repository);
    }

    @Override
    public List<DiscordUser> getAll() {
        return repository.get().getAll();
    }

    @Override
    public List<DiscordUser> getAllFromDatabase() {
        return repository.get().getAllDocuments();
    }

    @Override
    public Optional<DiscordUser> findById(String discordId) {
        return RepositoryLookups.toOptional(discordId, repository.get().findById(discordId));
    }

    @Override
    public List<String> findIdsWithBirthdaySet() {
        return repository.get().findIdsWithBirthdaySet();
    }

    @Override
    public void cache(DiscordUser user) {
        repository.get().cacheObject(user);
    }

    @Override
    public CompletableFuture<Void> save(DiscordUser user) {
        return repository.get().cacheAndSaveAsync(user).thenApply(result -> null);
    }

    @Override
    public void clearDriveAccess(DiscordUser user) {
        DiscordUserRepository users = repository.get();
        users.cacheObject(user);
        // A $set save can't remove a field, so the stored copy is cleared directly
        users.unsetField(user.getDiscordId(), "driveAccess");
    }
}
