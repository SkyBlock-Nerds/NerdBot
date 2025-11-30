package net.hypixel.nerdbot.app.nomination;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.role.RoleManager;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.BiPredicate;

@Slf4j
public class NominationService {

    private static final NominationService INSTANCE = new NominationService();

    private NominationService() {
    }

    public static NominationService getInstance() {
        return INSTANCE;
    }

    public NominationSweepReport runMemberNominationSweep() {
        String memberRoleId = DiscordBotEnvironment.getBot().getConfig().getRoleConfig().getMemberRoleId();

        BiPredicate<Member, DiscordUser> eligibility = (member, discordUser) -> {
            Role highestRole = RoleManager.getHighestRole(member);
            if (highestRole == null) {
                log.info("Skipping nomination for {} as they have no roles", member.getEffectiveName());
                return false;
            }

            if (!highestRole.getId().equalsIgnoreCase(memberRoleId)) {
                log.info("Skipping nomination for {} as their highest role is: {}", member.getEffectiveName(), highestRole.getName());
                return false;
            }

            return true;
        };

        return runNominationSweep(eligibility, null);
    }

    public NominationSweepReport runNewMemberNominationSweep() {
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        String newMemberRoleId = roleConfig.getNewMemberRoleId();
        int minDays = Math.max(0, roleConfig.getNewMemberNominationMinDays());

        BiPredicate<Member, DiscordUser> eligibility = (member, discordUser) -> {
            Role highestRole = RoleManager.getHighestRole(member);
            if (highestRole == null) {
                log.info("Skipping new member nomination for {} as they have no roles", member.getEffectiveName());
                return false;
            }

            boolean hasNewMemberRole = member.getRoles().stream().anyMatch(r -> r.getId().equalsIgnoreCase(newMemberRoleId));
            if (!hasNewMemberRole) {
                return false;
            }

            OffsetDateTime joinedAt = member.getTimeJoined();
            LocalDate joinDate = joinedAt.toLocalDate();
            LocalDate thresholdDate = LocalDate.now(ZoneId.systemDefault()).minusDays(minDays);
            if (joinDate.isAfter(thresholdDate)) {
                log.info("Skipping new member nomination for {} as they joined on {} (< {} days)", member.getEffectiveName(), joinedAt, minDays);
                return false;
            }

            return true;
        };

        return runNominationSweep(eligibility, "NewMember");
    }

    private NominationSweepReport runNominationSweep(BiPredicate<Member, DiscordUser> eligibility, String contextLabel) {
        Guild guild = DiscordUtils.getMainGuild();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();
        int requiredVotes = roleConfig.getMinimumVotesRequiredForPromotion();
        int requiredComments = roleConfig.getMinimumCommentsRequiredForPromotion();
        int requiredMessages = roleConfig.getMinimumMessagesRequiredForPromotion();
        int daysWindow = roleConfig.getDaysRequiredForVoteHistory();

        long startNanos = System.nanoTime();

        if (contextLabel == null) {
            log.info("Checking for users to nominate for promotion (required votes: {}, required comments: {})", requiredVotes, requiredComments);
        } else {
            log.info("Checking {} candidates for promotion eligibility (required votes: {}, required comments: {})", contextLabel, requiredVotes, requiredComments);
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
                log.error("Member not found for user {}", discordUser.getDiscordId());
                continue;
            }

            if (!eligibility.test(member, discordUser)) {
                ineligible++;
                continue;
            }

            eligible++;

            NominationOutcome outcome = evaluateNomination(member, discordUser, contextLabel, requiredVotes, requiredComments, requiredMessages, daysWindow);
            if (outcome == NominationOutcome.NOMINATED) {
                nominated++;
            } else if (outcome == NominationOutcome.SKIPPED_ALREADY_THIS_MONTH) {
                skippedAlreadyThisMonth++;
            } else if (outcome == NominationOutcome.SKIPPED_BELOW_THRESHOLD) {
                skippedBelowThreshold++;
            }
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        String label = (contextLabel == null ? "Member" : contextLabel);
        log.info("Nomination sweep complete for {}: scanned={}, eligible={}, nominated={}, belowThreshold={}, alreadyThisMonth={}, ineligible={}, missingMember={}, took={}ms",
            label, scanned, eligible, nominated, skippedBelowThreshold, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);

        return new NominationSweepReport(label, scanned, eligible, nominated, skippedBelowThreshold, skippedAlreadyThisMonth, ineligible, missingMember, durationMs);
    }

    private NominationOutcome evaluateNomination(Member member, DiscordUser discordUser, String contextLabel, int requiredVotes, int requiredComments, int requiredMessages, int daysWindow) {
        LastActivity lastActivity = discordUser.getLastActivity();
        int totalMessages = lastActivity.getTotalMessageCount(daysWindow);
        int totalComments = lastActivity.getTotalComments(daysWindow);
        int totalVotes = lastActivity.getTotalVotes(daysWindow);

        boolean hasRequiredVotes = totalVotes >= requiredVotes;
        boolean hasRequiredComments = totalComments >= requiredComments;
        boolean hasRequiredMessages = totalMessages >= requiredMessages;
        int requirementsMet = (hasRequiredMessages ? 1 : 0) + (hasRequiredVotes ? 1 : 0) + (hasRequiredComments ? 1 : 0);

        if (contextLabel == null) {
            log.info("Checking if {} should be nominated for promotion (total messages: {}, total comments: {}, total votes: {}, meets messages requirement: {}, meets comments requirement: {}, meets votes requirement: {}, requirements met: {}/3)",
                member.getEffectiveName(), totalMessages, totalComments, totalVotes, hasRequiredMessages, hasRequiredComments, hasRequiredVotes, requirementsMet);
        } else {
            log.info("[{}] Checking if {} should be nominated (total messages: {}, total comments: {}, total votes: {}, meets messages requirement: {}, meets comments requirement: {}, meets votes requirement: {}, requirements met: {}/3)",
                contextLabel, member.getEffectiveName(), totalMessages, totalComments, totalVotes, hasRequiredMessages, hasRequiredComments, hasRequiredVotes, requirementsMet);
        }

        final NominationOutcome[] outcomeRef = new NominationOutcome[]{null};

        lastActivity.getNominationInfo().getLastNominationTimestamp().ifPresentOrElse(timestamp -> {
            Month lastNominationMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
            Month now = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

            if (lastNominationMonth != now && requirementsMet >= 2) {
                if (contextLabel == null) {
                    log.info("Last nomination was not this month (last: {}, now: {}), sending nomination message for {} (nomination info: {})",
                        lastNominationMonth, now, member.getEffectiveName(), discordUser.getLastActivity().getNominationInfo());
                } else {
                    log.info("[{}] Last nomination not this month (last: {}, now: {}), sending nomination message for {}",
                        contextLabel, lastNominationMonth, now, member.getEffectiveName());
                }

                sendNominationMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, daysWindow);
                outcomeRef[0] = NominationOutcome.NOMINATED;
            }
        }, () -> {
            if (contextLabel == null) {
                log.info("No last nomination date found for {}, checking if they meet the minimum requirements (min. messages: {}, min. votes: {}, min. comments: {}, nomination info: {})",
                    member.getEffectiveName(), requiredMessages, requiredVotes, requiredComments, discordUser.getLastActivity().getNominationInfo());
            } else {
                log.info("[{}] No last nomination date found for {}, checking minimum requirements",
                    contextLabel, member.getEffectiveName());
            }

            if (requirementsMet >= 2) {
                sendNominationMessage(member, discordUser, requiredMessages, requiredVotes, requiredComments, daysWindow);
                outcomeRef[0] = NominationOutcome.NOMINATED;
            }
        });

        if (outcomeRef[0] != null) {
            return outcomeRef[0];
        }

        Long lastTimestamp = lastActivity.getNominationInfo().getLastNominationTimestamp().orElse(null);
        if (lastTimestamp != null) {
            Month lastMonth = Instant.ofEpochMilli(lastTimestamp).atZone(ZoneId.systemDefault()).getMonth();
            Month nowMonth = Instant.now().atZone(ZoneId.systemDefault()).getMonth();

            if (lastMonth == nowMonth) {
                return NominationOutcome.SKIPPED_ALREADY_THIS_MONTH;
            }
        }

        if (requirementsMet >= 2) {
            return NominationOutcome.NOMINATED;
        }

        return NominationOutcome.SKIPPED_BELOW_THRESHOLD;
    }

    private void sendNominationMessage(Member member, DiscordUser discordUser, int requiredMessages, int requiredVotes, int requiredComments, int daysWindow) {
        ChannelCache.getTextChannelById(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            LastActivity lastActivity = discordUser.getLastActivity();
            RoleConfig roleConfig = DiscordBotEnvironment.getBot().getConfig().getRoleConfig();

            int totalMessages = lastActivity.getTotalMessageCount(daysWindow);
            int totalVotes = lastActivity.getTotalVotes(daysWindow);
            int totalComments = lastActivity.getTotalComments(daysWindow);

            int requirementsMet = (totalMessages >= requiredMessages ? 1 : 0)
                + (totalVotes >= requiredVotes ? 1 : 0)
                + (totalComments >= requiredComments ? 1 : 0);
            String votesStatus = totalVotes >= requiredVotes ? "✅" : "❌";
            String commentsStatus = totalComments >= requiredComments ? "✅" : "❌";
            String messagesStatus = totalMessages >= requiredMessages ? "✅" : "❌";

            String nominationType = NominationTypeResolver.resolve(member, roleConfig);

            EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("Promotion Nomination")
                .setDescription("**" + member.getEffectiveName() + "** is eligible for promotion!")
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField(String.format("Activity Summary (last %d days)", daysWindow),
                    String.format("**Requirements Met:** %d/3", requirementsMet),
                    false)
                .addField("Messages",
                    String.format("%s **%s** / %s required",
                        messagesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalMessages),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredMessages)),
                    true)
                .addField("Votes",
                    String.format("%s **%s** / %s required",
                        votesStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalVotes),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredVotes)),
                    true)
                .addField("Comments",
                    String.format("%s **%s** / %s required",
                        commentsStatus,
                        StringUtils.COMMA_SEPARATED_FORMAT.format(totalComments),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(requiredComments)),
                    true)
                .addField("Nomination History",
                    String.format("**Total Nominations:** %s\n**Last Nomination:** %s",
                        StringUtils.COMMA_SEPARATED_FORMAT.format(lastActivity.getNominationInfo().getTotalNominations()),
                        lastActivity.getNominationInfo().getLastNominationDateString()),
                    false)
                .setTimestamp(Instant.now());

            if (nominationType != null) {
                embedBuilder.addField("Nomination Type", nominationType, false);
            }

            textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
            discordUser.getLastActivity().getNominationInfo().increaseNominations();
            log.info("Sent promotion nomination message for {} in voting channel (total nominations: {})",
                member.getEffectiveName(), discordUser.getLastActivity().getNominationInfo().getTotalNominations());
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send nomination message into!");
        });
    }

    private enum NominationOutcome {
        NOMINATED,
        SKIPPED_ALREADY_THIS_MONTH,
        SKIPPED_BELOW_THRESHOLD
    }
}
