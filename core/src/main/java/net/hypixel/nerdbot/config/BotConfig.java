package net.hypixel.nerdbot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.config.channel.ChannelConfig;
import net.hypixel.nerdbot.config.channel.ModMailConfig;
import net.hypixel.nerdbot.config.suggestion.SuggestionConfig;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class BotConfig {

    /**
     * Configuration for anything related to the Mod Mail feature
     */
    private ModMailConfig modMailConfig = new ModMailConfig();

    /**
     * Configuration for the suggestions channel
     */
    private SuggestionConfig suggestionConfig = new SuggestionConfig();

    /**
     * Configuration for alpha/project suggestion channels
     */
    private AlphaProjectConfig alphaProjectConfig = new AlphaProjectConfig();

    /**
     * Configuration for channels that the bot will be using
     */
    private ChannelConfig channelConfig = new ChannelConfig();

    /**
     * Configuration for roles that the bot will be using
     */
    private RoleConfig roleConfig = new RoleConfig();

    /**
     * Configuration for metrics that the bot will be grabbing
     */
    private MetricsConfig metricsConfig = new MetricsConfig();

    /**
     * Configuration for emoji IDs
     */
    private EmojiConfig emojiConfig = new EmojiConfig();

    /**
     * Configuration for badges
     */
    private BadgeConfig badgeConfig = new BadgeConfig();

    /**
     * Configuration for fun and miscellaneous things
     */
    private FunConfig funConfig = new FunConfig();

    /**
     * Configuration for status page monitoring
     */
    private StatusPageConfig statusPageConfig = new StatusPageConfig();

    /**
     * The Guild ID that the bot will be running in
     */
    private String guildId = "";

    /**
     * A list of owner IDs for the bot
     */
    private List<String> ownerIds = new ArrayList<>();

    /**
     * The limit of messages that the bot will curate in one go
     * Default value is 100 Message
     */
    private int messageLimit = 100;

    /**
     * The number of hours before re-caching all Minecraft Usernames
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
     * The activity type that the bot will display on its profile
     * Default is WATCHING
     */
    private ActivityType activityType = ActivityType.WATCHING;

    /**
     * The message being displayed as the bots activity on its profile
     * Default is "with a default message!"
     */
    private String activity = "with a default message!";

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

    public enum ActivityType {
        PLAYING,
        STREAMING,
        LISTENING,
        WATCHING,
        COMPETING,
        CUSTOM
    }
}