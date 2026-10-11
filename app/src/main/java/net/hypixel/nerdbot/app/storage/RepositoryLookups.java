package net.hypixel.nerdbot.app.storage;

import com.mongodb.MongoException;
import net.hypixel.nerdbot.marmalade.exception.RepositoryException;
import net.hypixel.nerdbot.marmalade.functional.Result;

import java.util.Optional;

public final class RepositoryLookups {

    private RepositoryLookups() {
    }

    /**
     * Turns a repository lookup into an Optional. A database error throws instead, so it can't look
     * like "not found" and lead callers to create or cache a blank record over the real one.
     */
    public static <T> Optional<T> toOptional(String id, Result<T, RepositoryException> result) {
        if (result instanceof Result.Failure<T, RepositoryException> failure
            && failure.error().getCause() instanceof MongoException) {
            throw new IllegalStateException("Database error looking up " + id, failure.error());
        }

        return result.toOptional();
    }
}
