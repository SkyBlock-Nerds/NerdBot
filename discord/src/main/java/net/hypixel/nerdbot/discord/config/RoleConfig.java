package net.hypixel.nerdbot.discord.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.discord.config.objects.PingableRole;

@Getter
@Setter
@ToString
public class RoleConfig {

    /**
     * The role ID of the Bot Manager role
     */
    private String botManagerRoleId = "";

    /**
     * The role ID of the Moderator role
     */
    private String moderatorRoleId = "";

    /**
     * The role ID of the Orange role
     */
    private String orangeRoleId = "";

    /**
     * The role ID of the Member role
     */
    private String memberRoleId = "";

    /**
     * The role ID of the New Member role
     */
    private String newMemberRoleId = "";

    /**
     * The role ID of the Limbo role
     */
    private String limboRoleId = "";

    /**
     * If we are currently in the process of promoting users who have met the requirements
     * Default value is false
     */
    private boolean currentlyPromotingUsers = false;

    /**
     * The amount of votes a user must have to be considered active enough to be promoted to the next stage
     * Default value is 100
     */
    private int minimumVotesRequiredForPromotion = 100;

    /**
     * The amount of comments a user must have to be considered active enough to be promoted to the next stage
     * Default value is 100
     */
    private int minimumCommentsRequiredForPromotion = 100;

    /**
     * The amount of messages a user must have to be considered active enough to be promoted to the next stage
     * Default value is 10
     */
    private int minimumMessagesRequiredForPromotion = 10;

    /**
     * The amount of days that the bot will pull voting history for a user
     * Default value is 90
     */
    private int daysRequiredForVoteHistory = 90;

    /**
     * The amount of days of activity data that the bot will pull for inactivity checks
     * Default value is 30
     */
    private int daysRequiredForInactivityCheck = 30;

    /**
     * The amount of messages a user must have to be considered active during an inactivity check
     * Default value is 100
     */
    private int messagesRequiredForInactivityCheck = 100;

    /**
     * The amount of votes a user must have to be considered active during an inactivity check
     * Default value is 25
     */
    private int votesRequiredForInactivityCheck = 25;

    /**
     * The amount of comments a user must have to be considered active during an inactivity check
     * Default value is 100
     */
    private int commentsRequiredForInactivityCheck = 100;

    /**
     * A list of {@link PingableRole PingableRoles} used for announcements etc.
     */
    private PingableRole[] pingableRoles = {};

    /**
     * Ordered list of role IDs representing the promotion path.
     * Example: [newMemberRoleId, memberRoleId, orangeRoleId]
     * Used to derive the displayed nomination type (source -> target).
     */
    private String[] promotionTierRoleIds = {};

    /**
     * Minimum number of days a user must have been in the guild (based on join date)
     * before they can be nominated from New Member to Member when running the
     * “new members only” nomination check. Default: 30 days.
     */
    private int newMemberNominationMinDays = 30;

}
