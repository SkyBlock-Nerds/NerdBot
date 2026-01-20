package net.hypixel.nerdbot.discord.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * NerdBot-specific configuration extending the base Discord bot framework config.
 * Contains settings specific to the SkyBlock Nerds Discord bot implementation.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class NerdBotConfig extends DiscordBotConfig {

    /**
     * Configuration for metrics that the bot will be grabbing
     */
    private MetricsConfig metricsConfig = new MetricsConfig();

    /**
     * Configuration for status page monitoring
     */
    private StatusPageConfig statusPageConfig = new StatusPageConfig();

    /**
     * The limit of messages that the bot will curate in one go
     * Default value is 100 messages
     */
    private int messageLimit = 100;

    /**
     * The number of hours before re-caching all Minecraft usernames
     * Default value is 12 hours
     */
    private int mojangUsernameCacheTTL = 12;

    /**
     * Whether the bot should forcefully update people's nicknames to their Minecraft username
     */
    private boolean mojangForceNicknameUpdate = false;

    /**
     * How long someone must be in the same voice channel for it to count towards activity, in seconds.
     */
    private long voiceThreshold = 60;

    /**
     * The interval between each curate cycle in milliseconds
     * Default value is 43200000 (12 hours)
     */
    private long interval = 43_200_000;

    /**
     * Whether nominations to the next role are enabled
     * Default value is true
     */
    private boolean nominationsEnabled = true;

    /**
     * Whether the bot should run the inactivity checker
     * Default value is true
     */
    private boolean inactivityCheckEnabled = true;

    /**
     * The amount of days that a user must be inactive for to show up in the inactive user list
     * Default value is 7
     */
    private int inactivityDays = 7;

    /**
     * The amount of messages that a user must have sent in the last inactivityDays to be considered active
     * Default value is 10
     */
    private int inactivityMessages = 10;

    /**
     * Feature configuration. If present and not empty,
     * the bot will instantiate and start the listed features in order.
     */
    private List<FeatureConfig> features;

    /**
     * URL watcher configuration. If present and not empty,
     * the bot will instantiate and start the listed watchers.
     */
    private List<WatcherConfig> watchers;
}
