package net.hypixel.nerdbot.app.storage;

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

    private final DiscordUserRepository repository;

    public RepositoryDiscordUserStore(DiscordUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DiscordUser> getAll() {
        return repository.getAll();
    }

    @Override
    public Optional<DiscordUser> findById(String discordId) {
        return RepositoryLookups.toOptional(discordId, repository.findById(discordId));
    }

    @Override
    public List<String> findIdsWithBirthdaySet() {
        return repository.findIdsWithBirthdaySet();
    }

    @Override
    public void cache(DiscordUser user) {
        repository.cacheObject(user);
    }

    @Override
    public CompletableFuture<Void> save(DiscordUser user) {
        return repository.cacheAndSaveAsync(user).thenApply(result -> null);
    }
}
