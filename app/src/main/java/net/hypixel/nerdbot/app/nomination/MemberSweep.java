package net.hypixel.nerdbot.app.nomination;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.role.RoleManager;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checks every stored member, including those the cache has dropped, one at a time. Each check runs
 * in the member's queue, so it can't race the listeners changing the same record.
 */
@Slf4j
public class MemberSweep {

    public enum Outcome {
        ACTED,
        ALREADY_THIS_MONTH,
        NOT_DUE,
        INELIGIBLE,
        FAILED
    }

    @FunctionalInterface
    public interface Check {
        Outcome check(Member member, DiscordUser user);
    }

    private final DiscordUserStore users;
    private final MemberQueue queue;
    private final GuildScope guildScope;
    private final JDA jda;
    private final Clock clock;

    @Inject
    public MemberSweep(DiscordUserStore users, MemberQueue queue, GuildScope guildScope, JDA jda, Clock clock) {
        this.users = users;
        this.queue = queue;
        this.guildScope = guildScope;
        this.jda = jda;
        this.clock = clock;
    }

    /**
     * @throws IllegalStateException when the bot isn't in the configured guild
     */
    public SweepReport run(String label, Check check) {
        Guild guild = guildScope.mainGuild(jda).orElseThrow(() -> new IllegalStateException("The bot isn't in the configured guild"));
        long started = clock.millis();
        Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
        int scanned = 0;
        int missingMember = 0;

        for (String memberId : users.findAllIds()) {
            scanned++;
            Member member = guild.getMemberById(memberId);
            if (member == null) {
                missingMember++;
                log.warn("{}: member {} has a record but isn't in the guild", label, memberId);
                continue;
            }

            Optional<Outcome> outcome = checkInQueue(label, member, check);
            if (outcome.isEmpty()) {
                missingMember++;
                continue;
            }

            outcomes.merge(outcome.get(), 1, Integer::sum);
        }

        SweepReport report = new SweepReport(label, scanned,
            outcomes.getOrDefault(Outcome.ACTED, 0), outcomes.getOrDefault(Outcome.ALREADY_THIS_MONTH, 0),
            outcomes.getOrDefault(Outcome.NOT_DUE, 0), outcomes.getOrDefault(Outcome.INELIGIBLE, 0),
            missingMember, outcomes.getOrDefault(Outcome.FAILED, 0), clock.millis() - started);
        log.info("{} sweep finished: {}", label, report);
        return report;
    }

    // Empty when the record went while the sweep was running, as when the member left; the queue logs it
    private Optional<Outcome> checkInQueue(String label, Member member, Check check) {
        AtomicReference<Outcome> outcome = new AtomicReference<>();
        try {
            queue.withRecord(member.getId(), label + " sweep", user -> outcome.set(check.check(member, withActivity(user)))).join();
        } catch (CompletionException exception) {
            return Optional.of(Outcome.FAILED);
        }
        return Optional.ofNullable(outcome.get());
    }

    private static DiscordUser withActivity(DiscordUser user) {
        if (user.getLastActivity() == null) {
            user.setLastActivity(new LastActivity());
        }
        return user;
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock.withZone(ZoneOffset.UTC));
    }

    /** Whether {@code timestamp}, in epoch millis, falls in {@code month}, in UTC like the sweep schedule. */
    public static boolean isIn(Optional<Long> timestamp, YearMonth month) {
        return timestamp.map(millis -> YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)).equals(month)).orElse(false);
    }

    public static boolean hasHighestRole(Member member, String roleId) {
        Role highest = RoleManager.getHighestRole(member);
        return highest != null && highest.getId().equals(roleId);
    }

    public static boolean hasAnyRole(Member member, Collection<String> roleIds) {
        return member.getRoles().stream().map(Role::getId).anyMatch(roleIds::contains);
    }
}
