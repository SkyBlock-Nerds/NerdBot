package net.hypixel.nerdbot.bot.config.forum;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;

@Getter
@Setter
@ToString
public class SuggestionConfig {

    // Channels

    /**
     * The {@link TextChannel} IDs for the suggestion forum
     */
    private String forumChannelId = "";

    /**
     * The {@link TextChannel} ID for the suggestion review messages
     */
    private String requestedReviewChannelId = "";

    // Forum Tags

    /**
     * The name of the greenlit {@link ForumTag tag} in a {@link ForumChannel forum channel}.
     */
    private String greenlitTag = "Greenlit";

    /**
     * The name of the reviewed {@link ForumTag tag} in a {@link ForumChannel forum channel}.
     */
    private String reviewedTag = "Reviewed";

    // Greenlit Settings

    /**
     * The minimum threshold of {@link MessageReaction reactions} needed for a suggestion to be considered greenlit.
     * <br><br>
     * Default value is 20 {@link MessageReaction reactions}
     */
    private int greenlitThreshold = 20;

    /**
     * The percentage of positive {@link MessageReaction reactions} needed for a suggestion to be considered greenlit.
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
     * Automatically pin the first message in threads.
     * <br><br>
     * Default is false
     */
    private boolean autoPinFirstMessage = false;

    /**
     * The amount of agrees required to request a review.
     * <br><br>
     * Default is 15
     * <br>
     * Set to -1 to disable
     */
    private int requestReviewThreshold = 15;

    /**
     * Should greenlit threshold be enforced for requested reviews.
     * <br><br>
     * Default is disabled
     */
    private boolean enforcingGreenlitRatioForRequestReview = false;

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

    // Review Settings

    /**
     * The minimum required age of a suggestion to be eligible for a review
     * <br><br>
     * Default is 7 days
     * <br>
     * Set to 0 to disable
     */
    private long minimumSuggestionRequestAge = 1_000L * 60L * 60L * 24L * 7L; // 7 days
}
