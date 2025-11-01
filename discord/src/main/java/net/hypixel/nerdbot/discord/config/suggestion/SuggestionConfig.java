package net.hypixel.nerdbot.discord.config.suggestion;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SuggestionConfig {

    /**
     * Configuration for the review request feature
     */
    private ReviewRequestConfig reviewRequestConfig;

    // Channels

    /**
     * The text channel IDs for the suggestion forum
     */
    private String forumChannelId = "";

    // Forum Tags

    /**
     * The name of the greenlit forum tag in a forum channel
     */
    private String greenlitTag = "Greenlit";

    /**
     * The name of the reviewed forum tag in a forum channel
     */
    private String reviewedTag = "Reviewed";

    // Greenlit Settings

    /**
     * The minimum threshold of reactions needed for a suggestion to be considered greenlit.
     * <br><br>
     * Default value is 20 reactions
     */
    private int greenlitThreshold = 20;

    /**
     * The percentage of positive reactions needed for a suggestion to be considered greenlit.
     * <br><br>
     * Default value is 75%
     */
    private double greenlitRatio = 75;

    /**
     * Automatically archive posts when they get greenlit.
     * <br><br>
     * Default is false
     */
    private boolean archiveOnGreenlit = false;

    /**
     * Automatically archive posts when they get greenlit.
     * <br><br>
     * Default is false
     */
    private boolean lockOnGreenlit = false;

    // Features

    /**
     * The amount of hours with no activity to archive a thread.
     * <br><br>
     * Default is 7 days
     * <br>
     * Set to 0 to disable
     */
    private int autoArchiveThreshold = 24 * 7;

    /**
     * The amount of hours with no activity to lock a thread.
     * <br><br>
     * Default is disabled
     * <br>
     * Set to -1 to disable
     */
    private int autoLockThreshold = -1;

}