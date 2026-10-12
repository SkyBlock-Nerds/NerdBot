package net.hypixel.nerdbot.app.nomination;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.nomination.MemberSweep.Outcome;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;

import java.awt.Color;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

/**
 * Nominates members active enough for promotion in the voting channel, at most once a month each. A
 * nomination is only recorded once Discord has accepted the message, so one that fails is tried
 * again on the next run.
 */
@Slf4j
public class PromotionNominations {

    // Of the messages, votes and comments thresholds
    static final int REQUIREMENTS_FOR_NOMINATION = 2;

    private final MemberSweep sweep;
    private final VotingChannel votingChannel;
    private final DiscordUserStore users;
    private final Provider<NerdBotConfig> config;
    private final Clock clock;

    @Inject
    public PromotionNominations(MemberSweep sweep, VotingChannel votingChannel, DiscordUserStore users, Provider<NerdBotConfig> config, Clock clock) {
        this.sweep = sweep;
        this.votingChannel = votingChannel;
        this.users = users;
        this.config = config;
        this.clock = clock;
    }

    /** Members whose highest role is the member role. */
    public SweepReport nominateMembers() {
        String memberRoleId = config.get().getRoleConfig().getMemberRoleId();
        return nominate("Member", member -> MemberSweep.hasHighestRole(member, memberRoleId));
    }

    /** Members with the new member role who joined at least {@code newMemberNominationMinDays} ago. */
    public SweepReport nominateNewMembers() {
        RoleConfig roles = config.get().getRoleConfig();
        LocalDate joinedBy = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(Math.max(0, roles.getNewMemberNominationMinDays()));
        return nominate("New Member", member -> MemberSweep.hasAnyRole(member, List.of(roles.getNewMemberRoleId()))
            && !member.getTimeJoined().atZoneSameInstant(ZoneOffset.UTC).toLocalDate().isAfter(joinedBy));
    }

    private SweepReport nominate(String label, Predicate<Member> eligible) {
        votingChannel.requireAvailable();
        RoleConfig roles = config.get().getRoleConfig();
        ActivityRequirements requirements = ActivityRequirements.forPromotion(roles);
        YearMonth month = sweep.currentMonth();

        return sweep.run(label + " nominations", (member, user) -> {
            if (!eligible.test(member)) {
                return Outcome.INELIGIBLE;
            }

            NominationInfo nominations = user.getLastActivity().getNominationInfo();
            if (MemberSweep.isIn(nominations.getLastNominationTimestamp(), month)) {
                return Outcome.ALREADY_THIS_MONTH;
            }

            ActivityRequirements.Progress progress = requirements.measure(user.getLastActivity());
            log.debug("{} has {} messages, {} votes and {} comments ({}/3 requirements)",
                member.getId(), progress.messages(), progress.votes(), progress.comments(), progress.met());
            if (progress.met() < REQUIREMENTS_FOR_NOMINATION) {
                return Outcome.NOT_DUE;
            }

            return send(member, user, nominationEmbed(member, nominations, progress, NominationTypeResolver.resolve(member, roles)));
        });
    }

    private Outcome send(Member member, DiscordUser user, MessageEmbed embed) {
        try {
            votingChannel.send(embed).join();
        } catch (CompletionException exception) {
            log.error("Discord didn't accept the nomination of {}; they'll be tried again next run", member.getId(), exception);
            return Outcome.FAILED;
        }

        NominationInfo nominations = user.getLastActivity().getNominationInfo();
        nominations.setTotalNominations(nominations.getTotalNominations() + 1);
        nominations.setLastNominationTimestamp(clock.millis());
        try {
            users.save(user).join();
        } catch (CompletionException exception) {
            log.error("Nominated {} but couldn't save it; the autosave will retry", member.getId(), exception);
        }
        log.info("Nominated {} for promotion ({} nominations so far)", member.getId(), nominations.getTotalNominations());
        return Outcome.ACTED;
    }

    private MessageEmbed nominationEmbed(Member member, NominationInfo nominations, ActivityRequirements.Progress progress, String nominationType) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("Promotion Nomination")
            .setDescription("**" + member.getEffectiveName() + "** is eligible for promotion!")
            .setThumbnail(member.getEffectiveAvatarUrl())
            .setTimestamp(clock.instant());
        SweepEmbeds.addProgress(embed, progress, null)
            .addField("Nomination History", String.format("**Total Nominations:** %s\n**Last Nomination:** %s",
                StringUtils.formatWithCommas(nominations.getTotalNominations()), nominations.getLastNominationDateString()), false);

        if (nominationType != null) {
            embed.addField("Nomination Type", nominationType, false);
        }
        return embed.build();
    }
}
