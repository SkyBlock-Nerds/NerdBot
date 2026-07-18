package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.punishment.PunishmentStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link PunishmentStore} for tests. Seed punishments with {@link #seed(Punishment)};
 * insertion order is preserved.
 */
public class FakePunishmentStore implements PunishmentStore {

    private final List<Punishment> punishments = new ArrayList<>();

    public FakePunishmentStore seed(Punishment punishment) {
        punishments.add(punishment);
        return this;
    }

    @Override
    public List<Punishment> getAll() {
        return new ArrayList<>(punishments);
    }

    @Override
    public List<Punishment> findByModeratorUserId(String moderatorUserId) {
        return punishments.stream()
            .filter(punishment -> moderatorUserId.equals(punishment.getModeratorUserId()))
            .toList();
    }

    @Override
    public long countByTargetUserId(String targetUserId) {
        return punishments.stream()
            .filter(punishment -> targetUserId.equals(punishment.getTargetUserId()))
            .count();
    }

    @Override
    public long countByTargetAndType(String targetUserId, PunishmentType type) {
        return punishments.stream()
            .filter(punishment -> targetUserId.equals(punishment.getTargetUserId()) && punishment.getType() == type)
            .count();
    }
}
