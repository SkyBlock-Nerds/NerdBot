package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.punishment.PunishmentStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link PunishmentStore} for tests. Seed punishments with {@link #seed(Punishment)};
 * insertion order is preserved. Writes complete straight away, and can be made to go unacknowledged
 * or fail.
 */
public class FakePunishmentStore implements PunishmentStore {

    public enum WriteOutcome {
        ACKNOWLEDGED,
        UNACKNOWLEDGED,
        FAILED
    }

    private final List<Punishment> punishments = new CopyOnWriteArrayList<>();
    private WriteOutcome writeOutcome = WriteOutcome.ACKNOWLEDGED;

    public FakePunishmentStore seed(Punishment punishment) {
        punishments.add(punishment);
        return this;
    }

    public FakePunishmentStore writesWill(WriteOutcome outcome) {
        this.writeOutcome = outcome;
        return this;
    }

    @Override
    public List<Punishment> getAll() {
        return List.copyOf(punishments);
    }

    @Override
    public Optional<Punishment> findById(String punishmentId) {
        return punishments.stream()
            .filter(punishment -> punishmentId.equals(punishment.getPunishmentId()))
            .findFirst();
    }

    @Override
    public List<Punishment> findByTargetUserId(String targetUserId) {
        return punishments.stream()
            .filter(punishment -> targetUserId.equals(punishment.getTargetUserId()))
            .toList();
    }

    @Override
    public List<Punishment> findByModeratorUserId(String moderatorUserId) {
        return punishments.stream()
            .filter(punishment -> moderatorUserId.equals(punishment.getModeratorUserId()))
            .toList();
    }

    @Override
    public long countByTargetUserId(String targetUserId) {
        return findByTargetUserId(targetUserId).size();
    }

    @Override
    public long countByTargetAndType(String targetUserId, PunishmentType type) {
        return findByTargetUserId(targetUserId).stream()
            .filter(punishment -> punishment.getType() == type)
            .count();
    }

    @Override
    public CompletableFuture<Boolean> save(Punishment punishment) {
        return write(() -> punishments.add(punishment));
    }

    @Override
    public CompletableFuture<Boolean> delete(String punishmentId) {
        return write(() -> punishments.removeIf(punishment -> punishmentId.equals(punishment.getPunishmentId())));
    }

    private CompletableFuture<Boolean> write(Runnable change) {
        return switch (writeOutcome) {
            case ACKNOWLEDGED -> {
                change.run();
                yield CompletableFuture.completedFuture(true);
            }
            case UNACKNOWLEDGED -> CompletableFuture.completedFuture(false);
            case FAILED -> CompletableFuture.failedFuture(new IllegalStateException("database unavailable"));
        };
    }
}
