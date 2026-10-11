package net.hypixel.nerdbot.app.punishment;

import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Narrow view over {@link Punishment} storage, so {@link PunishmentService} and the punishment
 * commands can be tested against an in-memory fake. Production code is backed by
 * {@link RepositoryPunishmentStore}.
 */
public interface PunishmentStore {

    List<Punishment> getAll();

    Optional<Punishment> findById(String punishmentId);

    List<Punishment> findByTargetUserId(String targetUserId);

    List<Punishment> findByModeratorUserId(String moderatorUserId);

    long countByTargetUserId(String targetUserId);

    long countByTargetAndType(String targetUserId, PunishmentType type);

    /**
     * @return whether the database acknowledged the write
     */
    CompletableFuture<Boolean> save(Punishment punishment);

    /**
     * @return whether the database acknowledged the delete
     */
    CompletableFuture<Boolean> delete(String punishmentId);
}
