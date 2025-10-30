package net.hypixel.nerdbot.config.suggestion;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReviewRequestConfig {

    /**
     * Whether review requests are enabled
     */
    private boolean enabled = false;

    /**
     * The Discord channel ID for the suggestion review messages
     */
    private String channelId = "";

    /**
     * The amount of agrees required to request a review.
     * <br><br>
     * Default is 15
     * <br>
     * Set to -1 to disable
     */
    private int threshold = 15;

    /**
     * Should greenlit threshold be enforced for requested reviews.
     * <br><br>
     * Default is disabled
     */
    private boolean enforceGreenlitRatio = false;

    /**
     * The minimum required age of a suggestion to be eligible for a review
     * <br><br>
     * Default is 7 days
     * <br>
     * Set to 0 to disable
     */
    private long minimumSuggestionAge = 1_000L * 60L * 60L * 24L * 7L; // 7 days

}
