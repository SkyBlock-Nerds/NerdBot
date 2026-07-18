package net.hypixel.nerdbot.app.storage;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Production {@link DiscordUserStore} backed by the real {@link DiscordUserRepository}.
 *
 * <p>This is a thin adapter: {@link #getAll()} and {@link #save(DiscordUser)} map directly onto the
 * repository's in-memory cache operations, matching the behaviour of the call sites this store
 * replaces.
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
        return repository.findById(discordId).toOptional();
    }

    @Override
    public void save(DiscordUser user) {
        repository.cacheObject(user);
    }
}
