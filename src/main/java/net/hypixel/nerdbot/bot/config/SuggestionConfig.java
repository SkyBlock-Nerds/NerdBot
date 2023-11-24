package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.Objects;
import java.util.function.Function;

@Getter
@Setter
@ToString
public class SuggestionConfig {

    // Channels

    /**
     * The {@link TextChannel} IDs for the suggestion forums
     */
    private String[] suggestionForumIds = {};

    /**
     * The {@link TextChannel} IDs for the alpha suggestion forums
     */
    private String[] alphaSuggestionForumIds = {};

    /**
     * The {@link TextChannel} ID for the suggestion review messages
     */
    private String requestedReviewForumId = "";

    // Forum Tags

    /**
     * The ID of the flared {@link ForumTag tag} in an alpha {@link ForumChannel suggestion channel}.
     */
    private String flaredTag = "";

    /**
     * The ID of the greenlit {@link ForumTag tag} in a {@link ForumChannel forum channel}.
     */
    private String greenlitTag = "";

    /**
     * The ID of the reviewed {@link ForumTag tag} in a {@link ForumChannel forum channel}.
     */
    private String reviewedTag = "";

    // Emojis

    /**
     * The ID of the reaction for the agree emoji
     */
    private String agreeEmojiId = "";

    /**
     * The ID of the reaction for the disagree emoji
     */
    private String disagreeEmojiId = "";

    /**
     * The ID of the reaction for the neutral emoji
     */
    private String neutralEmojiId = "";

    /**
     * The ID of the reaction for the greenlit emoji
     */
    private String greenlitEmojiId = "";

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

    // Helper Methods

    public boolean isReactionEquals(MessageReaction reaction, Function<SuggestionConfig, String> function) {
        if (reaction.getEmoji().getType() != Emoji.Type.CUSTOM) {
            return false;
        }

        return Objects.equals(reaction.getEmoji().asCustom().getId(), function.apply(this));
    }

}
