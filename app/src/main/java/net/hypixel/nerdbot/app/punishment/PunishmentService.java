package net.hypixel.nerdbot.app.punishment;

import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Punishment search and statistics, extracted from {@code PunishmentCommands} so the querying and
 * aggregation logic can be exercised against a {@link PunishmentStore} without a live bot or
 * database. The commands keep the Discord modal/embed plumbing.
 */
public class PunishmentService {

    private final PunishmentStore store;

    public PunishmentService(PunishmentStore store) {
        this.store = store;
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
