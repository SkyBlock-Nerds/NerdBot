package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.app.role.SpecialRoles;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.discord.role.RoleManager;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.StringUtils;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.awt.*;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class NominationInactivityService {

    private static final NominationInactivityService INSTANCE = new NominationInactivityService();
    private static final int ROLE_RESTRICTED_INACTIVITY_THRESHOLD = 2;

    private NominationInactivityService() {
    }

    public static NominationInactivityService getInstance() {
        return INSTANCE;
    }

    public InactivitySweepReport runInactivitySweepForMembers() {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        int requiredVotes = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getVotesRequiredForInactivityCheck();
        int requiredComments = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getCommentsRequiredForInactivityCheck();
        int requiredMessages = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getMessagesRequiredForInactivityCheck();

        log.info("Checking for inactive users (required votes: {}, required comments: {}, required messages: {})", requiredVotes, requiredComments, requiredMessages);

        int scanned = 0;
        int missingMember = 0;
        int ineligible = 0;
        int warned = 0;
        int skippedAlreadyThisMonth = 0;
        long startNanos = System.nanoTime();

        for (DiscordUser discordUser : discordUserRepository.getAll()) {
            scanned++;

            Member member = guild.getMemberById(discordUser.getDiscordId());
            if (member == null) {
                missingMember++;
                log.warn("Member not found for user {}", discordUser.getDiscordId());
                continue;
            }

            Role highestRole = RoleManager.getHighestRole(member);
            if (highestRole == null) {
                ineligible++;
                log.info("Skipping inactivity check for {} (ID: {}) as they have no roles", member.getEffectiveName(), member.getId());
                continue;
            }

            if (Arrays.stream(SpecialRoles.NAMES).anyMatch(role -> highestRole.getName().equalsIgnoreCase(role))) {
                ineligible++;
                log.info("Skipping inactivity check for {} (ID: {}) as they have a special role: {} (ID: {})", member.getEffectiveName(), member.getId(), highestRole.getName(), highestRole.getId());
                continue;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            int days = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck();
            int totalMessages = lastActivity.getTotalMessageCount(days);
            int totalComments = lastActivity.getTotalComments(days);
            int totalVotes = lastActivity.getTotalVotes(days);

            boolean hasRequiredVotes = totalVotes >= requiredVotes;
            boolean hasRequiredComments = totalComments >= requiredComments;
            boolean hasRequiredMessages = totalMessages >= requiredMessages;
            final int requirementsMet = (hasRequiredMessages ? 1 : 0) + (hasRequiredComments ? 1 : 0) + (hasRequiredVotes ? 1 : 0);

            log.info("Checking if {} (ID: {}) should be flagged for inactivity (total messages: {}, total comments: {}, total votes: {}) (has min. comments: {}, has min. votes: {}, has min. messages: {}, requirements met: {}/3)", member.getEffectiveName(), member.getId(), totalMessages, totalComments, totalVotes, hasRequiredComments, hasRequiredVotes, hasRequiredMessages, requirementsMet);

            if (shouldSendInactivityWarning(requirementsMet, 2, lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().orElse(null), YearMonth.now(ZoneId.systemDefault()))) {
                sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, "Member");
            }

            Long lastWarningTimestamp = lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().orElse(null);
            if (lastWarningTimestamp != null) {
                YearMonth lastWarnMonth = YearMonth.from(Instant.ofEpochMilli(lastWarningTimestamp).atZone(ZoneId.systemDefault()));
                YearMonth nowMonth = YearMonth.now(ZoneId.systemDefault());

                if (lastWarnMonth.equals(nowMonth)) {
                    warned++;
                } else {
                    skippedAlreadyThisMonth++;
                }
            }
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("Inactive MEMBER sweep complete: scanned={}, warnedThisMonth={}, skippedAlreadyThisMonth={}, ineligible={}, missingMember={}, took={}ms", scanned, warned, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);

        return new InactivitySweepReport("Member", scanned, warned, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);
    }

    public InactivitySweepReport runInactivitySweepForNewMembers() {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        String newMemberRoleId = roleConfig.getNewMemberRoleId();
        int requiredVotes = roleConfig.getVotesRequiredForInactivityCheck();
        int requiredComments = roleConfig.getCommentsRequiredForInactivityCheck();
        int requiredMessages = roleConfig.getMessagesRequiredForInactivityCheck();

        log.info("Checking for inactive NEW MEMBERS (required votes: {}, required comments: {}, required messages: {})", requiredVotes, requiredComments, requiredMessages);

        int scanned = 0;
        int missingMember = 0;
        int ineligible = 0;
        int warned = 0;
        int skippedAlreadyThisMonth = 0;
        long startNanos = System.nanoTime();

        for (DiscordUser discordUser : discordUserRepository.getAll()) {
            scanned++;

            Member member = guild.getMemberById(discordUser.getDiscordId());
            if (member == null) {
                missingMember++;
                log.warn("Member not found for user {}", discordUser.getDiscordId());
                continue;
            }

            Role highestRole = RoleManager.getHighestRole(member);
            if (highestRole == null || !highestRole.getId().equalsIgnoreCase(newMemberRoleId)) {
                ineligible++;
                continue;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            int windowDays = roleConfig.getDaysRequiredForInactivityCheck();
            int totalMessages = lastActivity.getTotalMessageCount(windowDays);
            int totalComments = lastActivity.getTotalComments(windowDays);
            int totalVotes = lastActivity.getTotalVotes(windowDays);

            boolean hasRequiredVotes = totalVotes >= requiredVotes;
            boolean hasRequiredComments = totalComments >= requiredComments;
            boolean hasRequiredMessages = totalMessages >= requiredMessages;
            final int requirementsMet = (hasRequiredMessages ? 1 : 0) + (hasRequiredComments ? 1 : 0) + (hasRequiredVotes ? 1 : 0);

            log.info("[NewMember] Checking inactivity for {} (ID: {}) (messages: {}, comments: {}, votes: {}) (has min. comments: {}, has min. votes: {}, has min. messages: {}, requirements met: {}/3)", member.getEffectiveName(), member.getId(), totalMessages, totalComments, totalVotes, hasRequiredComments, hasRequiredVotes, hasRequiredMessages, requirementsMet);

            if (shouldSendInactivityWarning(requirementsMet, 3, lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().orElse(null), YearMonth.now(ZoneId.systemDefault()))) {
                sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, "New Member");
            }

            Long lastWarningTimestamp = lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().orElse(null);
            if (lastWarningTimestamp != null) {
                YearMonth lastWarnMonth = YearMonth.from(Instant.ofEpochMilli(lastWarningTimestamp).atZone(ZoneId.systemDefault()));
                YearMonth nowMonth = YearMonth.now(ZoneId.systemDefault());

                if (lastWarnMonth.equals(nowMonth)) {
                    warned++;
                } else {
                    skippedAlreadyThisMonth++;
                }
            }
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("Inactive NEW MEMBER sweep complete: scanned={}, warnedThisMonth={}, skippedAlreadyThisMonth={}, ineligible={}, missingMember={}, took={}ms", scanned, warned, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);

        return new InactivitySweepReport("NewMember", scanned, warned, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);
    }

    public void runRoleRestrictedInactivitySweep() {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        List<RoleRestrictedChannelGroup> channelGroups = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getRoleRestrictedChannelGroups();

        if (channelGroups.isEmpty()) {
            log.debug("No role-restricted channel groups configured, skipping role-restricted inactivity check");
            return;
        }

        String votingChannelId = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId();
        TextChannel votingChannel = ChannelCache.getTextChannelById(votingChannelId).orElse(null);
        if (votingChannel == null) {
            log.error("Cannot find the member voting channel (ID: {}) to send role-restricted inactivity warnings into, skipping the sweep", votingChannelId);
            return;
        }

        log.info("Checking for inactive users in {} role-restricted channel groups", channelGroups.size());

        for (RoleRestrictedChannelGroup group : channelGroups) {
            if (!group.isVotingNotificationsEnabled()) {
                log.info("Skipping inactivity warnings for role-restricted channel group '{}' as voting notifications are disabled", group.getIdentifier());
                continue;
            }

            log.info("Checking inactivity for role-restricted channel group '{}' (display name: '{}', required messages: {}, required votes: {}, required comments: {}, check days: {})",
                group.getIdentifier(), group.getDisplayName(), group.getMinimumMessagesForActivity(),
                group.getMinimumVotesForActivity(), group.getMinimumCommentsForActivity(), group.getActivityCheckDays()
            );

            int scanned = 0;
            int missingMember = 0;
            int ineligible = 0;
            int active = 0;
            int warned = 0;
            int skippedAlreadyThisMonth = 0;
            long startNanos = System.nanoTime();
            YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
            List<String> requiredRoleIds = Arrays.asList(group.getRequiredRoleIds());

            for (DiscordUser discordUser : discordUserRepository.getAll()) {
                scanned++;

                Member member = guild.getMemberById(discordUser.getDiscordId());
                if (member == null) {
                    missingMember++;
                    log.warn("Member not found for user {}", discordUser.getDiscordId());
                    continue;
                }

                boolean hasRequiredRole = member.getRoles().stream().anyMatch(role -> requiredRoleIds.contains(role.getId()));
                if (!hasRequiredRole) {
                    ineligible++;
                    continue;
                }

                LastActivity lastActivity = discordUser.getLastActivity();
                NominationInfo nominationInfo = lastActivity.getNominationInfo();
                int totalMessages = lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), group.getActivityCheckDays());
                int totalVotes = lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), group.getActivityCheckDays());
                int totalComments = lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), group.getActivityCheckDays());

                int requirementsMet = (totalMessages >= group.getMinimumMessagesForActivity() ? 1 : 0)
                    + (totalVotes >= group.getMinimumVotesForActivity() ? 1 : 0)
                    + (totalComments >= group.getMinimumCommentsForActivity() ? 1 : 0);

                Long lastGroupWarning = nominationInfo.getLastRoleRestrictedInactivityWarningTimestamp(group.getIdentifier()).orElse(null);
                RoleRestrictedOutcome outcome = classifyRoleRestrictedMember(requirementsMet, lastGroupWarning, currentMonth);
                if (outcome == RoleRestrictedOutcome.ACTIVE) {
                    active++;
                    continue;
                }
                if (outcome == RoleRestrictedOutcome.ALREADY_WARNED_THIS_MONTH) {
                    skippedAlreadyThisMonth++;
                    continue;
                }

                votingChannel.sendMessageEmbeds(buildRoleRestrictedWarningEmbed(member, group, lastActivity, requirementsMet, totalMessages, totalVotes, totalComments)).queue();
                nominationInfo.recordRoleRestrictedInactivityWarning(group.getIdentifier());
                warned++;
                log.info("Sent role-restricted inactivity warning for {} (ID: {}) in group '{}' (total warnings: {})",
                    member.getEffectiveName(), member.getId(), group.getIdentifier(), nominationInfo.getTotalRoleRestrictedInactivityWarnings());
            }

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("Role-restricted inactivity sweep complete for group '{}': scanned={}, warned={}, active={}, skippedAlreadyThisMonth={}, ineligible={}, missingMember={}, took={}ms",
                group.getIdentifier(), scanned, warned, active, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);
        }
    }

    enum RoleRestrictedOutcome {
        ACTIVE,
        ALREADY_WARNED_THIS_MONTH,
        WARN
    }

    /**
     * Same rule as the member sweep (fewer than 2 of the 3 requirements met is inactive), but the
     * once-a-month limit is tracked per channel group.
     */
    static RoleRestrictedOutcome classifyRoleRestrictedMember(int requirementsMet, Long lastGroupWarningTimestamp, YearMonth currentMonth) {
        if (requirementsMet >= ROLE_RESTRICTED_INACTIVITY_THRESHOLD) {
            return RoleRestrictedOutcome.ACTIVE;
        }

        return shouldSendInactivityWarning(requirementsMet, ROLE_RESTRICTED_INACTIVITY_THRESHOLD, lastGroupWarningTimestamp, currentMonth)
            ? RoleRestrictedOutcome.WARN
            : RoleRestrictedOutcome.ALREADY_WARNED_THIS_MONTH;
    }

    private MessageEmbed buildRoleRestrictedWarningEmbed(Member member, RoleRestrictedChannelGroup group, LastActivity lastActivity,
                                                         int requirementsMet, int totalMessages, int totalVotes, int totalComments) {
        String roleNames = Arrays.stream(group.getRequiredRoleIds())
            .map(roleId -> member.getGuild().getRoleById(roleId))
            .filter(Objects::nonNull)
            .map(Role::getName)
            .collect(Collectors.joining(", "));

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setColor(requirementsMet == 0 ? Color.RED : new Color(255, 165, 0))
            .setTitle(String.format("Role-Restricted Inactivity Warning - %s", group.getDisplayName()))
            .setDescription("**" + member.getAsMention() + "** has been flagged for inactivity in **" + group.getDisplayName() + "**")
            .setThumbnail(member.getEffectiveAvatarUrl())
            .addField("Channel Group",
                String.format("**Group:** %s\n**Required Roles:** %s",
                    group.getDisplayName(),
                    (!roleNames.isEmpty() ? roleNames : "Unknown")),
                false)
            .addField(String.format("Activity Summary (last %d days)", group.getActivityCheckDays()),
                String.format("**Requirements Met:** %d/3\n**Last Activity:** %s",
                    requirementsMet,
                    lastActivity.getRoleRestrictedChannelRelativeTimestamp(group.getIdentifier())),
                false)
            .addField("Messages", requirementLine(totalMessages, group.getMinimumMessagesForActivity()), true)
            .addField("Votes", requirementLine(totalVotes, group.getMinimumVotesForActivity()), true)
            .addField("Comments", requirementLine(totalComments, group.getMinimumCommentsForActivity()), true);

        List<String> openTicketLinks = findOpenTicketLinks(member.getId());
        if (!openTicketLinks.isEmpty()) {
            embedBuilder.addField("Open Tickets", String.join("\n", openTicketLinks), false);
        }

        return embedBuilder.build();
    }

    private static String requirementLine(int actual, int required) {
        return String.format("%s %s / %s required",
            actual >= required ? "✅" : "❌",
            StringUtils.formatWithCommas(actual),
            StringUtils.formatWithCommas(required));
    }

    /**
     * Decide whether an inactivity warning should be sent to a member this run.
     *
     * <p>A warning is sent when the member is below the activity threshold (fewer than
     * {@code inactivityThreshold} of the three requirements met) and has not already been warned in
     * the current calendar month. Extracted from the member and new-member sweeps - which previously
     * decided this via an {@code ifPresentOrElse} and then recomputed it to tally counters - so the
     * decision can be unit-tested in isolation.
     *
     * @param requirementsMet      the number of activity thresholds met (0-3)
     * @param inactivityThreshold  warn when fewer than this many requirements are met
     * @param lastWarningTimestamp epoch-millis of the last inactivity warning, or {@code null}
     * @param currentYearMonth     the year-month to treat as "now"
     * @return {@code true} if an inactivity warning should be sent
     */
    static boolean shouldSendInactivityWarning(int requirementsMet, int inactivityThreshold, Long lastWarningTimestamp, YearMonth currentYearMonth) {
        if (requirementsMet >= inactivityThreshold) {
            return false;
        }

        if (lastWarningTimestamp != null) {
            YearMonth lastWarningMonth = YearMonth.from(Instant.ofEpochMilli(lastWarningTimestamp).atZone(ZoneId.systemDefault()));
            return !lastWarningMonth.equals(currentYearMonth);
        }

        return true;
    }

    private void sendInactiveUserMessage(Member member, DiscordUser discordUser, int requiredMessages, int requiredVotes, int requiredComments, String inactivityType) {
        LastActivity lastActivity = discordUser.getLastActivity();
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        int totalMessages = lastActivity.getTotalMessageCount(roleConfig.getDaysRequiredForInactivityCheck());
        int totalVotes = lastActivity.getTotalVotes(roleConfig.getDaysRequiredForInactivityCheck());
        int totalComments = lastActivity.getTotalComments(roleConfig.getDaysRequiredForInactivityCheck());

        ChannelCache.getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            List<String> openTicketLinks = findOpenTicketLinks(member.getId());

            String messagesStatus = totalMessages >= requiredMessages ? "✅" : "❌";
            String votesStatus = totalVotes >= requiredVotes ? "✅" : "❌";
            String commentsStatus = totalComments >= requiredComments ? "✅" : "❌";

            int requirementsMet = (totalMessages >= requiredMessages ? 1 : 0)
                + (totalVotes >= requiredVotes ? 1 : 0)
                + (totalComments >= requiredComments ? 1 : 0);

            Color embedColor = requirementsMet == 0 ? Color.RED : Color.ORANGE;

            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(embedColor)
                .setTitle(String.format("%s Inactivity Warning", inactivityType))
                .setDescription(member.getAsMention() + " has been flagged for inactivity")
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField(String.format("Activity Summary (last %d days)", roleConfig.getDaysRequiredForInactivityCheck()),
                    String.format("**Requirements Met:** %d/3", requirementsMet),
                    false)
                .addField("Messages",
                    String.format("%s %s / %s required",
                        messagesStatus,
                        StringUtils.formatWithCommas(totalMessages),
                        StringUtils.formatWithCommas(requiredMessages)),
                    true)
                .addField("Votes",
                    String.format("%s %s / %s required",
                        votesStatus,
                        StringUtils.formatWithCommas(totalVotes),
                        StringUtils.formatWithCommas(requiredVotes)),
                    true)
                .addField("Comments",
                    String.format("%s %s / %s required",
                        commentsStatus,
                        StringUtils.formatWithCommas(totalComments),
                        StringUtils.formatWithCommas(requiredComments)),
                    true)
                .addField("Warning History",
                    String.format("**Total Warnings:** %s\n**Last Warning:** %s",
                        StringUtils.formatWithCommas(lastActivity.getNominationInfo().getTotalInactivityWarnings()),
                        lastActivity.getNominationInfo().getLastInactivityWarningDateString()),
                    false)
                .setTimestamp(Instant.now());

            if (!openTicketLinks.isEmpty()) {
                embedBuilder.addField("Open Tickets", String.join("\n", openTicketLinks), false);
            }

            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
            discordUser.getLastActivity().getNominationInfo().increaseInactivityWarnings();
            log.info("Sent inactivity warning message for {} (ID: {}) in voting channel (total warnings: {})",
                member.getEffectiveName(), member.getId(),
                discordUser.getLastActivity().getNominationInfo().getTotalInactivityWarnings());
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send inactivity warning message into!");
        });
    }

    /**
     * Queries the tickets collection directly via MongoDB to find open tickets for a user,
     * avoiding a compile-time dependency on the ticket model classes.
     *
     * @param userId the Discord user ID
     *
     * @return formatted ticket links (e.g. "#0001: <#channelId>"), or empty list
     */
    private List<String> findOpenTicketLinks(String userId) {
        try {
            var database = BotEnvironment.getBot().getDatabase();
            if (!database.isConnected() || database.getMongoClient() == null) {
                return List.of();
            }

            String dbName = database.getConnectionString().getDatabase();
            if (dbName == null) {
                return List.of();
            }

            MongoCollection<Document> ticketsCollection = database.getMongoClient()
                .getDatabase(dbName)
                .getCollection("tickets");

            Bson filter = Filters.and(
                Filters.eq("ownerId", userId),
                Filters.or(
                    Filters.exists("closedAt", false),
                    Filters.eq("closedAt", null),
                    Filters.lte("closedAt", 0L)
                )
            );

            List<String> links = new ArrayList<>();
            for (Document doc : ticketsCollection.find(filter).sort(Sorts.descending("createdAt"))) {
                Integer ticketNumber = doc.getInteger("ticketNumber");
                String channelId = doc.getString("channelId");
                if (ticketNumber != null && channelId != null) {
                    links.add(String.format("#%04d: <#%s>", ticketNumber, channelId));
                }
            }

            return links;
        } catch (Exception e) {
            log.warn("Failed to query open tickets for user {}", userId, e);
            return List.of();
        }
    }
}