package net.hypixel.nerdbot.bot.config.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a group of channels that are restricted to specific roles
 * and require separate activity tracking
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RoleRestrictedChannelGroup {

    /**
     * Unique identifier for this channel group (e.g., "exclusive-channels")
     */
    private String identifier = "";

    /**
     * Display name for this channel group
     */
    private String displayName = "";

    /**
     * Array of channel IDs that belong to this group
     */
    private String[] channelIds = {};

    /**
     * Array of role IDs that have access to these channels
     * Users must have at least one of these roles to be tracked
     */
    private String[] requiredRoleIds = {};

    /**
     * Minimum messages required in this channel group to avoid inactivity warning
     */
    private int minimumMessagesForActivity = 5;

    /**
     * Minimum votes required in this channel group to avoid inactivity warning
     */
    private int minimumVotesForActivity = 2;

    /**
     * Minimum comments required in this channel group to avoid inactivity warning
     */
    private int minimumCommentsForActivity = 1;

    /**
     * Number of days to check for activity in this channel group
     */
    private int activityCheckDays = 30;
}