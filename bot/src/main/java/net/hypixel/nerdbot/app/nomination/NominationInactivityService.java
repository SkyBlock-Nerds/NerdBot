package net.hypixel.nerdbot.app.nomination;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.role.RoleManager;
import net.hypixel.nerdbot.app.ticket.TicketService;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.discord.util.Utils;

import java.awt.*;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class NominationInactivityService {

    private static final NominationInactivityService INSTANCE = new NominationInactivityService();

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
                log.error("Member not found for user {}", discordUser.getDiscordId());
                continue;
            }

            Role highestRole = RoleManager.getHighestRole(member);
            if (highestRole == null) {
                ineligible++;
                log.info("Skipping inactivity check for {} as they have no roles", member.getEffectiveName());
                continue;
            }

            if (Arrays.stream(Utils.SPECIAL_ROLES).anyMatch(role -> highestRole.getName().equalsIgnoreCase(role))) {
                ineligible++;
                log.info("Skipping inactivity check for {} as they have a special role: {}", member.getEffectiveName(), highestRole.getName());
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

            log.info("Checking if {} should be flagged for inactivity (total messages: {}, total comments: {}, total votes: {}) (has min. comments: {}, has min. votes: {}, has min. messages: {}, requirements met: {}/3)", member.getEffectiveName(), totalMessages, totalComments, totalVotes, hasRequiredComments, hasRequiredVotes, hasRequiredMessages, requirementsMet);

            lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().ifPresentOrElse(timestamp -> {
                Month lastInactivityWarningMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
                Month monthNow = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

                if (lastInactivityWarningMonth != monthNow && requirementsMet < 2) {
                    sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, "Member");
                }
            }, () -> {
                if (requirementsMet < 2) {
                    sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, "Member");
                }
            });

            Long lastWarningTimestamp = lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().orElse(null);
            if (lastWarningTimestamp != null) {
                Month lastWarnMonth = Instant.ofEpochMilli(lastWarningTimestamp).atZone(ZoneId.systemDefault()).getMonth();
                Month nowMonth = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

                if (lastWarnMonth == nowMonth) {
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
                log.error("Member not found for user {}", discordUser.getDiscordId());
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

            log.info("[NewMember] Checking inactivity for {} (messages: {}, comments: {}, votes: {}) (has min. comments: {}, has min. votes: {}, has min. messages: {}, requirements met: {}/3)", member.getEffectiveName(), totalMessages, totalComments, totalVotes, hasRequiredComments, hasRequiredVotes, hasRequiredMessages, requirementsMet);

            lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().ifPresentOrElse(timestamp -> {
                Month lastInactivityWarningMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
                Month monthNow = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

                if (lastInactivityWarningMonth != monthNow && requirementsMet < 3) {
                    sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, "New Member");
                }
            }, () -> {
                if (requirementsMet < 3) {
                    sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, "New Member");
                }
            });

            Long lastWarningTimestamp = lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().orElse(null);
            if (lastWarningTimestamp != null) {
                Month lastWarnMonth = Instant.ofEpochMilli(lastWarningTimestamp).atZone(ZoneId.systemDefault()).getMonth();
                Month nowMonth = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

                if (lastWarnMonth == nowMonth) {
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
            int warned = 0;
            int skippedAlreadyThisMonth = 0;
            long startNanos = System.nanoTime();

            for (DiscordUser discordUser : discordUserRepository.getAll()) {
                scanned++;

                Member member = guild.getMemberById(discordUser.getDiscordId());
                if (member == null) {
                    missingMember++;
                    log.error("Member not found for user {}", discordUser.getDiscordId());
                    continue;
                }

                // Must have one of the group's required roles
                boolean hasRequiredRole = member.getRoles().stream().anyMatch(role -> Arrays.asList(group.getRequiredRoleIds()).contains(role.getId()));
                if (!hasRequiredRole) {
                    ineligible++;
                    continue;
                }

                LastActivity lastActivity = discordUser.getLastActivity();
                int totalMessages = lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), group.getActivityCheckDays());
                int totalVotes = lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), group.getActivityCheckDays());
                int totalComments = lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), group.getActivityCheckDays());

                String messagesStatus = totalMessages >= group.getMinimumMessagesForActivity() ? "✅" : "❌";
                String votesStatus = totalVotes >= group.getMinimumVotesForActivity() ? "✅" : "❌";
                String commentsStatus = totalComments >= group.getMinimumCommentsForActivity() ? "✅" : "❌";

                int requirementsMet = (totalMessages >= group.getMinimumMessagesForActivity() ? 1 : 0)
                    + (totalVotes >= group.getMinimumVotesForActivity() ? 1 : 0)
                    + (totalComments >= group.getMinimumCommentsForActivity() ? 1 : 0);

                Color embedColor = requirementsMet == 0 ? Color.RED : new Color(255, 165, 0);

                StringBuilder roleNames = new StringBuilder();
                for (String roleId : group.getRequiredRoleIds()) {
                    Role role = member.getGuild().getRoleById(roleId);
                    if (role != null) {
                        if (!roleNames.isEmpty()) {
                            roleNames.append(", ");
                        }

                        roleNames.append(role.getName());
                    }
                }

                ChannelCache.getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
                    List<Ticket> openTickets = TicketService.getInstance().getTicketRepository().findOpenTicketsByUser(member.getId());

                    EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setColor(embedColor)
                        .setTitle(String.format("Role-Restricted Inactivity Warning - %s", group.getDisplayName()))
                        .setDescription("**" + member.getAsMention() + "** has been flagged for inactivity in **" + group.getDisplayName() + "**")
                        .setThumbnail(member.getEffectiveAvatarUrl())
                        .addField("Channel Group",
                            String.format("**Group:** %s\n**Required Roles:** %s",
                                group.getDisplayName(),
                                (!roleNames.isEmpty() ? roleNames.toString() : "Unknown")),
                            false)
                        .addField(String.format("Activity Summary (last %d days)", group.getActivityCheckDays()),
                            String.format("**Requirements Met:** %d/3\n**Last Activity:** %s",
                                requirementsMet,
                                lastActivity.getRoleRestrictedChannelRelativeTimestamp(group.getIdentifier())),
                            false)
                        .addField("Messages",
                            String.format("%s %s / %s required",
                                messagesStatus,
                                StringUtils.COMMA_SEPARATED_FORMAT.format(totalMessages),
                                StringUtils.COMMA_SEPARATED_FORMAT.format(group.getMinimumMessagesForActivity())),
                            true)
                        .addField("Votes",
                            String.format("%s %s / %s required",
                                votesStatus,
                                StringUtils.COMMA_SEPARATED_FORMAT.format(totalVotes),
                                StringUtils.COMMA_SEPARATED_FORMAT.format(group.getMinimumVotesForActivity())),
                            true)
                        .addField("Comments",
                            String.format("%s %s / %s required",
                                commentsStatus,
                                StringUtils.COMMA_SEPARATED_FORMAT.format(totalComments),
                                StringUtils.COMMA_SEPARATED_FORMAT.format(group.getMinimumCommentsForActivity())),
                            true);

                    if (!openTickets.isEmpty()) {
                        String ticketLinks = openTickets.stream()
                            .map(t -> t.getFormattedTicketId() + ": <#" + t.getThreadId() + ">")
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("");
                        embedBuilder.addField("Open Tickets", ticketLinks, false);
                    }

                    textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
                    lastActivity.getNominationInfo().increaseRoleRestrictedInactivityWarnings();
                    log.info("Sent role-restricted inactivity warning for {} in group '{}' (total warnings: {})",
                        member.getEffectiveName(), group.getIdentifier(),
                        lastActivity.getNominationInfo().getTotalRoleRestrictedInactivityWarnings());
                }, () -> {
                    throw new IllegalStateException("Cannot find voting channel to send role-restricted inactivity warning message into!");
                });
            }

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("Role-restricted inactivity sweep complete for group '{}': scanned={}, warnedThisMonth={}, skippedAlreadyThisMonth={}, ineligible={}, missingMember={}, took={}ms",
                group.getIdentifier(), scanned, warned, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);
        }
    }

private void sendInactiveUserMessage(Member member, DiscordUser discordUser, int requiredMessages, int requiredVotes, int requiredComments, String inactivityType) {
        LastActivity lastActivity = discordUser.getLastActivity();
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        int totalMessages = lastActivity.getTotalMessageCount(roleConfig.getDaysRequiredForInactivityCheck());
        int totalVotes = lastActivity.getTotalVotes(roleConfig.getDaysRequiredForInactivityCheck());
        int totalComments = lastActivity.getTotalComments(roleConfig.getDaysRequiredForInactivityCheck());

        ChannelCache.getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            List<Ticket> openTickets = TicketService.getInstance().getTicketRepository().findOpenTicketsByUser(member.getId());

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
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalMessages),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredMessages)),
                    true)
                .addField("Votes",
                    String.format("%s %s / %s required",
                        votesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalVotes),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredVotes)),
                    true)
                .addField("Comments",
                    String.format("%s %s / %s required",
                        commentsStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalComments),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredComments)),
                    true)
                .addField("Warning History",
                    String.format("**Total Warnings:** %s\n**Last Warning:** %s",
                        StringUtils.COMMA_SEPARATED_FORMAT.format(lastActivity.getNominationInfo().getTotalInactivityWarnings()),
                        lastActivity.getNominationInfo().getLastInactivityWarningDateString()),
                    false)
                .setTimestamp(Instant.now());

            if (!openTickets.isEmpty()) {
                String ticketLinks = openTickets.stream()
                    .map(t -> t.getFormattedTicketId() + ": <#" + t.getThreadId() + ">")
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
                embedBuilder.addField("Open Tickets", ticketLinks, false);
            }

            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
            discordUser.getLastActivity().getNominationInfo().increaseInactivityWarnings();
            log.info("Sent inactivity warning message for {} in voting channel (total warnings: {})",
                member.getEffectiveName(),
                discordUser.getLastActivity().getNominationInfo().getTotalInactivityWarnings());
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send inactivity warning message into!");
        });
    }
}
