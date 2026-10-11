package net.hypixel.nerdbot.app.storage;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Narrow, injectable view over {@link DiscordUser} persistence.
 *
 * <p>Services depend on this interface instead of reaching through the global bot instance for the
 * concrete {@code DiscordUserRepository}. Receiving the store as an explicit collaborator lets the
 * logic that depends on user storage be unit-tested against an in-memory fake, with no live bot or
 * database. Production code is backed by {@link RepositoryDiscordUserStore}.
 */
public interface DiscordUserStore {

    List<DiscordUser> getAll();

    Optional<DiscordUser> findById(String discordId);

    // Reads the database rather than the cache, which drops members after a day of inactivity
    List<DiscordUser> findWithBirthdaySet();

    // In-memory only; the periodic autosave writes it out later
    void cache(DiscordUser user);

    CompletableFuture<Void> save(DiscordUser user);
}
