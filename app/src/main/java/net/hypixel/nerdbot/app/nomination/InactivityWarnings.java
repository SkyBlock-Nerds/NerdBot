package net.hypixel.nerdbot.app.nomination;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.nomination.MemberSweep.Outcome;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;

import java.awt.Color;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Warns about inactive members in the voting channel, at most once a month each: members and new
 * members by their highest role, and restricted channel groups by their members' activity in the
 * group's channels. A warning is only recorded once Discord has accepted the message.
 */
@Slf4j
public class InactivityWarnings {

    // Warned when fewer than this many of the messages, votes and comments thresholds are met
    static final int MEMBER_REQUIREMENTS = 2;
    static final int NEW_MEMBER_REQUIREMENTS = 3;
    static final int RESTRICTED_GROUP_REQUIREMENTS = 2;

    private static final Color ORANGE = new Color(255, 165, 0);

    private final MemberSweep sweep;
    private final VotingChannel votingChannel;
    private final OpenTickets openTickets;
    private final DiscordUserStore users;
    private final Provider<NerdBotConfig> config;
    private final Clock clock;

    @Inject
    public InactivityWarnings(MemberSweep sweep, VotingChannel votingChannel, OpenTickets openTickets, DiscordUserStore users,
                              Provider<NerdBotConfig> config, Clock clock) {
        this.sweep = sweep;
        this.votingChannel = votingChannel;
        this.openTickets = openTickets;
        this.users = users;
        this.config = config;
        this.clock = clock;
    }

    public SweepReport checkMembers() {
        return checkRole("Member", config.get().getRoleConfig().getMemberRoleId(), MEMBER_REQUIREMENTS);
    }

    public SweepReport checkNewMembers() {
        return checkRole("New Member", config.get().getRoleConfig().getNewMemberRoleId(), NEW_MEMBER_REQUIREMENTS);
    }

    /** Members whose highest role is {@code roleId}, so each member is only checked by one of these sweeps. */
    private SweepReport checkRole(String label, String roleId, int requirementsNeeded) {
        votingChannel.requireAvailable();
        ActivityRequirements requirements = ActivityRequirements.forInactivity(config.get().getRoleConfig());
        YearMonth month = sweep.currentMonth();

        return sweep.run(label + " inactivity", (member, user) -> {
            if (!MemberSweep.hasHighestRole(member, roleId)) {
                return Outcome.INELIGIBLE;
            }

            NominationInfo warnings = user.getLastActivity().getNominationInfo();
            ActivityRequirements.Progress progress = requirements.measure(user.getLastActivity());
            if (progress.met() >= requirementsNeeded) {
                return Outcome.NOT_DUE;
            }
            if (MemberSweep.isIn(warnings.getLastInactivityWarningTimestamp(), month)) {
                return Outcome.ALREADY_THIS_MONTH;
            }

            EmbedBuilder embed = SweepEmbeds.addProgress(
                    warningEmbed(label + " Inactivity Warning", member.getAsMention() + " has been flagged for inactivity", member, progress), progress, null)
                .addField("Warning History", String.format("**Total Warnings:** %s\n**Last Warning:** %s",
                    StringUtils.formatWithCommas(warnings.getTotalInactivityWarnings()), warnings.getLastInactivityWarningDateString()), false);
            SweepEmbeds.addOpenTickets(embed, openTickets.linksFor(member.getId()));

            return send(member, user, embed.build(), recorded -> {
                recorded.setTotalInactivityWarnings(recorded.getTotalInactivityWarnings() + 1);
                recorded.setLastInactivityWarningTimestamp(clock.millis());
            });
        });
    }

    /** One report per group with warnings turned on. */
    public List<SweepReport> checkRestrictedGroups() {
        List<RoleRestrictedChannelGroup> groups = config.get().getChannelConfig().getRoleRestrictedChannelGroups();
        if (groups.isEmpty()) {
            return List.of();
        }

        votingChannel.requireAvailable();
        return groups.stream()
            .filter(group -> {
                if (!group.isVotingNotificationsEnabled()) {
                    log.info("Skipping inactivity warnings for restricted channel group '{}'; they're turned off", group.getIdentifier());
                }
                return group.isVotingNotificationsEnabled();
            })
            .map(this::checkGroup)
            .toList();
    }

    private SweepReport checkGroup(RoleRestrictedChannelGroup group) {
        ActivityRequirements requirements = ActivityRequirements.forGroup(group);
        Set<String> requiredRoleIds = Set.copyOf(Arrays.asList(Objects.requireNonNullElse(group.getRequiredRoleIds(), new String[0])));
        YearMonth month = sweep.currentMonth();

        return sweep.run(group.getDisplayName() + " inactivity", (member, user) -> {
            if (!MemberSweep.hasAnyRole(member, requiredRoleIds)) {
                return Outcome.INELIGIBLE;
            }

            LastActivity activity = user.getLastActivity();
            ActivityRequirements.Progress progress = requirements.measureInGroup(activity, group.getIdentifier());
            if (progress.met() >= RESTRICTED_GROUP_REQUIREMENTS) {
                return Outcome.NOT_DUE;
            }
            if (MemberSweep.isIn(activity.getNominationInfo().getLastRoleRestrictedInactivityWarningTimestamp(group.getIdentifier()), month)) {
                return Outcome.ALREADY_THIS_MONTH;
            }

            EmbedBuilder embed = warningEmbed("Role-Restricted Inactivity Warning - " + group.getDisplayName(),
                "**" + member.getAsMention() + "** has been flagged for inactivity in **" + group.getDisplayName() + "**", member, progress)
                .addField("Channel Group", String.format("**Group:** %s\n**Required Roles:** %s", group.getDisplayName(), roleNames(member, requiredRoleIds)), false);
            SweepEmbeds.addProgress(embed, progress, "**Last Activity:** " + activity.getRoleRestrictedChannelRelativeTimestamp(group.getIdentifier()));
            SweepEmbeds.addOpenTickets(embed, openTickets.linksFor(member.getId()));

            return send(member, user, embed.build(), recorded -> recordGroupWarning(recorded, group.getIdentifier()));
        });
    }

    private void recordGroupWarning(NominationInfo warnings, String groupIdentifier) {
        long now = clock.millis();
        warnings.setTotalRoleRestrictedInactivityWarnings(warnings.getTotalRoleRestrictedInactivityWarnings() + 1);
        warnings.setLastRoleRestrictedInactivityWarningTimestamp(now);
        if (warnings.getLastRoleRestrictedInactivityWarningTimestamps() == null) {
            warnings.setLastRoleRestrictedInactivityWarningTimestamps(new HashMap<>());
        }
        warnings.getLastRoleRestrictedInactivityWarningTimestamps().put(groupIdentifier, now);
    }

    private static String roleNames(Member member, Set<String> roleIds) {
        String names = roleIds.stream()
            .map(member.getGuild()::getRoleById)
            .filter(Objects::nonNull)
            .map(Role::getName)
            .sorted()
            .collect(Collectors.joining(", "));
        return names.isEmpty() ? "Unknown" : names;
    }

    private EmbedBuilder warningEmbed(String title, String description, Member member, ActivityRequirements.Progress progress) {
        return new EmbedBuilder()
            .setColor(progress.met() == 0 ? Color.RED : ORANGE)
            .setTitle(title)
            .setDescription(description)
            .setThumbnail(member.getEffectiveAvatarUrl())
            .setTimestamp(clock.instant());
    }

    private Outcome send(Member member, DiscordUser user, MessageEmbed embed, Consumer<NominationInfo> record) {
        try {
            votingChannel.send(embed).join();
        } catch (CompletionException exception) {
            log.error("Discord didn't accept the inactivity warning for {}; they'll be tried again next run", member.getId(), exception);
            return Outcome.FAILED;
        }

        record.accept(user.getLastActivity().getNominationInfo());
        try {
            users.save(user).join();
        } catch (CompletionException exception) {
            log.error("Warned {} about inactivity but couldn't save it; the autosave will retry", member.getId(), exception);
        }
        log.info("Sent an inactivity warning for {}: {}", member.getId(), embed.getTitle());
        return Outcome.ACTED;
    }
}
