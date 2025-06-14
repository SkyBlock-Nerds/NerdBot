package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.bot.config.RoleConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.command.ModMailCommands;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

import java.time.Duration;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Optional;
import java.util.TimerTask;

@Log4j2
public class UserNominationFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (Util.isDayOfMonth(1) && NerdBotApp.getBot().getConfig().isNominationsEnabled()) {
                    log.info("Running nomination check");
                    nominateUsers();
                }

                if (Util.isDayOfMonth(15) && NerdBotApp.getBot().getConfig().isInactivityCheckEnabled()) {
                    log.info("Running inactivity check");
                    findInactiveUsers();
                }
            }
        }, 0, Duration.ofHours(1).toMillis());
    }

    @Override
    public void onFeatureEnd() {

    }

    public static void nominateUsers() {
        Guild guild = Util.getMainGuild();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        int requiredVotes = NerdBotApp.getBot().getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion();
        int requiredComments = NerdBotApp.getBot().getConfig().getRoleConfig().getMinimumCommentsRequiredForPromotion();

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

            if (!highestRole.getId().equalsIgnoreCase(NerdBotApp.getBot().getConfig().getRoleConfig().getMemberRoleId())) {
                log.info("Skipping nomination for " + member.getEffectiveName() + " as their highest role is: " + highestRole.getName());
                return;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            int totalComments = lastActivity.getTotalComments(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory());
            int totalVotes = lastActivity.getTotalVotes(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory());

            boolean hasRequiredVotes = totalVotes >= requiredVotes;
            boolean hasRequiredComments = totalComments >= requiredComments;

            log.info("Checking if " + member.getEffectiveName() + " should be nominated for promotion (total comments: " + totalComments + ", total votes: " + totalVotes + ", meets comments requirement: " + hasRequiredComments + ", meets votes requirement: " + hasRequiredVotes + ")");

            lastActivity.getNominationInfo().getLastNominationDate().ifPresentOrElse(date -> {
                Month lastNominationMonth = date.toInstant().atZone(ZoneId.systemDefault()).getMonth();
                Month now = Calendar.getInstance().toInstant().atZone(ZoneId.systemDefault()).getMonth();

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
        Guild guild = Util.getMainGuild();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        int requiredVotes = NerdBotApp.getBot().getConfig().getRoleConfig().getVotesRequiredForInactivityCheck();
        int requiredComments = NerdBotApp.getBot().getConfig().getRoleConfig().getCommentsRequiredForInactivityCheck();
        int requiredMessages = NerdBotApp.getBot().getConfig().getRoleConfig().getMessagesRequiredForInactivityCheck();

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

            if (Arrays.stream(Util.SPECIAL_ROLES).anyMatch(role -> highestRole.getName().equalsIgnoreCase(role))) {
                log.info("Skipping inactivity check for " + member.getEffectiveName() + " as they have a special role: " + highestRole.getName());
                return;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            int totalMessages = lastActivity.getTotalMessageCount(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());
            int totalComments = lastActivity.getTotalComments(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());
            int totalVotes = lastActivity.getTotalVotes(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForInactivityCheck());

            boolean hasRequiredVotes = totalVotes >= requiredVotes;
            boolean hasRequiredComments = totalComments >= requiredComments;
            boolean hasRequiredMessages = totalMessages >= requiredMessages;
            final int requirementsMet = (hasRequiredMessages ? 1 : 0) + (hasRequiredComments ? 1 : 0) + (hasRequiredVotes ? 1 : 0);

            log.info("Checking if " + member.getEffectiveName() + " should be flagged for inactivity (total messages: " + totalMessages + ", total comments: " + totalComments + ", total votes: " + totalVotes + ") (has min. comments: " + hasRequiredComments + ", has min. votes: " + hasRequiredVotes + ", has min. messages: " + hasRequiredMessages + ", requirements met: " + requirementsMet + "/3)");

            lastActivity.getNominationInfo().getLastInactivityWarningDate().ifPresentOrElse(date -> {
                Month lastInactivityWarningMonth = date.toInstant().atZone(ZoneId.systemDefault()).getMonth();
                Month monthNow = Calendar.getInstance().toInstant().atZone(ZoneId.systemDefault()).getMonth();

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
        ChannelCache.getTextChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            textChannel.sendMessage("Promote " + member.getEffectiveName() + " to Nerd?\n("
                + "Tracked Messages: " + Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getTotalMessageCount(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory()))
                + " / Votes: " + Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getTotalVotes(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory()))
                + " / Comments: " + Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getTotalComments(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory()))
                + " / Nominations: " + Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getNominationInfo().getTotalNominations())
                + " / Last: " + discordUser.getLastActivity().getNominationInfo().getLastNominationDateString()
                + ")").queue();
            discordUser.getLastActivity().getNominationInfo().increaseNominations();
            log.info("Sent promotion nomination message for " + member.getEffectiveName() + " in voting channel (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send nomination message into!");
        });
    }

    private static void sendInactiveUserMessage(Member member, DiscordUser discordUser, int requiredMessages, int requiredVotes, int requiredComments) {
        LastActivity lastActivity = discordUser.getLastActivity();
        RoleConfig roleConfig = NerdBotApp.getBot().getConfig().getRoleConfig();
        int totalMessages = lastActivity.getTotalMessageCount(roleConfig.getDaysRequiredForInactivityCheck());
        int totalVotes = lastActivity.getTotalVotes(roleConfig.getDaysRequiredForInactivityCheck());
        int totalComments = lastActivity.getTotalComments(roleConfig.getDaysRequiredForInactivityCheck());

        ChannelCache.getTextChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            Optional<ThreadChannel> modMailThread = ModMailCommands.getModMailThread(member.getUser());
            String message = "Warn or remove " + member.getEffectiveName() + " for inactivity? Last " + roleConfig.getDaysRequiredForInactivityCheck() + " day(s):"
                + "\n(Tracked Messages: " + Util.COMMA_SEPARATED_FORMAT.format(totalMessages) + "/" + requiredMessages
                + " / Votes: " + Util.COMMA_SEPARATED_FORMAT.format(totalVotes) + "/" + requiredVotes
                + " / Comments: " + Util.COMMA_SEPARATED_FORMAT.format(totalComments) + "/" + requiredComments
                + " / Inactivity Warnings: " + Util.COMMA_SEPARATED_FORMAT.format(lastActivity.getNominationInfo().getTotalInactivityWarnings())
                + " / Last: " + lastActivity.getNominationInfo().getLastInactivityWarningDateString()
                + ")";

            if (modMailThread.isPresent()) {
                message += "\nMod Mail Thread: " + modMailThread.get().getAsMention();
            }

            textChannel.sendMessage(message).queue();
            discordUser.getLastActivity().getNominationInfo().increaseInactivityWarnings();
            log.info("Sent inactivity warning message for " + member.getEffectiveName() + " in voting channel (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send inactivity warning message into!");
        });
    }
}