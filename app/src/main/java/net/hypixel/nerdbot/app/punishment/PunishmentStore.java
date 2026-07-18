package net.hypixel.nerdbot.app.punishment;

import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;

import java.util.List;

/**
 * Narrow, injectable view over {@link Punishment} storage.
 *
 * <p>{@link PunishmentService} depends on this interface instead of reaching through the global bot
 * for the concrete {@code PunishmentRepository}, so its search and stats logic can be unit-tested
 * against an in-memory fake. Production code is backed by {@link RepositoryPunishmentStore}.
 */
public interface PunishmentStore {

    /**
     * @return every recorded punishment
     */
    List<Punishment> getAll();

    /**
     * @param moderatorUserId the moderator's Discord ID
     * @return the punishments issued by that moderator
     */
    List<Punishment> findByModeratorUserId(String moderatorUserId);

    /**
     * @param targetUserId the punished user's Discord ID
     * @return how many punishments the user has received
     */
    long countByTargetUserId(String targetUserId);

    /**
     * @param targetUserId the punished user's Discord ID
     * @param type         the punishment type
     * @return how many punishments of that type the user has received
     */
    long countByTargetAndType(String targetUserId, PunishmentType type);
}
