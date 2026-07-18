package net.hypixel.nerdbot.app.punishment;

import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import net.hypixel.nerdbot.marmalade.storage.database.repository.PunishmentRepository;

import java.util.List;

/**
 * Production {@link PunishmentStore} backed by the real {@link PunishmentRepository}.
 */
public class RepositoryPunishmentStore implements PunishmentStore {

    private final PunishmentRepository repository;

    public RepositoryPunishmentStore(PunishmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Punishment> getAll() {
        return repository.getAll();
    }

    @Override
    public List<Punishment> findByModeratorUserId(String moderatorUserId) {
        return repository.findByModeratorUserId(moderatorUserId);
    }

    @Override
    public long countByTargetUserId(String targetUserId) {
        return repository.countByTargetUserId(targetUserId);
    }

    @Override
    public long countByTargetAndType(String targetUserId, PunishmentType type) {
        return repository.countByTargetAndType(targetUserId, type);
    }
}
