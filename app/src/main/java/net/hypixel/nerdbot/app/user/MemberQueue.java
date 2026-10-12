package net.hypixel.nerdbot.app.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.inject.MemberWork;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Where listeners change a member's record. Work for one member runs in the order it was queued,
 * one piece at a time, so a member's activity, roles, Drive access and join or leave never race
 * each other. Different members are handled in parallel, off JDA's event threads.
 */
@Slf4j
@Singleton
public class MemberQueue {

    private static final Duration MISSING_RECORD_REPORT_INTERVAL = Duration.ofDays(1);

    private final DiscordUserStore users;
    private final Executor pool;
    private final Map<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();
    // A member without a record would otherwise log on every message they send
    private final Cache<String, Boolean> reportedMissing = Caffeine.newBuilder()
        .expireAfterWrite(MISSING_RECORD_REPORT_INTERVAL)
        .build();

    @Inject
    public MemberQueue(DiscordUserStore users, @MemberWork Executor pool) {
        this.users = users;
        this.pool = pool;
    }

    /**
     * Runs {@code task} after everything already queued for the member. A failure is logged against
     * {@code action} and doesn't stop later work.
     */
    public CompletableFuture<Void> run(String memberId, String action, Runnable task) {
        CompletableFuture<Void> next = tails.compute(memberId, (id, tail) ->
            (tail == null ? CompletableFuture.<Void>completedFuture(null) : tail.exceptionally(error -> null))
                .thenRunAsync(() -> runLogged(memberId, action, task), pool));
        next.whenComplete((done, error) -> tails.remove(memberId, next));
        return next;
    }

    /**
     * As {@link #run}, with the member's record. Every member should have one, so a missing record
     * is logged as an error, once a day per member, and the change is skipped.
     */
    public CompletableFuture<Void> withRecord(String memberId, String action, Consumer<DiscordUser> change) {
        return run(memberId, action, () -> users.findById(memberId).ifPresentOrElse(change, () -> {
            if (reportedMissing.asMap().putIfAbsent(memberId, true) == null) {
                log.error("Member {} has no user record, so their {} wasn't recorded", memberId, action);
            }
        }));
    }

    private static void runLogged(String memberId, String action, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            log.error("Failed to record the {} of member {}", action, memberId, exception);
            throw exception;
        }
    }
}
