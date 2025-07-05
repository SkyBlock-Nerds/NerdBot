package net.hypixel.nerdbot.bot.config.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.objects.CustomForumTag;
import net.hypixel.nerdbot.bot.config.objects.ReactionChannel;
import net.hypixel.nerdbot.bot.config.objects.RoleRestrictedChannelGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@Log4j2
public class ChannelConfig {

    /**
     * The {@link TextChannel} ID that the bot will be logging to
     */
    private String logChannelId = "";

    /**
     * Mojang profile verification requests will be logged to this {@link TextChannel} ID
     */
    private String verifyLogChannelId = "";

    /**
     * Public archive {@link Category} ID
     */
    private String publicArchiveCategoryId = "";

    /**
     * Nerd archive {@link Category} ID
     */
    private String nerdArchiveCategoryId = "";

    /**
     * Alpha archive {@link Category} ID
     */
    private String alphaArchiveCategoryId = "";

    /**
     * The {@link TextChannel} ID for an announcement channel, primarily used for things like fire sales or status updates
     */
    private String announcementChannelId = "";

    /**
     * The {@link TextChannel} ID for the bot-spam channel, used for miscellaneous bot output, mostly for debugging
     */
    private String botSpamChannelId = "";

    /**
     * The {@link TextChannel} ID for the poll channel
     */
    private String pollChannelId = "";

    /**
     * The {@link TextChannel} ID for the member voting channel
     */
    private String memberVotingChannelId = "";

    /**
     * The {@link TextChannel} ID for birthday notifications to be sent to
     */
    private String birthdayNotificationChannelId = "";

    /**
     * The {@link TextChannel} ID for the itemgen channel
     */
    private String[] genChannelIds = {};

    /**
     * A list of {@link TextChannel} IDs where the bot will allow April Fools generator changes
     */
    private String[] filteredAprilFoolsGenChannelIds = {};

    /**
     * Configuration for channels that will have reactions automatically added to all new messages
     */
    private List<ReactionChannel> reactionChannels = List.of();

    /**
     * The {@link TextChannel} IDs for the channels that the bot will ignore for activity tracking
     */
    private String[] blacklistedChannels = {};

    /**
     * The {@link CustomForumTag} list for custom forum tags
     * that may be used by specific users
     *
     * @see CustomForumTag#getOwnerId() for the owner of the tag
     */
    private List<CustomForumTag> customForumTags = List.of();

    /**
     * Configuration for role-restricted channel groups that require separate activity tracking
     * Each group represents channels that only specific roles can access
     */
    private List<RoleRestrictedChannelGroup> roleRestrictedChannelGroups = List.of();

    /**
     * Whether to automatically manage role-restricted channel groups
     * When enabled, channels will be automatically added/removed from groups based on their permissions
     */
    private boolean autoManageRoleRestrictedChannels = true;

    /**
     * Default activity requirements for automatically created role-restricted channel groups
     */
    private int defaultMinimumMessagesForActivity = 5;
    private int defaultMinimumVotesForActivity = 2;
    private int defaultMinimumCommentsForActivity = 1;
    private int defaultActivityCheckDays = 30;

    /**
     * Automatically pin the first message in threads.
     * <br><br>
     * Default is true
     */
    private boolean autoPinFirstMessage = true;

    /**
     * The {@link ForumChannel} IDs for the channels ignored by the autopinning feature.
     */
    private String[] autoPinBlacklistedChannels = {};

    /**
     * Find or create a role-restricted channel group for the given roles
     *
     * @param roleIds Set of role IDs that should have access to channels in this group
     *
     * @return The matching or newly created {@link RoleRestrictedChannelGroup}
     */
    public RoleRestrictedChannelGroup findOrCreateRoleRestrictedGroup(Set<String> roleIds) {
        if (!autoManageRoleRestrictedChannels) {
            return null;
        }

        List<RoleRestrictedChannelGroup> groups = new ArrayList<>(roleRestrictedChannelGroups);

        for (RoleRestrictedChannelGroup group : groups) {
            Set<String> groupRoleIds = Set.of(group.getRequiredRoleIds());
            if (groupRoleIds.equals(roleIds)) {
                return group;
            }
        }

        String identifier = generateGroupIdentifier(roleIds);
        String displayName = generateGroupDisplayName(roleIds);

        RoleRestrictedChannelGroup newGroup = new RoleRestrictedChannelGroup(
            identifier,
            displayName,
            new String[0], // Empty channel list initially
            roleIds.toArray(new String[0]), // Empty role list
            defaultMinimumMessagesForActivity,
            defaultMinimumVotesForActivity,
            defaultMinimumCommentsForActivity,
            defaultActivityCheckDays
        );

        groups.add(newGroup);
        this.roleRestrictedChannelGroups = groups;

        log.info("Created new role-restricted channel group '{}' for roles: {}",
            displayName, String.join(", ", roleIds));

        return newGroup;
    }

    /**
     * Remove empty role-restricted channel groups (groups with no channels)
     *
     * @return True if any groups were removed
     */
    public boolean removeEmptyRoleRestrictedGroups() {
        if (!autoManageRoleRestrictedChannels) {
            return false;
        }

        List<RoleRestrictedChannelGroup> groups = new ArrayList<>(roleRestrictedChannelGroups);
        int originalSize = groups.size();

        groups.removeIf(group -> {
            if (group.getChannelIds().length == 0) {
                log.info("Removing empty role-restricted channel group '{}'", group.getDisplayName());
                return true;
            }
            return false;
        });

        if (groups.size() != originalSize) {
            this.roleRestrictedChannelGroups = groups;
            return true;
        }

        return false;
    }

    /**
     * Update all role-restricted channel groups by scanning all channels in the guild
     * This can be used for initial setup or to fix inconsistencies
     */
    public void rebuildRoleRestrictedChannelGroups() {
        if (!autoManageRoleRestrictedChannels) {
            log.info("Auto-management is disabled, skipping rebuild of role-restricted channel groups");
            return;
        }

        log.info("Rebuilding role-restricted channel groups from scratch...");

        List<RoleRestrictedChannelGroup> newGroups = new ArrayList<>();
        Map<Set<String>, List<String>> roleToChannelsMap = new HashMap<>();

        if (NerdBotApp.getBot().getJDA().getGuilds().isEmpty()) {
            log.warn("No guilds found, cannot rebuild role-restricted channel groups");
            this.roleRestrictedChannelGroups = newGroups;
            return;
        }

        Guild guild = NerdBotApp.getBot().getJDA().getGuilds().get(0);
        int totalChannelsScanned = 0;
        int roleRestrictedChannelsFound = 0;

        for (TextChannel channel : guild.getTextChannels()) {
            totalChannelsScanned++;
            Set<String> restrictedRoles = getChannelRestrictedRoles(channel);

            if (!restrictedRoles.isEmpty()) {
                roleRestrictedChannelsFound++;
                roleToChannelsMap.computeIfAbsent(restrictedRoles, k -> new ArrayList<>()).add(channel.getId());
                log.debug("Found role-restricted text channel: {} with roles: {}", channel.getName(), restrictedRoles);
            }
        }

        for (VoiceChannel channel : guild.getVoiceChannels()) {
            totalChannelsScanned++;
            Set<String> restrictedRoles = getChannelRestrictedRoles(channel);

            if (!restrictedRoles.isEmpty()) {
                roleRestrictedChannelsFound++;
                roleToChannelsMap.computeIfAbsent(restrictedRoles, k -> new ArrayList<>()).add(channel.getId());
                log.debug("Found role-restricted voice channel: {} with roles: {}", channel.getName(), restrictedRoles);
            }
        }

        for (ForumChannel channel : guild.getForumChannels()) {
            totalChannelsScanned++;
            Set<String> restrictedRoles = getChannelRestrictedRoles(channel);

            if (!restrictedRoles.isEmpty()) {
                roleRestrictedChannelsFound++;
                roleToChannelsMap.computeIfAbsent(restrictedRoles, k -> new ArrayList<>()).add(channel.getId());
                log.debug("Found role-restricted forum channel: {} with roles: {}", channel.getName(), restrictedRoles);
            }
        }

        // Create new groups for each unique role combination
        for (Map.Entry<Set<String>, List<String>> entry : roleToChannelsMap.entrySet()) {
            Set<String> roleIds = entry.getKey();
            List<String> channelIds = entry.getValue();

            String identifier = generateGroupIdentifier(roleIds);
            String displayName = generateGroupDisplayName(roleIds);

            RoleRestrictedChannelGroup newGroup = new RoleRestrictedChannelGroup(
                identifier,
                displayName,
                channelIds.toArray(new String[0]),
                roleIds.toArray(new String[0]),
                defaultMinimumMessagesForActivity,
                defaultMinimumVotesForActivity,
                defaultMinimumCommentsForActivity,
                defaultActivityCheckDays
            );

            newGroups.add(newGroup);

            log.info("Created role-restricted channel group '{}' with {} channels for roles: {}",
                displayName, channelIds.size(),
                roleIds.stream()
                    .map(roleId -> {
                        Role role = guild.getRoleById(roleId);
                        return role != null ? role.getName() : roleId;
                    })
                    .collect(Collectors.joining(", ")));
        }

        this.roleRestrictedChannelGroups = newGroups;

        log.info("Role-restricted channel groups rebuild complete: "
                + "Scanned {} channels, found {} role-restricted channels, created {} groups",
            totalChannelsScanned, roleRestrictedChannelsFound, newGroups.size()
        );

        if (!newGroups.isEmpty()) {
            log.info("Created groups summary:");
            for (RoleRestrictedChannelGroup group : newGroups) {
                log.info("  - '{}' ({}) with {} channels",
                    group.getDisplayName(), group.getIdentifier(), group.getChannelIds().length);
            }
        } else {
            log.info("No role-restricted channels found - all channels appear to be publicly accessible");
        }
    }

    /**
     * Get the roles that have exclusive access to this channel
     *
     * @param channel The channel to analyze
     *
     * @return Set of role IDs that have VIEW_CHANNEL permission while @everyone is denied
     */
    private Set<String> getChannelRestrictedRoles(GuildChannel channel) {
        Set<String> restrictedRoles = new HashSet<>();

        // Check if @everyone is denied VIEW_CHANNEL permission
        PermissionOverride everyoneOverride = channel.getPermissionContainer().getPermissionOverride(channel.getGuild().getPublicRole());
        if (everyoneOverride == null || !everyoneOverride.getDenied().contains(Permission.VIEW_CHANNEL)) {
            // Channel is publicly accessible
            return restrictedRoles;
        }

        // Check for roles that are specifically allowed VIEW_CHANNEL permission
        for (PermissionOverride override : channel.getPermissionContainer().getPermissionOverrides()) {
            if (override.isRoleOverride() &&
                override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                restrictedRoles.add(override.getRole().getId());
            }
        }

        if (channel instanceof StandardGuildChannel standardGuildChannel) {
            Category parent = standardGuildChannel.getParentCategory();
            if (parent != null) {
                PermissionOverride parentEveryoneOverride = parent.getPermissionOverride(parent.getGuild().getPublicRole());

                if (parentEveryoneOverride != null && parentEveryoneOverride.getDenied().contains(Permission.VIEW_CHANNEL)) {
                    for (PermissionOverride override : parent.getPermissionOverrides()) {
                        if (override.isRoleOverride() &&
                            override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                            restrictedRoles.add(override.getRole().getId());
                        }
                    }
                }
            }
        }


        return restrictedRoles;
    }


    /**
     * Generate a unique identifier for a role combination
     */
    private String generateGroupIdentifier(Set<String> roleIds) {
        List<String> roleNames = roleIds.stream()
            .map(roleId -> {
                Role role = NerdBotApp.getBot().getJDA().getRoleById(roleId);
                return role != null ? role.getName().toLowerCase().replaceAll("[^a-z0-9]", "") : roleId;
            })
            .sorted()
            .toList();

        return String.join("-", roleNames) + "-channels";
    }

    /**
     * Generate a human-readable display name for a role combination
     */
    private String generateGroupDisplayName(Set<String> roleIds) {
        List<String> roleNames = roleIds.stream()
            .map(roleId -> {
                Role role = NerdBotApp.getBot().getJDA().getRoleById(roleId);
                return role != null ? role.getName() : "Unknown Role";
            })
            .sorted()
            .toList();

        if (roleNames.size() == 1) {
            return roleNames.get(0) + " Channels";
        } else if (roleNames.size() == 2) {
            return roleNames.get(0) + " & " + roleNames.get(1) + " Channels";
        } else {
            return String.join(", ", roleNames.subList(0, roleNames.size() - 1)) +
                " & " + roleNames.get(roleNames.size() - 1) + " Channels";
        }
    }
}