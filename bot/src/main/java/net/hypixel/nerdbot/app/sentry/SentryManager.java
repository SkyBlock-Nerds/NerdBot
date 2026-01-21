package net.hypixel.nerdbot.app.sentry;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.discord.api.bot.Environment;

@Slf4j
public class SentryManager {

    private SentryManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Configures Sentry with the current environment.
     * Sentry is auto-initialized via sentry.properties, this just sets the environment tag.
     */
    public static void configureEnvironment() {
        if (!Sentry.isEnabled()) {
            log.info("Sentry is not enabled, skipping environment configuration");
            return;
        }

        String environment = Environment.getEnvironment().name().toLowerCase();
        Sentry.configureScope(scope -> scope.setTag("environment", environment));
        log.info("Sentry configured with environment: {}", environment);
    }

    /**
     * Closes Sentry on shutdown.
     */
    public static void close() {
        if (Sentry.isEnabled()) {
            Sentry.close();
            log.info("Sentry closed");
        }
    }
}