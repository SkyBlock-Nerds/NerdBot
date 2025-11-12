package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.modmail.ModMailService;
import net.hypixel.nerdbot.app.role.RoleManager;
import net.hypixel.nerdbot.core.TimeUtils;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.discord.util.Utils;

import java.awt.Color;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TimerTask;
import java.util.function.BiPredicate;

@Slf4j
public class UserNominationFeature extends BotFeature {

    public static void nominateUsers() {
        String memberRoleId = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getMemberRoleId();

        BiPredicate<Member, DiscordUser> eligibility = (member, discordUser) -> {
            Role highestRole = RoleManager.getHighestRole(member);
            if (highestRole == null) {
                log.info("Skipping nomination for " + member.getEffectiveName() + " as they have no roles");
                return false;
            }

            if (!highestRole.getId().equalsIgnoreCase(memberRoleId)) {
                log.info("Skipping nomination for " + member.getEffectiveName() + " as their highest role is: " + highestRole.getName());
                return false;
            }

            return true;
        };

        runNominationSweep(eligibility, null);
    }

    /**
     * Runs the same promotion eligibility check as {@link #nominateUsers()}, but only for users whose
     * highest role is the configured "New Member" role. This is intended to be triggered manually
     * via an admin force command rather than on a schedule.
     */
    public static void nominateNewMembers() {
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        String newMemberRoleId = roleConfig.getNewMemberRoleId();
        int minDays = Math.max(0, roleConfig.getNewMemberNominationMinDays());

        BiPredicate<Member, DiscordUser> eligibility = (member, discordUser) -> {
            Role highestRole = RoleManager.getHighestRole(member);
            if (highestRole == null) {
                log.info("Skipping new member nomination for " + member.getEffectiveName() + " as they have no roles");
                return false;
            }

            if (!highestRole.getId().equalsIgnoreCase(newMemberRoleId)) {
                return false;
            }

            OffsetDateTime joinedAt = member.getTimeJoined();
            OffsetDateTime threshold = OffsetDateTime.now().minusDays(minDays);
            if (joinedAt.isAfter(threshold)) {
                log.info("Skipping new member nomination for " + member.getEffectiveName() + " as they joined on " + joinedAt + " (< " + minDays + " days)");
                return false;
            }

            return true;
        };

        runNominationSweep(eligibility, "NewMember");
    }

    private static void runNominationSweep(BiPredicate<Member, DiscordUser> eligibility, String contextLabel) {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        int requiredVotes = roleConfig.getMinimumVotesRequiredForPromotion();
        int requiredComments = roleConfig.getMinimumCommentsRequiredForPromotion();
        int daysWindow = roleConfig.getDaysRequiredForVoteHistory();

        long startNanos = System.nanoTime();

        if (contextLabel == null) {
            log.info("Checking for users to nominate for promotion (required votes: " + requiredVotes + ", required comments: " + requiredComments + ")");
        } else {
            log.info("Checking " + contextLabel + " candidates for promotion eligibility (required votes: " + requiredVotes + ", required comments: " + requiredComments + ")");
        }

        int scanned = 0;
        int missingMember = 0;
        int ineligible = 0;
        int eligible = 0;
        int nominated = 0;
        int skippedBelowThreshold = 0;
        int skippedAlreadyThisMonth = 0;

        for (DiscordUser discordUser : discordUserRepository.getAll()) {
            scanned++;
            Member member = guild.getMemberById(discordUser.getDiscordId());
            if (member == null) {
                missingMember++;
                log.error("Member not found for user " + discordUser.getDiscordId());
                continue;
            }

            if (!eligibility.test(member, discordUser)) {
                ineligible++;
                continue;
            }

            eligible++;
            NominationOutcome outcome = evaluateNomination(member, discordUser, contextLabel, requiredVotes, requiredComments, daysWindow);
            if (outcome == NominationOutcome.NOMINATED) {
                nominated++;
            } else if (outcome == NominationOutcome.SKIPPED_BELOW_THRESHOLD) {
                skippedBelowThreshold++;
            } else if (outcome == NominationOutcome.SKIPPED_ALREADY_THIS_MONTH) {
                skippedAlreadyThisMonth++;
            }
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        String label = (contextLabel == null ? "Member" : contextLabel);
        log.info("Nomination sweep complete for " + label + ": scanned=" + scanned
            + ", eligible=" + eligible
            + ", nominated=" + nominated
            + ", belowThreshold=" + skippedBelowThreshold
            + ", alreadyThisMonth=" + skippedAlreadyThisMonth
            + ", ineligible=" + ineligible
            + ", missingMember=" + missingMember
            + ", took=" + durationMs + "ms");
    }

    private enum NominationOutcome {
        NOMINATED,
        SKIPPED_ALREADY_THIS_MONTH,
        SKIPPED_BELOW_THRESHOLD
    }

    private static NominationOutcome evaluateNomination(Member member, DiscordUser discordUser, String contextLabel, int requiredVotes, int requiredComments, int daysWindow) {
        LastActivity lastActivity = discordUser.getLastActivity();
        int totalComments = lastActivity.getTotalComments(daysWindow);
        int totalVotes = lastActivity.getTotalVotes(daysWindow);

        boolean hasRequiredVotes = totalVotes >= requiredVotes;
        boolean hasRequiredComments = totalComments >= requiredComments;

        if (contextLabel == null) {
            log.info("Checking if " + member.getEffectiveName() + " should be nominated for promotion (total comments: " + totalComments + ", total votes: " + totalVotes + ", meets comments requirement: " + hasRequiredComments + ", meets votes requirement: " + hasRequiredVotes + ")");
        } else {
            log.info("[" + contextLabel + "] Checking if " + member.getEffectiveName() + " should be nominated (total comments: " + totalComments + ", total votes: " + totalVotes + ", meets comments requirement: " + hasRequiredComments + ", meets votes requirement: " + hasRequiredVotes + ")");
        }

        lastActivity.getNominationInfo().getLastNominationTimestamp().ifPresentOrElse(timestamp -> {
            Month lastNominationMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
            Month now = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

            if (lastNominationMonth != now && (totalComments >= requiredComments && totalVotes >= requiredVotes)) {
                if (contextLabel == null) {
                    log.info("Last nomination was not this month (last: " + lastNominationMonth + ", now: " + now + "), sending nomination message for " + member.getEffectiveName() + " (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
                } else {
                    log.info("[" + contextLabel + "] Last nomination not this month (last: " + lastNominationMonth + ", now: " + now + "), sending nomination message for " + member.getEffectiveName());
                }
                sendNominationMessage(member, discordUser);
            }
        }, () -> {
            if (contextLabel == null) {
                log.info("No last nomination date found for " + member.getEffectiveName() + ", checking if they meet the minimum requirements (min. votes: " + requiredVotes + ", min. comments: " + requiredComments + ", nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
            } else {
                log.info("[" + contextLabel + "] No last nomination date found for " + member.getEffectiveName() + ", checking minimum requirements");
            }
            if (totalComments >= requiredComments && totalVotes >= requiredVotes) {
                sendNominationMessage(member, discordUser);
            }
        });

        Long lastTimestamp = lastActivity.getNominationInfo().getLastNominationTimestamp().orElse(null);
        if (lastTimestamp != null) {
            Month lastMonth = Instant.ofEpochMilli(lastTimestamp).atZone(ZoneId.systemDefault()).getMonth();
            Month nowMonth = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

            if (lastMonth == nowMonth) {
                return NominationOutcome.SKIPPED_ALREADY_THIS_MONTH;
            }
        }

        if (hasRequiredComments && hasRequiredVotes) {
            return NominationOutcome.NOMINATED;
        }

        return NominationOutcome.SKIPPED_BELOW_THRESHOLD;
    }

    public static void findInactiveUsers() {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        int requiredVotes = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getVotesRequiredForInactivityCheck();
        int requiredComments = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getCommentsRequiredForInactivityCheck();
        int requiredMessages = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getMessagesRequiredForInactivityCheck();

        log.info("Checking for inactive users (required votes: " + requiredVotes + ", required comments: " + requiredComments + ", required messages: " + requiredMessages + ")");

        discordUserRepository.getAll().forEach(discordUser -> {
            Member member = guild.getMemberById(discordUser.getDiscordId());

            if (member == null) {
                log.error("Member not found for user " + discordUser.getDiscordId());
                return;
            }

            Role highestRole = RoleManager.getHighestRole(member);

            if (highestRole == null) {
                log.info("Skipping inactivity check for " + member.getEffectiveName() + " as they have no roles");
                return;
            }

            if (Arrays.stream(Utils.SPECIAL_ROLES).anyMatch(role -> highestRole.getName().equalsIgnoreCase(role))) {
                log.info("Skipping inactivity check for " + member.getEffectiveName() + " as they have a special role: " + highestRole.getName());
                return;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            int totalMessages = lastActivity.getTotalMessageCount(DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());
            int totalComments = lastActivity.getTotalComments(DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());
            int totalVotes = lastActivity.getTotalVotes(DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());

            boolean hasRequiredVotes = totalVotes >= requiredVotes;
            boolean hasRequiredComments = totalComments >= requiredComments;
            boolean hasRequiredMessages = totalMessages >= requiredMessages;
            final int requirementsMet = (hasRequiredMessages ? 1 : 0) + (hasRequiredComments ? 1 : 0) + (hasRequiredVotes ? 1 : 0);

            log.info("Checking if " + member.getEffectiveName() + " should be flagged for inactivity (total messages: " + totalMessages + ", total comments: " + totalComments + ", total votes: " + totalVotes + ") (has min. comments: " + hasRequiredComments + ", has min. votes: " + hasRequiredVotes + ", has min. messages: " + hasRequiredMessages + ", requirements met: " + requirementsMet + "/3)");

            lastActivity.getNominationInfo().getLastInactivityWarningTimestamp().ifPresentOrElse(timestamp -> {
                Month lastInactivityWarningMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
                Month monthNow = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

                if (lastInactivityWarningMonth != monthNow && requirementsMet < 2) {
                    log.debug("Last inactivity check was not this month (last: " + lastInactivityWarningMonth + ", now: " + monthNow + "), sending inactivity message for " + member.getEffectiveName() + " (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
                    sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments);
                }
            }, () -> {
                log.debug("No last inactivity warning date found for " + member.getEffectiveName() + ", checking if they meet the minimum requirements (min. votes: " + requiredVotes + ", min. comments: " + requiredComments + ", min. messages: " + requiredMessages + ", nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
                if (requirementsMet < 2) {
                    sendInactiveUserMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments);
                }
            });
        });
    }

    private static void sendNominationMessage(Member member, DiscordUser discordUser) {
        ChannelCache.getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            LastActivity lastActivity = discordUser.getLastActivity();
            RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();

            int totalMessages = lastActivity.getTotalMessageCount(roleConfig.getDaysRequiredForVoteHistory());
            int totalVotes = lastActivity.getTotalVotes(roleConfig.getDaysRequiredForVoteHistory());
            int totalComments = lastActivity.getTotalComments(roleConfig.getDaysRequiredForVoteHistory());

            int requiredVotes = roleConfig.getMinimumVotesRequiredForPromotion();
            int requiredComments = roleConfig.getMinimumCommentsRequiredForPromotion();

            String votesStatus = totalVotes >= requiredVotes ? "✅" : "⚠️";
            String commentsStatus = totalComments >= requiredComments ? "✅" : "⚠️";

            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("🌟 Promotion Nomination")
                .setDescription("**" + member.getEffectiveName() + "** is eligible for promotion!")
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField("📊 Activity Summary (last %d days)".formatted(roleConfig.getDaysRequiredForVoteHistory()),
                    "**All Requirements:** ✅ Met",
                    false)
                .addField("💬 Messages",
                    String.format("📈 **%s** tracked",
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalMessages)),
                    true)
                .addField("🗳️ Votes",
                    String.format("%s **%s** / %s required",
                        votesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalVotes),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredVotes)),
                    true)
                .addField("💭 Comments",
                    String.format("%s **%s** / %s required",
                        commentsStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalComments),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredComments)),
                    true)
                .addField("📈 Nomination History",
                    String.format("**Total Nominations:** %s\n**Last Nomination:** %s",
                        StringUtils.COMMA_SEPARATED_FORMAT.format(lastActivity.getNominationInfo().getTotalNominations()),
                        lastActivity.getNominationInfo().getLastNominationDateString()),
                    false)
                .setTimestamp(java.time.Instant.now());

            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
            discordUser.getLastActivity().getNominationInfo().increaseNominations();
            log.info("Sent promotion nomination message for " + member.getEffectiveName() + " in voting channel (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send nomination message into!");
        });
    }

    private static void sendInactiveUserMessage(Member member, DiscordUser discordUser, int requiredMessages, int requiredVotes, int requiredComments) {
        LastActivity lastActivity = discordUser.getLastActivity();
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        int totalMessages = lastActivity.getTotalMessageCount(roleConfig.getDaysRequiredForInactivityCheck());
        int totalVotes = lastActivity.getTotalVotes(roleConfig.getDaysRequiredForInactivityCheck());
        int totalComments = lastActivity.getTotalComments(roleConfig.getDaysRequiredForInactivityCheck());

        ChannelCache.getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            Optional<ThreadChannel> modMailThread = ModMailService.getInstance().findExistingThread(member.getUser());

            String messagesStatus = totalMessages >= requiredMessages ? "✅" : "⚠️";
            String votesStatus = totalVotes >= requiredVotes ? "✅" : "⚠️";
            String commentsStatus = totalComments >= requiredComments ? "✅" : "⚠️";

            int requirementsMet = (totalMessages >= requiredMessages ? 1 : 0) +
                (totalVotes >= requiredVotes ? 1 : 0) +
                (totalComments >= requiredComments ? 1 : 0);

            Color embedColor = requirementsMet == 0 ? Color.RED : Color.ORANGE;

            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(embedColor)
                .setTitle("⚠️ Inactivity Warning")
                .setDescription(member.getAsMention() + " has been flagged for inactivity")
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField("📊 Activity Summary (last %d days)".formatted(roleConfig.getDaysRequiredForInactivityCheck()),
                    String.format("**Requirements Met:** %d/3", requirementsMet),
                    false)
                .addField("💬 Messages",
                    String.format("%s **%s** / %s required",
                        messagesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalMessages),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredMessages)),
                    true)
                .addField("🗳️ Votes",
                    String.format("%s **%s** / %s required",
                        votesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalVotes),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredVotes)),
                    true)
                .addField("💭 Comments",
                    String.format("%s **%s** / %s required",
                        commentsStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalComments),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredComments)),
                    true)
                .addField("📈 Warning History",
                    String.format("**Total Warnings:** %s\n**Last Warning:** %s",
                        StringUtils.COMMA_SEPARATED_FORMAT.format(lastActivity.getNominationInfo().getTotalInactivityWarnings()),
                        lastActivity.getNominationInfo().getLastInactivityWarningDateString()),
                    false)
                .setTimestamp(Instant.now());

            modMailThread.ifPresent(threadChannel -> embedBuilder.addField("📧 Mod Mail",
                "Active thread: " + threadChannel.getAsMention(),
                false));

            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
            discordUser.getLastActivity().getNominationInfo().increaseInactivityWarnings();
            log.info("Sent inactivity warning message for " + member.getEffectiveName() + " in voting channel (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send inactivity warning message into!");
        });
    }

    /**
     * Check for inactive users in role-restricted channels
     */
    public static void findInactiveUsersInRoleRestrictedChannels() {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        List<RoleRestrictedChannelGroup> channelGroups = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getRoleRestrictedChannelGroups();

        if (channelGroups.isEmpty()) {
            log.debug("No role-restricted channel groups configured, skipping role-restricted inactivity check");
            return;
        }

        log.info("Checking for inactive users in {} role-restricted channel groups", channelGroups.size());

        for (RoleRestrictedChannelGroup group : channelGroups) {
            log.info("Checking inactivity for role-restricted channel group '{}' (display name: '{}', required messages: {}, required votes: {}, required comments: {}, check days: {})",
                group.getIdentifier(), group.getDisplayName(), group.getMinimumMessagesForActivity(),
                group.getMinimumVotesForActivity(), group.getMinimumCommentsForActivity(), group.getActivityCheckDays());

            discordUserRepository.getAll().forEach(discordUser -> {
                Member member = guild.getMemberById(discordUser.getDiscordId());

                if (member == null) {
                    log.error("Member not found for user " + discordUser.getDiscordId());
                    return;
                }

                boolean hasRequiredRole = Arrays.stream(group.getRequiredRoleIds())
                    .anyMatch(roleId -> member.getRoles().stream()
                        .map(Role::getId)
                        .anyMatch(memberRoleId -> memberRoleId.equalsIgnoreCase(roleId)));

                if (!hasRequiredRole) {
                    log.debug("Skipping role-restricted inactivity check for {} in group '{}' as they don't have required roles",
                        member.getEffectiveName(), group.getIdentifier());
                    return;
                }

                Role highestRole = RoleManager.getHighestRole(member);
                if (highestRole != null && Arrays.stream(Utils.SPECIAL_ROLES).anyMatch(role -> highestRole.getName().equalsIgnoreCase(role))) {
                    log.debug("Skipping role-restricted inactivity check for {} in group '{}' as they have a special role: {}",
                        member.getEffectiveName(), group.getIdentifier(), highestRole.getName());
                    return;
                }

                LastActivity lastActivity = discordUser.getLastActivity();
                int totalMessages = lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), group.getActivityCheckDays());
                int totalVotes = lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), group.getActivityCheckDays());
                int totalComments = lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), group.getActivityCheckDays());

                boolean hasRequiredMessages = totalMessages >= group.getMinimumMessagesForActivity();
                boolean hasRequiredVotes = totalVotes >= group.getMinimumVotesForActivity();
                boolean hasRequiredComments = totalComments >= group.getMinimumCommentsForActivity();
                final int requirementsMet = (hasRequiredMessages ? 1 : 0) + (hasRequiredComments ? 1 : 0) + (hasRequiredVotes ? 1 : 0);

                log.debug("Checking if {} should be flagged for inactivity in group '{}' (messages: {}/{}, comments: {}/{}, votes: {}/{}) (requirements met: {}/3)",
                    member.getEffectiveName(), group.getIdentifier(),
                    totalMessages, group.getMinimumMessagesForActivity(),
                    totalComments, group.getMinimumCommentsForActivity(),
                    totalVotes, group.getMinimumVotesForActivity(),
                    requirementsMet);

            lastActivity.getNominationInfo().getLastRoleRestrictedInactivityWarningTimestamp().ifPresentOrElse(timestamp -> {
                Month lastInactivityWarningMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
                Month monthNow = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

                    if (lastInactivityWarningMonth != monthNow && requirementsMet < 2) {
                        log.info("Last role-restricted inactivity check for group '{}' was not this month (last: {}, now: {}), sending inactivity message for {} (nomination info: {})",
                            group.getIdentifier(), lastInactivityWarningMonth, monthNow, member.getEffectiveName(), lastActivity.getNominationInfo());
                        sendRoleRestrictedInactiveUserMessage(member, discordUser, group, totalMessages, totalVotes, totalComments);
                    }
                }, () -> {
                    log.debug("No last role-restricted inactivity warning date found for {} in group '{}', checking if they meet the minimum requirements (nomination info: {})",
                        member.getEffectiveName(), group.getIdentifier(), lastActivity.getNominationInfo());
                    if (requirementsMet < 2) {
                        sendRoleRestrictedInactiveUserMessage(member, discordUser, group, totalMessages, totalVotes, totalComments);
                    }
                });
            });
        }
    }

    private static void sendRoleRestrictedInactiveUserMessage(Member member, DiscordUser discordUser, RoleRestrictedChannelGroup group, int totalMessages, int totalVotes, int totalComments) {
        LastActivity lastActivity = discordUser.getLastActivity();

        ChannelCache.getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            Optional<ThreadChannel> modMailThread = ModMailService.getInstance().findExistingThread(member.getUser());

            String messagesStatus = totalMessages >= group.getMinimumMessagesForActivity() ? "✅" : "⚠️";
            String votesStatus = totalVotes >= group.getMinimumVotesForActivity() ? "✅" : "⚠️";
            String commentsStatus = totalComments >= group.getMinimumCommentsForActivity() ? "✅" : "⚠️";

            int requirementsMet = (totalMessages >= group.getMinimumMessagesForActivity() ? 1 : 0) +
                (totalVotes >= group.getMinimumVotesForActivity() ? 1 : 0) +
                (totalComments >= group.getMinimumCommentsForActivity() ? 1 : 0);

            Color embedColor = requirementsMet == 0 ? Color.RED : new Color(255, 165, 0); // Orange to signify some requirements met

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

            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(embedColor)
                .setTitle("🔒 Role-Restricted Channel Inactivity")
                .setDescription("**" + member.getAsMention() + "** has been flagged for inactivity in **" + group.getDisplayName() + "**")
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField("🏷️ Channel Group",
                    String.format("**Group:** %s\n**Required Roles:** %s",
                        group.getDisplayName(),
                        !roleNames.isEmpty() ? roleNames.toString() : "Unknown"),
                    false)
                .addField("📊 Activity Summary (last %d days)".formatted(group.getActivityCheckDays()),
                    String.format("**Requirements Met:** %d/3\n**Last Activity:** %s",
                        requirementsMet,
                        lastActivity.getRoleRestrictedChannelRelativeTimestamp(group.getIdentifier())),
                    false)
                .addField("💬 Messages",
                    String.format("%s **%s** / %s required",
                        messagesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalMessages),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(group.getMinimumMessagesForActivity())),
                    true)
                .addField("🗳️ Votes",
                    String.format("%s **%s** / %s required",
                        votesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalVotes),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(group.getMinimumVotesForActivity())),
                    true)
                .addField("💭 Comments",
                    String.format("%s **%s** / %s required",
                        commentsStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalComments),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(group.getMinimumCommentsForActivity())),
                    true)
                .addField("📈 Role-Restricted Warning History",
                    String.format("**Total Warnings:** %s\n**Last Warning:** %s",
                        StringUtils.COMMA_SEPARATED_FORMAT.format(lastActivity.getNominationInfo().getTotalRoleRestrictedInactivityWarnings()),
                        lastActivity.getNominationInfo().getLastRoleRestrictedInactivityWarningDateString()),
                    false);

            modMailThread.ifPresent(threadChannel -> embedBuilder.addField("📧 Mod Mail",
                "Active thread: " + threadChannel.getAsMention(),
                false));

            embedBuilder.setTimestamp(java.time.Instant.now());
            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();

            discordUser.getLastActivity().getNominationInfo().increaseRoleRestrictedInactivityWarnings();

            log.info("Sent role-restricted channel inactivity warning message for {} in group '{}' in voting channel (role-restricted warnings: {})",
                member.getEffectiveName(), group.getIdentifier(),
                lastActivity.getNominationInfo().getTotalRoleRestrictedInactivityWarnings());
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send role-restricted inactivity warning message into!");
        });
    }

    @Override
    public void onFeatureStart() {
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (TimeUtils.isDayOfMonth(1) && SkyBlockNerdsBot.config().isNominationsEnabled()) {
                    log.info("Running nomination check");
                    nominateUsers();
                }

                if (TimeUtils.isDayOfMonth(15) && SkyBlockNerdsBot.config().isInactivityCheckEnabled()) {
                    log.info("Running inactivity check");
                    findInactiveUsers();
                    findInactiveUsersInRoleRestrictedChannels();
                }
            }
        }, 0, Duration.ofHours(1).toMillis());
    }

    @Override
    public void onFeatureEnd() {

    }
}
