package net.hypixel.nerdbot.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
     */
    private int messageLimit;

    /**
     * The percentage of positive reactions needed for a suggestion to be considered greenlit
     * Default value is 75%
     */
    private double percentage = 75;

    /**
     * The interval between each curate cycle in milliseconds
     * Default value is 43200000 (12 hours)
     */
    private long interval;

    /**
     * Emotes that the bot will use to react to suggestions
     */
    private Emojis emojis;

    /**
     * Forum tags for the bot to use, such as the greenlit tag to set suggestions as greenlit
     */
    private Tags tags;
    
}
