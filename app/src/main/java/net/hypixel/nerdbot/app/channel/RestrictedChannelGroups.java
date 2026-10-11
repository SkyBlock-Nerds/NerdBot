package net.hypixel.nerdbot.app.channel;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.hypixel.nerdbot.app.config.ConfigStore;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the role-restricted channel groups in the config matching who can see each channel. A
 * channel belongs to the group for exactly the set of roles allowed to view it, and that group is
 * created when it doesn't exist yet. Nothing changes while auto-management is switched off.
 */
@Slf4j
@Singleton
public class RestrictedChannelGroups {

    /** The groups before and after a rebuild. */
    public record Rebuild(int groupsBefore, int channelsBefore, List<RoleRestrictedChannelGroup> groupsAfter) {

        public int channelsAfter() {
            return countChannels(groupsAfter);
        }
    }

    private final Provider<NerdBotConfig> config;
    private final ConfigStore store;

    @Inject
    public RestrictedChannelGroups(Provider<NerdBotConfig> config, ConfigStore store) {
        this.config = config;
        this.store = store;
    }

    public static boolean isTracked(GuildChannel channel) {
        return channel.getType() == ChannelType.TEXT || channel.getType() == ChannelType.VOICE || channel.getType() == ChannelType.FORUM;
    }

    /**
     * Moves the channel into the group for the roles that can see it, or out of every group when
     * anyone can. Saves the config when that changed anything.
     *
     * @return whether the groups changed
     */
    public synchronized boolean refresh(GuildChannel channel) {
        NerdBotConfig current = config.get();
        if (!current.getChannelConfig().isAutoManageRoleRestrictedChannels() || !isTracked(channel)) {
            return false;
        }

        ChannelConfig channelConfig = current.getChannelConfig();
        boolean changed = place(channelConfig, channel) | channelConfig.removeEmptyRoleRestrictedGroups();
        if (changed) {
            save(current);
        }
        return changed;
    }

    /** Takes a deleted channel out of every group. */
    public synchronized boolean forget(GuildChannel channel) {
        NerdBotConfig current = config.get();
        if (!current.getChannelConfig().isAutoManageRoleRestrictedChannels()) {
            return false;
        }

        ChannelConfig channelConfig = current.getChannelConfig();
        boolean changed = removeEverywhere(channelConfig, Set.of(channel.getId())) | channelConfig.removeEmptyRoleRestrictedGroups();
        if (changed) {
            save(current);
        }
        return changed;
    }

    /**
     * Rechecks every tracked channel in the guild, drops channels that no longer exist and empty
     * groups, and saves once at the end.
     */
    public synchronized Rebuild rebuild(Guild guild) {
        NerdBotConfig current = config.get();
        ChannelConfig channelConfig = current.getChannelConfig();
        int groupsBefore = channelConfig.getRoleRestrictedChannelGroups().size();
        int channelsBefore = countChannels(channelConfig.getRoleRestrictedChannelGroups());

        channelConfig.rebuildRoleRestrictedChannelGroups();

        Set<String> existing = guild.getChannels().stream().map(GuildChannel::getId).collect(Collectors.toSet());
        Set<String> gone = channelConfig.getRoleRestrictedChannelGroups().stream()
            .flatMap(group -> Arrays.stream(group.getChannelIds()))
            .filter(channelId -> !existing.contains(channelId))
            .collect(Collectors.toSet());
        removeEverywhere(channelConfig, gone);

        guild.getChannels().stream().filter(RestrictedChannelGroups::isTracked).forEach(channel -> place(channelConfig, channel));
        // Only now, so a configured group that was empty keeps its settings when channels land in it
        channelConfig.removeEmptyRoleRestrictedGroups();

        save(current);
        return new Rebuild(groupsBefore, channelsBefore, List.copyOf(channelConfig.getRoleRestrictedChannelGroups()));
    }

    private boolean place(ChannelConfig channelConfig, GuildChannel channel) {
        Set<String> roleIds = rolesThatCanView(channel);
        RoleRestrictedChannelGroup target = roleIds.isEmpty() ? null : findGroup(channelConfig, roleIds);
        List<RoleRestrictedChannelGroup> containing = channelConfig.getRoleRestrictedChannelGroups().stream()
            .filter(group -> Arrays.asList(group.getChannelIds()).contains(channel.getId()))
            .toList();

        boolean alreadyPlaced = roleIds.isEmpty() ? containing.isEmpty() : target != null && containing.equals(List.of(target));
        if (alreadyPlaced) {
            return false;
        }

        removeEverywhere(channelConfig, Set.of(channel.getId()));
        if (roleIds.isEmpty()) {
            return true;
        }

        RoleRestrictedChannelGroup group = target != null ? target : createGroup(channelConfig, channel.getGuild(), roleIds);
        String[] channelIds = Arrays.copyOf(group.getChannelIds(), group.getChannelIds().length + 1);
        channelIds[channelIds.length - 1] = channel.getId();
        group.setChannelIds(channelIds);
        log.info("Added channel {} ({}) to role-restricted group '{}'", channel.getName(), channel.getId(), group.getDisplayName());
        return true;
    }

    /** Removes the channels from every group. Groups left empty stay until the caller drops them. */
    private static boolean removeEverywhere(ChannelConfig channelConfig, Set<String> channelIds) {
        boolean changed = false;
        for (RoleRestrictedChannelGroup group : channelConfig.getRoleRestrictedChannelGroups()) {
            String[] kept = Arrays.stream(group.getChannelIds()).filter(id -> !channelIds.contains(id)).toArray(String[]::new);
            if (kept.length != group.getChannelIds().length) {
                group.setChannelIds(kept);
                changed = true;
                log.info("Removed channel(s) {} from role-restricted group '{}'", channelIds, group.getDisplayName());
            }
        }

        return changed;
    }

    /**
     * The roles allowed to view the channel when @everyone can't, including those allowed on a
     * private parent category. Empty when the channel is public.
     */
    static Set<String> rolesThatCanView(GuildChannel channel) {
        if (!ChannelAccess.isHiddenFromEveryone(channel.getPermissionContainer())) {
            return Set.of();
        }

        Set<String> roleIds = new HashSet<>();
        ChannelAccess.rolesAllowedToView(channel.getPermissionContainer()).forEach(role -> roleIds.add(role.getId()));

        if (channel instanceof StandardGuildChannel standard) {
            Category parent = standard.getParentCategory();
            if (parent != null && ChannelAccess.isHiddenFromEveryone(parent)) {
                ChannelAccess.rolesAllowedToView(parent).forEach(role -> roleIds.add(role.getId()));
            }
        }

        return roleIds;
    }

    private static RoleRestrictedChannelGroup findGroup(ChannelConfig channelConfig, Set<String> roleIds) {
        return channelConfig.getRoleRestrictedChannelGroups().stream()
            .filter(group -> Set.of(group.getRequiredRoleIds()).equals(roleIds))
            .findFirst()
            .orElse(null);
    }

    private static RoleRestrictedChannelGroup createGroup(ChannelConfig channelConfig, Guild guild, Set<String> roleIds) {
        List<String> roleNames = roleIds.stream()
            .map(guild::getRoleById)
            .filter(Objects::nonNull)
            .map(Role::getName)
            .toList();

        RoleRestrictedChannelGroup group = new RoleRestrictedChannelGroup(
            groupIdentifier(roleNames, roleIds),
            groupDisplayName(roleNames),
            new String[0],
            roleIds.toArray(new String[0]),
            channelConfig.getDefaultMinimumMessagesForActivity(),
            channelConfig.getDefaultMinimumVotesForActivity(),
            channelConfig.getDefaultMinimumCommentsForActivity(),
            channelConfig.getDefaultActivityCheckDays(),
            true
        );

        List<RoleRestrictedChannelGroup> groups = new ArrayList<>(channelConfig.getRoleRestrictedChannelGroups());
        groups.add(group);
        channelConfig.setRoleRestrictedChannelGroups(groups);
        return group;
    }

    /**
     * Lowercased role names stripped to {@code [a-z0-9]}, sorted and joined with hyphens, e.g.
     * {@code admin-mod-channels}. The role ids stand in when no names resolve.
     */
    static String groupIdentifier(List<String> roleNames, Set<String> fallbackRoleIds) {
        List<String> sanitized = roleNames.stream()
            .map(name -> name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))
            .sorted()
            .toList();

        return String.join("-", sanitized.isEmpty() ? fallbackRoleIds : sanitized) + "-channels";
    }

    /** "A Channels", "A & B Channels" or "A, B & C Channels", with the names sorted. */
    static String groupDisplayName(List<String> roleNames) {
        List<String> sorted = roleNames.stream().sorted().toList();

        return switch (sorted.size()) {
            case 0 -> "Unknown Role Channels";
            case 1 -> sorted.getFirst() + " Channels";
            default -> String.join(", ", sorted.subList(0, sorted.size() - 1)) + " & " + sorted.getLast() + " Channels";
        };
    }

    /** Counts loosely, since groups read from an old config can be missing their channel list. */
    public static int countChannels(List<RoleRestrictedChannelGroup> groups) {
        return groups.stream().filter(Objects::nonNull).mapToInt(group -> group.getChannelIds() == null ? 0 : group.getChannelIds().length).sum();
    }

    private void save(NerdBotConfig current) {
        try {
            store.save(current);
        } catch (IOException exception) {
            throw new UncheckedIOException("Couldn't save the role-restricted channel groups", exception);
        }
    }
}
