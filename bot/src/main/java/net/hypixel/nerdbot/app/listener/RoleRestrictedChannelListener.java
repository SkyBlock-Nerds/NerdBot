package net.hypixel.nerdbot.app.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

@Slf4j
public class RoleRestrictedChannelListener {

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        GuildChannel channel = event.getChannel().asGuildChannel();

        // Only handle text, voice, and forum channels
        if (!isTrackableChannelType(channel)) {
            return;
        }

        log.debug("Channel created: {} ({}), checking for role-restricted access", channel.getName(), channel.getId());
        updateChannelGroupMembership(channel, true);
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        GuildChannel channel = event.getChannel().asGuildChannel();

        // Only handle text, voice, and forum channels
        if (!isTrackableChannelType(channel)) {
            return;
        }

        log.debug("Channel deleted: {} ({}), removing from role-restricted groups", channel.getName(), channel.getId());
        updateChannelGroupMembership(channel, false);
    }

    @SubscribeEvent
    public void onPermissionOverrideCreate(PermissionOverrideCreateEvent event) {
        handlePermissionChange(event.getChannel());
    }

    @SubscribeEvent
    public void onPermissionOverrideUpdate(PermissionOverrideUpdateEvent event) {
        handlePermissionChange(event.getChannel());
    }

    @SubscribeEvent
    public void onPermissionOverrideDelete(PermissionOverrideDeleteEvent event) {
        handlePermissionChange(event.getChannel());
    }

    private void handlePermissionChange(GuildChannel channel) {
        if (!isTrackableChannelType(channel)) {
            return;
        }

        log.debug("Permission override changed for channel: {} ({}), updating role-restricted groups", channel.getName(), channel.getId());

        updateChannelGroupMembership(channel, false);
        updateChannelGroupMembership(channel, true);
    }

    private boolean isTrackableChannelType(GuildChannel channel) {
        return channel.getType() == ChannelType.TEXT ||
            channel.getType() == ChannelType.VOICE ||
            channel.getType() == ChannelType.FORUM;
    }

    /**
     * Update channel group membership based on role permissions
     *
     * @param channel  The channel to check
     * @param isAdding True if adding to groups, false if removing from groups
     */
    public void updateChannelGroupMembership(GuildChannel channel, boolean isAdding) {
        DiscordBotConfig botConfig = DiscordBotEnvironment.getBot().getConfig();
        net.dv8tion.jda.api.entities.Guild guild = channel.getGuild();

        if (!botConfig.getChannelConfig().isAutoManageRoleRestrictedChannels()) {
            log.debug("Auto-management is disabled, skipping channel group update");
            return;
        }

        boolean configChanged = false;

        if (isAdding) {
            Set<String> channelRoleIds = getChannelRestrictedRoles(channel);

            if (channelRoleIds.isEmpty()) {
                log.debug("Channel {} has no role restrictions, skipping", channel.getName());
                return;
            }

            RoleRestrictedChannelGroup targetGroup = findOrCreateRoleRestrictedGroup(botConfig, guild, channelRoleIds);
            if (!Arrays.asList(targetGroup.getChannelIds()).contains(channel.getId())) {
                String[] newChannelIds = Arrays.copyOf(targetGroup.getChannelIds(), targetGroup.getChannelIds().length + 1);
                newChannelIds[newChannelIds.length - 1] = channel.getId();
                targetGroup.setChannelIds(newChannelIds);
                configChanged = true;

                log.info("Added channel {} ({}) to role-restricted group '{}'", channel.getName(), channel.getId(), targetGroup.getDisplayName());
            }
        } else {
            List<RoleRestrictedChannelGroup> groups = new ArrayList<>(botConfig.getChannelConfig().getRoleRestrictedChannelGroups());

            for (RoleRestrictedChannelGroup group : groups) {
                List<String> channelList = Arrays.stream(group.getChannelIds())
                    .filter(id -> !id.equals(channel.getId()))
                    .toList();

                if (channelList.size() != group.getChannelIds().length) {
                    group.setChannelIds(channelList.toArray(new String[0]));
                    configChanged = true;

                    log.info("Removed channel {} ({}) from role-restricted group '{}'",
                        channel.getName(), channel.getId(), group.getDisplayName());
                }
            }

            // Remove empty groups
            if (removeEmptyRoleRestrictedGroups(botConfig)) {
                configChanged = true;
            }
        }

        if (configChanged) {
            log.info("Role-restricted channel configuration updated, writing to file");
            DiscordBotEnvironment.getBot().writeConfig(botConfig);
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
            return restrictedRoles;
        }

        // Check for roles that are specifically allowed VIEW_CHANNEL permission
        for (PermissionOverride override : channel.getPermissionContainer().getPermissionOverrides()) {
            if (override.isRoleOverride() && override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                restrictedRoles.add(override.getRole().getId());
            }
        }

        // Also check parent category permissions if channel inherits them
        if (channel instanceof StandardGuildChannel standardGuildChannel) {
            Category parent = standardGuildChannel.getParentCategory();
            if (parent != null) {
                PermissionOverride parentEveryoneOverride = parent.getPermissionOverride(parent.getGuild().getPublicRole());

                if (parentEveryoneOverride != null && parentEveryoneOverride.getDenied().contains(Permission.VIEW_CHANNEL)) {
                    for (PermissionOverride override : parent.getPermissionOverrides()) {
                        if (override.isRoleOverride() && override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                            restrictedRoles.add(override.getRole().getId());
                        }
                    }
                }
            }
        }

        return restrictedRoles;
    }

    private RoleRestrictedChannelGroup findOrCreateRoleRestrictedGroup(DiscordBotConfig botConfig, Guild guild, Set<String> roleIds) {
        List<RoleRestrictedChannelGroup> groups = new ArrayList<>(botConfig.getChannelConfig().getRoleRestrictedChannelGroups());

        for (RoleRestrictedChannelGroup group : groups) {
            Set<String> groupRoleIds = Set.of(group.getRequiredRoleIds());
            if (groupRoleIds.equals(roleIds)) {
                return group;
            }
        }

        String identifier = generateGroupIdentifier(guild, roleIds);
        String displayName = generateGroupDisplayName(guild, roleIds);

        RoleRestrictedChannelGroup newGroup = new RoleRestrictedChannelGroup(
            identifier,
            displayName,
            new String[0],
            roleIds.toArray(new String[0]),
            botConfig.getChannelConfig().getDefaultMinimumMessagesForActivity(),
            botConfig.getChannelConfig().getDefaultMinimumVotesForActivity(),
            botConfig.getChannelConfig().getDefaultMinimumCommentsForActivity(),
            botConfig.getChannelConfig().getDefaultActivityCheckDays()
        );

        groups.add(newGroup);
        botConfig.getChannelConfig().setRoleRestrictedChannelGroups(groups);

        return newGroup;
    }

    private boolean removeEmptyRoleRestrictedGroups(DiscordBotConfig botConfig) {
        List<RoleRestrictedChannelGroup> groups = new ArrayList<>(botConfig.getChannelConfig().getRoleRestrictedChannelGroups());
        int originalSize = groups.size();

        groups.removeIf(group -> group.getChannelIds().length == 0);

        if (groups.size() != originalSize) {
            botConfig.getChannelConfig().setRoleRestrictedChannelGroups(groups);
            return true;
        }

        return false;
    }

    private String generateGroupIdentifier(Guild guild, Set<String> roleIds) {
        List<String> roleNames = roleIds.stream()
            .map(guild::getRoleById)
            .filter(role -> role != null && role.getName() != null)
            .map(role -> role.getName().toLowerCase().replaceAll("[^a-z0-9]", ""))
            .sorted()
            .toList();

        if (roleNames.isEmpty()) {
            return String.join("-", roleIds) + "-channels";
        }

        return String.join("-", roleNames) + "-channels";
    }

    private String generateGroupDisplayName(Guild guild, Set<String> roleIds) {
        List<String> roleNames = roleIds.stream()
            .map(guild::getRoleById)
            .filter(role -> role != null && role.getName() != null)
            .map(net.dv8tion.jda.api.entities.Role::getName)
            .sorted()
            .toList();

        if (roleNames.isEmpty()) {
            return "Unknown Role Channels";
        }

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
