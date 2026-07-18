package net.hypixel.nerdbot.app.sentry;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import net.aerh.imagegenerator.exception.GeneratorException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which Sentry events the user-error filter drops: invalid generator input (directly or wrapped in
 * a cause chain) is discarded, while genuine application errors and untyped events pass through.
 */
class UserErrorSentryFilterTest {

    private final UserErrorSentryFilter filter = new UserErrorSentryFilter();

    @Test
    void dropsDirectGeneratorException() {
        SentryEvent event = new SentryEvent(new GeneratorException("Item with ID `GOLEM_SWORD` not found"));
        assertNull(filter.process(event, new Hint()));
    }

    @Test
    void dropsGeneratorExceptionWrappedInCauseChain() {
        SentryEvent event = new SentryEvent(new RuntimeException(new GeneratorException("bad slot")));
        assertNull(filter.process(event, new Hint()));
    }

    @Test
    void keepsInternalException() {
        SentryEvent event = new SentryEvent(new IOException("connection reset"));
        assertSame(event, filter.process(event, new Hint()));
    }

    @Test
    void keepsEventWithoutThrowable() {
        SentryEvent event = new SentryEvent();
        assertSame(event, filter.process(event, new Hint()));
    }
}
