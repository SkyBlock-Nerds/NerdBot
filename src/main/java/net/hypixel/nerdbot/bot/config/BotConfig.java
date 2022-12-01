package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.Activity;

@Getter
@Setter
@ToString
public class BotConfig {

    /**
     * The guild ID that the bot will be running in
     */
    private String guildId;

    /**
     * The channel ID that the bot will be logging to
     */
    private String logChannel;

    /**
     * The channel ID for the suggestion forum
     */
    private String suggestionForumId;

    /**
     * The minimum threshold of reactions needed for a suggestion to be considered greenlit
     * Default value is 15 reactions
     */
    private int minimumThreshold = 15;

    /**
     * The limit of messages that the bot will curate in one go
     * Default value is 100 messages
     */
    private int messageLimit = 100;

    /**
     * The percentage of positive reactions needed for a suggestion to be considered greenlit
     * Default value is 75%
     */
    private double percentage = 75;

    /**
     * The interval between each curate cycle in milliseconds
     * Default value is 43200000 (12 hours)
     */
    private long interval = 43_200_000;

    /**
     * Emotes that the bot will use to react to suggestions
     */
    private Emojis emojis;

    /**
     * Forum tags for the bot to use, such as the greenlit tag to set suggestions as greenlit
     */
    private Tags tags;

    /**
     * The activity that the bot will display on its profile
     * Default is PLAYING
     */
    private Activity.ActivityType activityType = Activity.ActivityType.PLAYING;

    /**
     * The string being displayed as the bot's activity on its profile
     */
    private String activity;

    private ModMailConfig modMailConfig;
}
