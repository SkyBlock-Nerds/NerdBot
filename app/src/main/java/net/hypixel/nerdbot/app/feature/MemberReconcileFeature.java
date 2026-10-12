package net.hypixel.nerdbot.app.feature;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.badge.BadgeCatalog;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.role.RoleIdSync;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Brings every member's record in line with the guild, at startup and then hourly. A member who
 * joined while the bot was down gets a record, stored role ids follow Discord, and badges the bot no
 * longer knows are dropped. The join and role listeners do this as it happens; this catches what
 * they missed. Each member goes through their queue and is saved only when something changed.
 */
@Slf4j
public class MemberReconcileFeature extends BotFeature {

    private static final Duration PERIOD = Duration.ofHours(1);

    enum Change {
        CREATED,
        UPDATED,
        UNCHANGED
    }

    private final GuildScope guildScope;
    private final JDA jda;
    private final MemberQueue queue;
    private final DiscordUserStore users;
    private final BadgeCatalog badges;
    private final Provider<BotState> state;

    @Inject
    public MemberReconcileFeature(GuildScope guildScope, JDA jda, MemberQueue queue, DiscordUserStore users, BadgeCatalog badges,
                                  Provider<BotState> state) {
        this.guildScope = guildScope;
        this.jda = jda;
        this.queue = queue;
        this.users = users;
        this.badges = badges;
        this.state = state;
    }

    @Override
    public void onFeatureStart() {
        if (state.get().isReadOnly()) {
            log.warn("Not reconciling member records; the bot is in read-only mode");
            return;
        }

        scheduleAtFixedRate("member-reconcile", this::reconcileAll, 0, PERIOD.toMillis());
    }

    void reconcileAll() {
        Optional<Guild> guild = guildScope.mainGuild(jda);
        if (guild.isEmpty()) {
            log.warn("Not reconciling member records; the bot isn't in the configured guild");
            return;
        }

        List<Member> members = guild.get().loadMembers().get();
        List<CompletableFuture<Change>> changes = new ArrayList<>();
        for (Member member : members) {
            if (!member.getUser().isBot()) {
                changes.add(reconcile(member.getId(), member.getRoles().stream().map(Role::getId).toList()));
            }
        }

        Map<Change, Integer> tally = new EnumMap<>(Change.class);
        int failed = 0;
        for (CompletableFuture<Change> change : changes) {
            try {
                tally.merge(change.join(), 1, Integer::sum);
            } catch (CompletionException exception) {
                failed++;
            }
        }

        log.info("Reconciled {} member records: {} created, {} updated, {} unchanged, {} failed", changes.size(),
            tally.getOrDefault(Change.CREATED, 0), tally.getOrDefault(Change.UPDATED, 0), tally.getOrDefault(Change.UNCHANGED, 0), failed);
    }

    /** A lookup that fails leaves the record alone, so a database error can't replace a real record with a blank one. */
    CompletableFuture<Change> reconcile(String memberId, List<String> roleIds) {
        AtomicReference<Change> change = new AtomicReference<>();

        return queue.run(memberId, "record reconcile", () -> {
            Optional<DiscordUser> stored = users.findById(memberId);
            if (stored.isEmpty()) {
                log.warn("Member {} had no record, so they probably joined while the bot was down; creating one", memberId);
            }

            DiscordUser user = stored.orElseGet(() -> new DiscordUser(memberId));
            boolean filled = fillMissingFields(user);
            boolean badgesDropped = dropUnknownBadges(user);
            boolean rolesChanged = RoleIdSync.applyRoleIds(user, roleIds);

            if (stored.isEmpty() || filled || badgesDropped || rolesChanged) {
                users.save(user).join();
                change.set(stored.isEmpty() ? Change.CREATED : Change.UPDATED);
            } else {
                change.set(Change.UNCHANGED);
            }
        }).thenApply(done -> change.get());
    }

    private static boolean fillMissingFields(DiscordUser user) {
        boolean filled = false;
        if (user.getLastActivity() == null) {
            user.setLastActivity(new LastActivity());
            filled = true;
        }
        if (user.getBadges() == null) {
            user.setBadges(new ArrayList<>());
            filled = true;
        }
        return filled;
    }

    private boolean dropUnknownBadges(DiscordUser user) {
        // With no badges loaded, as when the config has none, every badge would look unknown
        if (badges.all().isEmpty()) {
            return false;
        }

        return user.getBadges().removeIf(badge -> {
            boolean unknown = badges.find(badge.badgeId()).isEmpty();
            if (unknown) {
                log.warn("Dropping badge '{}' from member {}; it's no longer in the badge config", badge.badgeId(), user.getDiscordId());
            }
            return unknown;
        });
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
