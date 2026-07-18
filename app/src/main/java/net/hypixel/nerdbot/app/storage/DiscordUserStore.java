package net.hypixel.nerdbot.app.storage;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.List;
import java.util.Optional;

/**
 * Narrow, injectable view over {@link DiscordUser} persistence.
 *
 * <p>Services depend on this interface instead of reaching through the global bot instance for the
 * concrete {@code DiscordUserRepository}. Receiving the store as an explicit collaborator lets the
 * logic that depends on user storage be unit-tested against an in-memory fake, with no live bot or
 * database. Production code is backed by {@link RepositoryDiscordUserStore}.
 */
public interface DiscordUserStore {

    /**
     * @return every known user. The returned list is a snapshot and safe to iterate while calling
     * {@link #save(DiscordUser)}.
     */
    List<DiscordUser> getAll();

    /**
     * @param discordId the user's Discord ID
     * @return the stored user with that ID, or {@link Optional#empty()} if none is stored
     */
    Optional<DiscordUser> findById(String discordId);

    /**
     * Upsert a user into the store. Mirrors the repository's cache-write semantics: the in-memory
     * copy is updated immediately and persisted through the underlying repository's normal write
     * path.
     *
     * @param user the user to store
     */
    void save(DiscordUser user);
}
