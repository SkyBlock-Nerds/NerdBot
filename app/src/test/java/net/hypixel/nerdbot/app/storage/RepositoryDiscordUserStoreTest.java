package net.hypixel.nerdbot.app.storage;

import com.mongodb.MongoException;
import net.hypixel.nerdbot.marmalade.exception.RepositoryException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryDiscordUserStoreTest {

    @Test
    void successIsPresent() {
        DiscordUser user = new DiscordUser("1");

        Optional<DiscordUser> result = RepositoryDiscordUserStore.toOptional("1", Result.success(user));

        assertSame(user, result.orElseThrow());
    }

    @Test
    void notFoundIsEmpty() {
        Result<DiscordUser, RepositoryException> notFound = Result.failure(new RepositoryException("Not found: 1"));

        assertTrue(RepositoryDiscordUserStore.toOptional("1", notFound).isEmpty());
    }

    @Test
    void databaseErrorThrowsInsteadOfLookingLikeNotFound() {
        RepositoryException error = new RepositoryException("Database error looking up ID: 1", new MongoException("down"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> RepositoryDiscordUserStore.toOptional("1", Result.failure(error)));

        assertEquals("Database error looking up 1", thrown.getMessage());
        assertSame(error, thrown.getCause());
    }
}
