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

    // Every stored user, including ones the cache has dropped after a day of inactivity
    List<DiscordUser> getAllFromDatabase();

    Optional<DiscordUser> findById(String discordId);

    /**
     * Every member has a record, created when they join, so a missing one is a bug rather than
     * something to show the member.
     *
     * @throws IllegalStateException when there is no record
     */
    default DiscordUser require(String discordId) {
        return findById(discordId)
            .orElseThrow(() -> new IllegalStateException("Member " + discordId + " has no user record"));
    }

    // Reads the database rather than the cache, which drops members after a day of inactivity
    List<String> findIdsWithBirthdaySet();

    // In-memory only; the periodic autosave writes it out later
    void cache(DiscordUser user);

    CompletableFuture<Void> save(DiscordUser user);

    /** Caches the user, whose Drive access has already been cleared, and removes it from the stored copy. */
    void clearDriveAccess(DiscordUser user);
}
