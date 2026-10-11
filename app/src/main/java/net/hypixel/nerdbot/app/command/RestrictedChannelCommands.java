package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.channel.ChannelAccess;
import net.hypixel.nerdbot.app.channel.RestrictedChannelGroups;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.config.ConfigStore;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.storage.MissingUserRecordException;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;

import java.awt.Color;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** /channel-config, /scan-channel and the /debug views of the role-restricted channel groups. */
@Slf4j
public class RestrictedChannelCommands {

    static final String MANAGEMENT_DISABLED = "Automatic channel management is disabled. Enable it first with `/channel-config toggle`";
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int LISTED_CHANNELS = 10;
    private static final int LISTED_OTHER_CHANNELS = 5;

    private final Provider<NerdBotConfig> config;
    private final ConfigStore store;
    private final RestrictedChannelGroups groups;
    private final DiscordUserStore users;
    private final Executor backgroundWork;
    private final Clock clock;

    @Inject
    public RestrictedChannelCommands(Provider<NerdBotConfig> config, ConfigStore store, RestrictedChannelGroups groups, DiscordUserStore users,
                                     Executor backgroundWork, Clock clock) {
        this.config = config;
        this.store = store;
        this.groups = groups;
        this.users = users;
        this.backgroundWork = backgroundWork;
        this.clock = clock;
    }

    @SlashCommand(name = "channel-config", subcommand = "toggle", description = "Toggle automatic management of role-restricted channel groups", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void toggleAutoManageRoleRestricted(SlashCommandInteractionEvent event) {
        toggle(event);
    }

    void toggle(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        NerdBotConfig current = config.get();
        ChannelConfig channelConfig = current.getChannelConfig();
        boolean enabled = !channelConfig.isAutoManageRoleRestrictedChannels();

        channelConfig.setAutoManageRoleRestrictedChannels(enabled);
        try {
            store.save(current);
        } catch (IOException exception) {
            channelConfig.setAutoManageRoleRestrictedChannels(!enabled);
            CommandReplies.fail(hook, ConfigCommands.SAVE_FAILED_MESSAGE, exception);
            return;
        }

        log.info("{} {} automatic management of role-restricted channel groups", event.getUser().getName(), enabled ? "enabled" : "disabled");
        hook.editOriginal("✅ Automatic management of role-restricted channel groups has been **" + (enabled ? "enabled" : "disabled") + "**.").queue();
    }

    @SlashCommand(name = "channel-config", subcommand = "status", description = "View the status of automatic role-restricted channel management", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void autoManageStatus(SlashCommandInteractionEvent event) {
        status(event);
    }

    void status(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        ChannelConfig channelConfig = config.get().getChannelConfig();
        boolean enabled = channelConfig.isAutoManageRoleRestrictedChannels();

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Role-Restricted Channel Auto-Management Status")
            .setColor(enabled ? Color.GREEN : Color.RED)
            .addField("Automatic Management", enabled ? "✅ Enabled" : "❌ Disabled", false)
            .addField("Configured Groups", String.valueOf(channelConfig.getRoleRestrictedChannelGroups().size()), true);

        if (enabled) {
            embed.addField("Default Message Requirement", String.valueOf(channelConfig.getDefaultMinimumMessagesForActivity()), true)
                .addField("Default Vote Requirement", String.valueOf(channelConfig.getDefaultMinimumVotesForActivity()), true)
                .addField("Default Comment Requirement", String.valueOf(channelConfig.getDefaultMinimumCommentsForActivity()), true)
                .addField("Default Check Period", channelConfig.getDefaultActivityCheckDays() + " days", true)
                .addBlankField(true);
        }

        embed.addField("Total Tracked Channels", String.valueOf(RestrictedChannelGroups.countChannels(channelConfig.getRoleRestrictedChannelGroups())), false);
        hook.editOriginalEmbeds(embed.build()).queue();
    }

    @SlashCommand(name = "channel-config", subcommand = "rebuild", description = "Rebuild role-restricted channel groups from current permissions", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void rebuildRoleRestrictedGroups(SlashCommandInteractionEvent event) {
        rebuild(event);
    }

    CompletableFuture<Void> rebuild(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        if (!config.get().getChannelConfig().isAutoManageRoleRestrictedChannels()) {
            hook.editOriginal("❌ " + MANAGEMENT_DISABLED).queue();
            return CompletableFuture.completedFuture(null);
        }

        hook.editOriginal("🔄 Scanning all guild channels and rebuilding groups... This may take a moment.").queue();
        Guild guild = event.getGuild();

        return CommandReplies.finish(CompletableFuture.supplyAsync(() -> groups.rebuild(guild), backgroundWork), hook, "rebuild the role-restricted channel groups",
            "❌ **Rebuild Failed**\n\nAn error occurred during the rebuild process. Check the logs for details.",
            rebuild -> {
                log.info("Role-restricted channel groups rebuilt by {}: {} groups -> {} groups, {} channels -> {} channels", event.getUser().getName(),
                    rebuild.groupsBefore(), rebuild.groupsAfter().size(), rebuild.channelsBefore(), rebuild.channelsAfter());
                hook.editOriginal(rebuildSummary(rebuild)).queue();
            });
    }

    static String rebuildSummary(RestrictedChannelGroups.Rebuild rebuild) {
        StringBuilder summary = new StringBuilder("✅ **Rebuild Complete**\n\n")
            .append(String.format("**Before:** %d groups, %d channels\n", rebuild.groupsBefore(), rebuild.channelsBefore()))
            .append(String.format("**After:** %d groups, %d channels\n\n", rebuild.groupsAfter().size(), rebuild.channelsAfter()));

        if (rebuild.groupsAfter().isEmpty()) {
            return summary.append("**Result:** No role-restricted channels found - all channels appear to be publicly accessible.").toString();
        }

        summary.append("**Groups:**\n");
        List<RoleRestrictedChannelGroup> after = rebuild.groupsAfter();
        for (int i = 0; i < after.size(); i++) {
            String line = String.format("- **%s** (%d channels)\n", after.get(i).getDisplayName(), after.get(i).getChannelIds().length);
            String more = "... and " + (after.size() - i) + " more";
            if (summary.length() + line.length() + more.length() > MAX_MESSAGE_LENGTH) {
                return summary.append(more).toString();
            }
            summary.append(line);
        }
        return summary.toString();
    }

    @SlashCommand(name = "channel-config", subcommand = "clean", description = "Remove empty role-restricted channel groups", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void cleanRoleRestrictedGroups(SlashCommandInteractionEvent event) {
        clean(event);
    }

    void clean(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        NerdBotConfig current = config.get();
        ChannelConfig channelConfig = current.getChannelConfig();
        List<RoleRestrictedChannelGroup> before = channelConfig.getRoleRestrictedChannelGroups();

        if (!channelConfig.removeEmptyRoleRestrictedGroups()) {
            hook.editOriginal("✅ No empty role-restricted channel groups found.").queue();
            return;
        }

        try {
            store.save(current);
        } catch (IOException exception) {
            channelConfig.setRoleRestrictedChannelGroups(before);
            CommandReplies.fail(hook, ConfigCommands.SAVE_FAILED_MESSAGE, exception);
            return;
        }

        int removed = before.size() - channelConfig.getRoleRestrictedChannelGroups().size();
        log.info("{} cleaned role-restricted channel groups: removed {} empty groups", event.getUser().getName(), removed);
        hook.editOriginal(String.format("✅ Removed %d empty role-restricted channel group(s). %d groups remaining.",
            removed, channelConfig.getRoleRestrictedChannelGroups().size())).queue();
    }

    @SlashCommand(name = "scan-channel", description = "Manually scan a channel for role membership", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void scanChannelForRoleRestricted(SlashCommandInteractionEvent event, @SlashOption(description = "The channel to scan") GuildChannel channel) {
        scan(event, channel);
    }

    void scan(IReplyCallback event, GuildChannel channel) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        if (!config.get().getChannelConfig().isAutoManageRoleRestrictedChannels()) {
            hook.editOriginal(MANAGEMENT_DISABLED).queue();
            return;
        }
        if (!RestrictedChannelGroups.isTracked(channel)) {
            hook.editOriginal("Only text, voice and forum channels are put in role-restricted groups.").queue();
            return;
        }

        List<String> before = groupsContaining(channel);
        try {
            groups.refresh(channel);
        } catch (RuntimeException exception) {
            CommandReplies.fail(hook, ConfigCommands.SAVE_FAILED_MESSAGE, exception);
            return;
        }
        List<String> after = groupsContaining(channel);

        hook.editOriginalEmbeds(new EmbedBuilder()
            .setTitle("Channel Scan Results")
            .setDescription("Scanned " + channel.getAsMention() + " for role-restricted group membership")
            .setColor(Color.BLUE)
            .addField("Channel Type", channel.getType().name(), true)
            .addField("Before Scan", before.isEmpty() ? "No groups" : String.join(", ", before), false)
            .addField("After Scan", after.isEmpty() ? "No groups" : String.join(", ", after), false)
            .addField("Permission Analysis", ChannelAccess.summary(channel), false)
            .build()).queue();

        log.info("{} manually scanned channel {} ({})", event.getUser().getName(), channel.getName(), channel.getId());
    }

    private List<String> groupsContaining(GuildChannel channel) {
        return config.get().getChannelConfig().getRoleRestrictedChannelGroups().stream()
            .filter(group -> Arrays.asList(group.getChannelIds()).contains(channel.getId()))
            .map(RoleRestrictedChannelGroup::getDisplayName)
            .toList();
    }

    @SlashCommand(name = "debug", subcommand = "role-restricted-activity", description = "Debug role-restricted channel activity for a user", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void debugRoleRestrictedActivity(SlashCommandInteractionEvent event, @SlashOption(description = "The user to debug") Member member) {
        debugActivity(event, member);
    }

    void debugActivity(IReplyCallback event, Member member) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        List<RoleRestrictedChannelGroup> configured = config.get().getChannelConfig().getRoleRestrictedChannelGroups();

        if (configured.isEmpty()) {
            hook.editOriginal("No role-restricted channel groups configured.").queue();
            return;
        }

        DiscordUser user;
        try {
            user = users.require(member.getId());
        } catch (MissingUserRecordException exception) {
            CommandReplies.fail(hook, member.getAsMention() + " has no user record.", exception);
            return;
        }

        LastActivity activity = user.getLastActivity() == null ? new LastActivity() : user.getLastActivity();
        Set<String> memberRoleIds = new HashSet<>(member.getRoles().stream().map(Role::getId).toList());
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Role-Restricted Channel Activity Debug")
            .setDescription("Activity data for " + member.getAsMention())
            .setColor(Color.ORANGE)
            .setThumbnail(member.getEffectiveAvatarUrl());

        int shown = Math.min(configured.size(), MessageEmbed.MAX_FIELD_AMOUNT - 1);
        for (RoleRestrictedChannelGroup group : configured.subList(0, shown)) {
            embed.addField(group.getDisplayName(), groupActivity(group, activity, memberRoleIds, member.getGuild()), false);
        }
        if (shown < configured.size()) {
            embed.addField("…", (configured.size() - shown) + " more groups aren't shown", false);
        }

        hook.editOriginalEmbeds(embed.build()).queue();
    }

    static String groupActivity(RoleRestrictedChannelGroup group, LastActivity activity, Set<String> memberRoleIds, Guild guild) {
        boolean hasAccess = Arrays.stream(group.getRequiredRoleIds()).anyMatch(roleId -> memberRoleIds.stream().anyMatch(roleId::equalsIgnoreCase));
        StringBuilder value = new StringBuilder("**Has Access:** ").append(hasAccess ? "✅" : "❌").append("\n");

        if (!hasAccess) {
            List<String> roleNames = Arrays.stream(group.getRequiredRoleIds()).map(guild::getRoleById).filter(Objects::nonNull).map(Role::getName).toList();
            return value.append("**Required Roles:** ").append(String.join(" ", roleNames)).toString();
        }

        String id = group.getIdentifier();
        int days = group.getActivityCheckDays();
        int met = (activity.getRoleRestrictedChannelMessageCount(id, days) >= group.getMinimumMessagesForActivity() ? 1 : 0)
            + (activity.getRoleRestrictedChannelVoteCount(id, days) >= group.getMinimumVotesForActivity() ? 1 : 0)
            + (activity.getRoleRestrictedChannelCommentCount(id, days) >= group.getMinimumCommentsForActivity() ? 1 : 0);

        return value.append("**Last Activity:** ").append(activity.getRoleRestrictedChannelRelativeTimestamp(id)).append("\n")
            .append("**30-day Activity:**\n")
            .append(" - Messages: ").append(activity.getRoleRestrictedChannelMessageCount(id, 30)).append("/").append(group.getMinimumMessagesForActivity()).append("\n")
            .append(" - Votes: ").append(activity.getRoleRestrictedChannelVoteCount(id, 30)).append("/").append(group.getMinimumVotesForActivity()).append("\n")
            .append(" - Comments: ").append(activity.getRoleRestrictedChannelCommentCount(id, 30)).append("/").append(group.getMinimumCommentsForActivity()).append("\n")
            .append("**Requirements Met:** ").append(met).append("/3 ").append(met >= 2 ? "✅" : "⚠️")
            .toString();
    }

    @SlashCommand(name = "debug", subcommand = "ungrouped-channels", description = "Show channels that are not part of any role-restricted group", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void debugUngroupedChannels(SlashCommandInteractionEvent event) {
        listUngrouped(event);
    }

    void listUngrouped(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        List<RoleRestrictedChannelGroup> configured = config.get().getChannelConfig().getRoleRestrictedChannelGroups();
        Set<String> grouped = new HashSet<>();
        configured.forEach(group -> grouped.addAll(Arrays.asList(group.getChannelIds())));

        List<GuildChannel> ungrouped = event.getGuild().getChannels().stream().filter(channel -> !grouped.contains(channel.getId())).toList();
        List<GuildChannel> text = ofType(ungrouped, ChannelType.TEXT);
        List<GuildChannel> voice = ofType(ungrouped, ChannelType.VOICE);
        List<GuildChannel> forum = ofType(ungrouped, ChannelType.FORUM);
        List<GuildChannel> other = ungrouped.stream()
            .filter(channel -> channel.getType() != ChannelType.TEXT && channel.getType() != ChannelType.VOICE && channel.getType() != ChannelType.FORUM)
            .toList();

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("📋 Ungrouped Channels Analysis")
            .setColor(ungrouped.isEmpty() ? Color.GREEN : Color.YELLOW)
            .setDescription("Channels that are not part of any role-restricted channel group")
            .addField("📊 Summary", String.format("**Total Channels:** %d\n**Grouped:** %d\n**Ungrouped:** %d\n**Groups:** %d",
                event.getGuild().getChannels().size(), grouped.size(), ungrouped.size(), configured.size()), false);

        addChannelList(embed, "💬 Text Channels", text, LISTED_CHANNELS, channel -> channel.getAsMention() + " (" + ChannelAccess.describe(channel) + ")");
        addChannelList(embed, "🔊 Voice Channels", voice, LISTED_CHANNELS, channel -> channel.getName() + " (" + ChannelAccess.describe(channel) + ")");
        addChannelList(embed, "📋 Forum Channels", forum, LISTED_CHANNELS, channel -> channel.getAsMention() + " (" + ChannelAccess.describe(channel) + ")");
        addChannelList(embed, "🔧 Other Channels", other, LISTED_OTHER_CHANNELS, channel -> channel.getName() + " (" + channel.getType().name() + ")");

        hook.editOriginalEmbeds(embed.setTimestamp(clock.instant()).build()).queue();
    }

    private static List<GuildChannel> ofType(List<GuildChannel> channels, ChannelType type) {
        return channels.stream().filter(channel -> channel.getType() == type).toList();
    }

    static void addChannelList(EmbedBuilder embed, String title, List<GuildChannel> channels, int limit, Function<GuildChannel, String> describe) {
        if (channels.isEmpty()) {
            return;
        }

        StringBuilder value = new StringBuilder();
        channels.stream().limit(limit).forEach(channel -> value.append("- ").append(describe.apply(channel)).append('\n'));
        if (channels.size() > limit) {
            value.append("... and ").append(channels.size() - limit).append(" more");
        }
        embed.addField(title + " (" + channels.size() + ")", value.toString(), false);
    }
}
