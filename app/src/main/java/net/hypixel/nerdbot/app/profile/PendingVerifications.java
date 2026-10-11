package net.hypixel.nerdbot.app.profile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * /verify requests waiting for a moderator. Each one, and each decline, lasts a day from when it
 * was made. They're kept in memory, so a restart drops them and members ask again.
 */
@Singleton
public class PendingVerifications {

    public static final Duration EXPIRY = Duration.ofDays(1);

    public enum State { NONE, PENDING, DECLINED }

    private sealed interface Entry {
    }

    private record Pending(MojangProfile profile) implements Entry {
    }

    private record Declined() implements Entry {
    }

    private final Cache<String, Entry> entries;

    @Inject
    public PendingVerifications(Clock clock) {
        this.entries = Caffeine.newBuilder()
            .expireAfterWrite(EXPIRY)
            .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
            .build();
    }

    public State state(String memberId) {
        return switch (entries.getIfPresent(memberId)) {
            case Pending ignored -> State.PENDING;
            case Declined ignored -> State.DECLINED;
            case null -> State.NONE;
        };
    }

    public void submit(String memberId, MojangProfile profile) {
        entries.put(memberId, new Pending(profile));
    }

    /** Removes the request and returns its profile, or empty when it expired or was declined. A decline is left in place. */
    public Optional<MojangProfile> accept(String memberId) {
        AtomicReference<MojangProfile> accepted = new AtomicReference<>();
        entries.asMap().computeIfPresent(memberId, (id, entry) -> {
            if (entry instanceof Pending pending) {
                accepted.set(pending.profile());
                return null;
            }
            return entry;
        });
        return Optional.ofNullable(accepted.get());
    }

    /** The member can't ask again until the decline expires. */
    public void decline(String memberId) {
        entries.put(memberId, new Declined());
    }

    public void clear(String memberId) {
        entries.invalidate(memberId);
    }
}
