package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.internalapi.database.model.user.DiscordUser;
import net.hypixel.nerdbot.internalapi.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.internalapi.feature.BotFeature;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

import java.time.Duration;
import java.time.Month;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimerTask;

@Log4j2
public class UserNominationFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (Util.isFirstDayOfMonth()) {
                    nominateUsers();
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
            int totalComments = lastActivity.getTotalComments();
            int totalVotes = lastActivity.getTotalVotes();

            boolean hasRequiredVotes = totalVotes >= requiredVotes;
            boolean hasRequiredComments = totalComments >= requiredComments;

            log.info("Checking if " + member.getEffectiveName() + " should be nominated for promotion (total comments: " + totalComments + ", total votes: " + totalVotes + ") (comments: " + hasRequiredComments + ", votes: " + hasRequiredVotes +")");

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

    private static void sendNominationMessage(Member member, DiscordUser discordUser) {
        ChannelCache.getTextChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getMemberVotingChannelId()).ifPresentOrElse(textChannel -> {
            textChannel.sendMessage("Promote " + member.getEffectiveName() + " to Nerd?\n("
                + "Total Votes: " + Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getTotalVotes())
                + " / Total Comments: " + Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getTotalComments())
                + " / Total Nominations: " + Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getNominationInfo().getTotalNominations())
                + " / Last: " + discordUser.getLastActivity().getNominationInfo().getLastNominationDateString()
                + ")").queue();
            discordUser.getLastActivity().getNominationInfo().increaseNominations();
            log.info("Sent promotion nomination message for " + member.getEffectiveName() + " in voting channel (nomination info: " + discordUser.getLastActivity().getNominationInfo() + ")");
        }, () -> {
            throw new IllegalStateException("Cannot find voting channel to send nomination message into!");
        });
    }
}