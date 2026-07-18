package net.hypixel.nerdbot.app.sentry;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import net.aerh.imagegenerator.exception.GeneratorException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Drops Sentry events that were caused by invalid user input rather than a bug. The generator
 * throws {@link GeneratorException} to report things the user got wrong (an unknown item ID, a
 * malformed slot list, a missing resource pack); those messages are shown back to the user and are
 * not application errors, so they must not reach Sentry.
 *
 * <p>Filtering happens by runtime type, so exceptions co-caught alongside {@link GeneratorException}
 * (for example {@code IllegalArgumentException} or an NBT parse error) are still reported when they
 * are the actual failure.
 */
public class UserErrorSentryFilter implements EventProcessor {

    @Override
    @Nullable
    public SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
        return isUserError(event.getThrowable()) ? null : event;
    }

    /**
     * @param throwable the exception attached to a Sentry event, may be {@code null}
     *
     * @return {@code true} if the throwable, or any exception in its cause chain, represents invalid
     *         user input rather than an application error
     */
    static boolean isUserError(@Nullable Throwable throwable) {
        Set<Throwable> seen = new HashSet<>();

        for (Throwable current = throwable; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof GeneratorException) {
                return true;
            }
        }

        return false;
    }
}
