package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.bot.config.objects.PingableRole;

@Getter
@Setter
@ToString
public class RoleConfig {

    /**
     * The {@link Role} ID of the Bot Manager role
     */
    private String botManagerRoleId = "";

    /**
     * The {@link Role} ID of the Moderator role
     */
    private String moderatorRoleId = "";

    /**
     * The {@link Role} ID of the Orange role
     */
    private String orangeRoleId = "";

    /**
     * The {@link Role} ID of the Member role
     */
    private String memberRoleId = "";

    /**
     * The {@link Role} ID of the New Member role
     */
    private String newMemberRoleId = "";

    /**
     * The {@link Role} ID of the Limbo role
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
     * The amount of days that the bot will pull voting history for a user
     * Default value is 90
     */
    private int daysRequiredForVoteHistory = 90;

    /**
     * A list of {@link PingableRole PingableRoles} used for announcements etc.
     */
    private PingableRole[] pingableRoles = {};
}
