package net.hypixel.nerdbot.discord.api.feature;

import net.hypixel.nerdbot.discord.config.NerdBotConfig;

import java.util.TimerTask;

/**
 * Implement on features that execute periodic tasks. The framework
 * will call these methods to build the task and determine default
 * scheduling, and will apply any per-feature overrides from config.
 */
public interface SchedulableFeature {

    /**
     * Build the TimerTask that performs the feature's periodic task.
     */
    TimerTask buildTask();

    /**
     * Default initial delay in milliseconds before the first run.
     */
    long defaultInitialDelayMs(NerdBotConfig config);

    /**
     * Default repeating period in milliseconds.
     */
    long defaultPeriodMs(NerdBotConfig config);
}

