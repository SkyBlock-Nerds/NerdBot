package net.hypixel.nerdbot.app.punishment;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.hypixel.nerdbot.app.storage.RepositoryLookups;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import net.hypixel.nerdbot.marmalade.storage.database.repository.PunishmentRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Production {@link PunishmentStore} backed by the real {@link PunishmentRepository}. The repository
 * is looked up on each call rather than at construction, so the bot still starts without a database
 * and only the punishment commands fail.
 */
public class RepositoryPunishmentStore implements PunishmentStore {

    private final Provider<PunishmentRepository> repository;

    @Inject
    public RepositoryPunishmentStore(Provider<PunishmentRepository> repository) {
        this.repository = repository;
    }

    @Override
    public List<Punishment> getAll() {
        return repository.get().getAll();
    }

    @Override
    public Optional<Punishment> findById(String punishmentId) {
        return RepositoryLookups.toOptional(punishmentId, repository.get().findById(punishmentId));
    }

    @Override
    public List<Punishment> findByTargetUserId(String targetUserId) {
        return repository.get().findByTargetUserId(targetUserId);
    }

    @Override
    public List<Punishment> findByModeratorUserId(String moderatorUserId) {
        return repository.get().findByModeratorUserId(moderatorUserId);
    }

    @Override
    public long countByTargetUserId(String targetUserId) {
        return repository.get().countByTargetUserId(targetUserId);
    }

    @Override
    public long countByTargetAndType(String targetUserId, PunishmentType type) {
        return repository.get().countByTargetAndType(targetUserId, type);
    }

    @Override
    public CompletableFuture<Boolean> save(Punishment punishment) {
        return repository.get().cacheAndSaveAsync(punishment)
            .thenApply(result -> result != null && result.wasAcknowledged());
    }

    @Override
    public CompletableFuture<Boolean> delete(String punishmentId) {
        return repository.get().deleteFromDatabaseAsync(punishmentId)
            .thenApply(result -> result != null && result.wasAcknowledged());
    }
}
