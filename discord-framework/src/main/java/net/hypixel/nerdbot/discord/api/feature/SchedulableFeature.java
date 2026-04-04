package net.hypixel.nerdbot.discord.api.feature;

import net.hypixel.nerdbot.discord.config.NerdBotConfig;

/**
 * Implement on features that execute periodic tasks. The framework
 * will call executeTask() on a fixed schedule and handle error catching.
 * Implement defaultInitialDelayMs() and defaultPeriodMs() to control timing.
 */
public interface SchedulableFeature {

    /**
     * Executes the feature's periodic task. Any exception thrown here is
     * caught by the ScheduledTask framework and logged automatically.
     */
    void executeTask() throws Exception;

    /**
     * Default initial delay in milliseconds before the first run.
     */
    long defaultInitialDelayMs(NerdBotConfig config);

    /**
     * Default repeating period in milliseconds.
     */
    long defaultPeriodMs(NerdBotConfig config);
}
