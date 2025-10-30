package net.hypixel.nerdbot.discord.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.config.RoleConfig;
import net.hypixel.nerdbot.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.discord.modmail.ModMailService;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.StringUtils;
import net.hypixel.nerdbot.util.TimeUtils;
import net.hypixel.nerdbot.util.Utils;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimerTask;

@Slf4j
public class UserNominationFeature extends BotFeature {

    public static void nominateUsers() {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        int requiredVotes = BotEnvironment.getBot().getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion();
        int requiredComments = BotEnvironment.getBot().getConfig().getRoleConfig().getMinimumCommentsRequiredForPromotion();

        log.info("Checking for users to nominate for promotion (required votes: " + requiredVotes + ", required comments: " + requiredComments + ")");

        discordUserRepository.getAll().forEach(discordUser -> {
            Member member = guild.getMemberById(discordUser.getDiscordId());

            if (member == null) {
                log.error("Member not found for user " + discordUser.getDiscordId());
                return;
            }

            Role highestRole = RoleManager.getHighestRole(member);

            if (highestRole == null) {
                log.info("Skipping nomination for " + member.getEffectiveName() + " as they have no roles");
                return;
            }

            if (!highestRole.getId().equalsIgnoreCase(BotEnvironment.getBot().getConfig().getRoleConfig().getMemberRoleId())) {
                log.info("Skipping nomination for " + member.getEffectiveName() + " as their highest role is: " + highestRole.getName());
                return;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            int totalComments = lastActivity.getTotalComments(BotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory());
            int totalVotes = lastActivity.getTotalVotes(BotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory());

            boolean hasRequiredVotes = totalVotes >= requiredVotes;
            boolean hasRequiredComments = totalComments >= requiredComments;

            log.info("Checking if " + member.getEffectiveName() + " should be nominated for promotion (total comments: " + totalComments + ", total votes: " + totalVotes + ", meets comments requirement: " + hasRequiredComments + ", meets votes requirement: " + hasRequiredVotes + ")");

            lastActivity.getNominationInfo().getLastNominationTimestamp().ifPresentOrElse(timestamp -> {
                Month lastNominationMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
                Month now = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

                if (lastNominationMonth != now && (totalComments >= requiredComments && totalVotes >= requiredVotes)) {
                    log.info("Last nomination was not this month (last: " + lastNominationMonth + ", now: " + now + "), sending nomination message for " + member.getEffectiveName() + " (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
                    sendNominationMessage(member, discordUser);
                }
            }, () -> {
                log.info("No last nomination date found for " + member.getEffectiveName() + ", checking if they meet the minimum requirements (min. votes: " + requiredVotes + ", min. comments: " + requiredComments + ", nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");

                if (totalComments >= requiredComments && totalVotes >= requiredVotes) {
                    sendNominationMessage(member, discordUser);
                }
            });
        });
    }

    public static void findInactiveUsers() {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        int requiredVotes = BotEnvironment.getBot().getConfig().getRoleConfig().getVotesRequiredForInactivityCheck();
        int requiredComments = BotEnvironment.getBot().getConfig().getRoleConfig().getCommentsRequiredForInactivityCheck();
        int requiredMessages = BotEnvironment.getBot().getConfig().getRoleConfig().getMessagesRequiredForInactivityCheck();

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
            int totalMessages = lastActivity.getTotalMessageCount(BotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());
            int totalComments = lastActivity.getTotalComments(BotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());
            int totalVotes = lastActivity.getTotalVotes(BotEnvironment.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());

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
        ChannelCache.getTextChannelById(BotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            LastActivity lastActivity = discordUser.getLastActivity();
            RoleConfig roleConfig = BotEnvironment.getBot().getConfig().getRoleConfig();

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
        RoleConfig roleConfig = BotEnvironment.getBot().getConfig().getRoleConfig();
        int totalMessages = lastActivity.getTotalMessageCount(roleConfig.getDaysRequiredForInactivityCheck());
        int totalVotes = lastActivity.getTotalVotes(roleConfig.getDaysRequiredForInactivityCheck());
        int totalComments = lastActivity.getTotalComments(roleConfig.getDaysRequiredForInactivityCheck());

        ChannelCache.getTextChannelById(BotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
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
        List<RoleRestrictedChannelGroup> channelGroups = BotEnvironment.getBot().getConfig().getChannelConfig().getRoleRestrictedChannelGroups();

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

        ChannelCache.getTextChannelById(BotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
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
                if (TimeUtils.isDayOfMonth(1) && BotEnvironment.getBot().getConfig().isNominationsEnabled()) {
                    log.info("Running nomination check");
                    nominateUsers();
                }

                if (TimeUtils.isDayOfMonth(15) && BotEnvironment.getBot().getConfig().isInactivityCheckEnabled()) {
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