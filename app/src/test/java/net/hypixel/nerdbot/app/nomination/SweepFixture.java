package net.hypixel.nerdbot.app.nomination;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MemberFakes;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;

/**
 * A guild of members with stored records, and a voting channel that records what's sent. Every
 * threshold is one, so a member's activity can be set up with a message, a vote and a comment.
 */
final class SweepFixture {

    static final String MEMBER_ROLE = "10";
    static final String NEW_MEMBER_ROLE = "11";
    static final String MODERATOR_ROLE = "12";

    // LastActivity measures its windows from the real time, so activity and the clock both start from it
    final MutableClock clock = new MutableClock(Instant.now());
    final NerdBotConfig config = new NerdBotConfig();
    final FakeDiscordUserStore users = new FakeDiscordUserStore();
    final Map<String, Member> guildMembers = new HashMap<>();
    final RecordingVotingChannel votingChannel = new RecordingVotingChannel();
    final Map<String, List<String>> tickets = new HashMap<>();
    final OpenTickets openTickets = memberId -> tickets.getOrDefault(memberId, List.of());
    final MemberSweep sweep;

    SweepFixture() {
        config.setGuildId(MAIN_GUILD_ID);
        config.getRoleConfig().setMemberRoleId(MEMBER_ROLE);
        config.getRoleConfig().setNewMemberRoleId(NEW_MEMBER_ROLE);
        config.getRoleConfig().setNewMemberNominationMinDays(30);
        config.getRoleConfig().setMinimumMessagesRequiredForPromotion(1);
        config.getRoleConfig().setMinimumVotesRequiredForPromotion(1);
        config.getRoleConfig().setMinimumCommentsRequiredForPromotion(1);
        config.getRoleConfig().setMessagesRequiredForInactivityCheck(1);
        config.getRoleConfig().setVotesRequiredForInactivityCheck(1);
        config.getRoleConfig().setCommentsRequiredForInactivityCheck(1);

        Guild guild = Fakes.of(Guild.class, Map.of(
            "getId", MAIN_GUILD_ID,
            "getMemberById", (CallRecorder.Answer) args -> guildMembers.get(String.valueOf(args[0]))));
        JDA jda = Fakes.of(JDA.class, Map.of("getGuilds", List.of(guild)));
        sweep = new MemberSweep(users, new MemberQueue(users, Runnable::run), new GuildScope(() -> config), jda, clock);
    }

    PromotionNominations nominations() {
        return new PromotionNominations(sweep, votingChannel, users, () -> config, clock);
    }

    InactivityWarnings warnings() {
        return new InactivityWarnings(sweep, votingChannel, openTickets, users, () -> config, clock);
    }

    /** A stored member in the guild, holding {@code roleIds} with the first as their highest. */
    DiscordUser member(String id, String... roleIds) {
        return member(id, OffsetDateTime.now(ZoneOffset.UTC).minusYears(1), roleIds);
    }

    DiscordUser member(String id, OffsetDateTime joined, String... roleIds) {
        List<Role> roles = new ArrayList<>();
        for (String roleId : roleIds) {
            roles.add(MemberFakes.role(roleId));
        }
        Member base = MemberFakes.member(id, false, roles);
        guildMembers.put(id, Fakes.of(Member.class, Map.of(
            "getId", id,
            "getUser", base.getUser(),
            "getEffectiveName", base.getEffectiveName(),
            "getAsMention", base.getAsMention(),
            "getGuild", base.getGuild(),
            "getRoles", List.copyOf(roles),
            "getTimeJoined", joined)));

        DiscordUser user = new DiscordUser(id);
        users.seed(user);
        return user;
    }

    /** Activity now: {@code messages} messages, {@code votes} votes and {@code comments} comments. */
    static void active(DiscordUser user, int messages, int votes, int comments) {
        LastActivity activity = user.getLastActivity();
        long now = System.currentTimeMillis();
        for (int i = 0; i < messages; i++) {
            activity.addChannelHistory("50", "general", now);
        }
        for (int i = 0; i < votes; i++) {
            activity.getSuggestionVoteHistoryMap().put("vote-" + i, now);
        }
        for (int i = 0; i < comments; i++) {
            activity.getSuggestionCommentHistory().add(now);
        }
    }

    long lastMonth() {
        return clock.instant().minus(Duration.ofDays(40)).toEpochMilli();
    }

    static final class RecordingVotingChannel implements VotingChannel {

        final List<MessageEmbed> sent = new ArrayList<>();
        boolean available = true;
        boolean rejects;

        @Override
        public void requireAvailable() {
            if (!available) {
                throw new IllegalStateException("The member voting channel can't be found");
            }
        }

        @Override
        public CompletableFuture<Void> send(MessageEmbed embed) {
            if (rejects) {
                return CompletableFuture.failedFuture(new IllegalStateException("Missing permissions"));
            }
            sent.add(embed);
            return CompletableFuture.completedFuture(null);
        }

        List<String> titles() {
            return sent.stream().map(MessageEmbed::getTitle).toList();
        }
    }
}
