package net.hypixel.nerdbot.app.punishment;

import jakarta.inject.Inject;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Recording, looking up and deleting punishments, plus search and stats. The commands keep the
 * Discord modal and embed plumbing.
 */
public class PunishmentService {

    private final PunishmentStore store;

    @Inject
    public PunishmentService(PunishmentStore store) {
        this.store = store;
    }

    /**
     * Saves a new punishment. Blank notes are stored as no notes.
     *
     * @return the saved punishment; fails if the database didn't acknowledge the write
     */
    public CompletableFuture<Punishment> record(String targetUserId, String moderatorUserId, PunishmentType type,
                                                String reason, @Nullable String notes) {
        String storedNotes = notes == null || notes.isBlank() ? null : notes;
        Punishment punishment = new Punishment(targetUserId, moderatorUserId, type, reason, storedNotes);

        return store.save(punishment).thenApply(acknowledged -> {
            if (!acknowledged) {
                throw new IllegalStateException("Database did not acknowledge punishment " + punishment.getPunishmentId());
            }
            return punishment;
        });
    }

    /**
     * Fails if the database didn't acknowledge the delete. Deleting an id that no longer exists
     * succeeds.
     */
    public CompletableFuture<Void> delete(String punishmentId) {
        return store.delete(punishmentId).thenAccept(acknowledged -> {
            if (!acknowledged) {
                throw new IllegalStateException("Database did not acknowledge deleting punishment " + punishmentId);
            }
        });
    }

    public Optional<Punishment> find(String punishmentId) {
        return store.findById(punishmentId);
    }

    public List<Punishment> history(String targetUserId) {
        return store.findByTargetUserId(targetUserId);
    }

    /**
     * Search punishments by moderator and/or type.
     *
     * <p>When a moderator is given, only their punishments are scanned; otherwise all punishments
     * are scanned. A type, when given, further filters the results. If neither filter is given the
     * result is empty (callers should require at least one filter). At most {@code limit} results are
     * returned.
     *
     * @param moderatorUserId the moderator to filter by, or {@code null} for any
     * @param type            the punishment type to filter by, or {@code null} for any
     * @param limit           the maximum number of results
     * @return the matching punishments, capped at {@code limit}
     */
    public List<Punishment> search(@Nullable String moderatorUserId, @Nullable PunishmentType type, int limit) {
        List<Punishment> base;
        if (moderatorUserId != null) {
            base = store.findByModeratorUserId(moderatorUserId);
        } else if (type != null) {
            base = store.getAll();
        } else {
            return List.of();
        }

        return base.stream()
            .filter(punishment -> type == null || punishment.getType() == type)
            .limit(limit)
            .toList();
    }

    /**
     * Tally a user's punishments into a total and a per-type breakdown (omitting zero counts).
     *
     * @param targetUserId the punished user's Discord ID
     * @return the user's {@link PunishmentStats}
     */
    public PunishmentStats stats(String targetUserId) {
        long total = store.countByTargetUserId(targetUserId);

        Map<PunishmentType, Long> countsByType = new EnumMap<>(PunishmentType.class);
        for (PunishmentType type : PunishmentType.values()) {
            long count = store.countByTargetAndType(targetUserId, type);
            if (count > 0) {
                countsByType.put(type, count);
            }
        }

        return new PunishmentStats(total, countsByType);
    }
}
