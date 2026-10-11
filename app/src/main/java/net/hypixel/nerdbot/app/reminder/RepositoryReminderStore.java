package net.hypixel.nerdbot.app.reminder;

import com.mongodb.client.result.DeleteResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.hypixel.nerdbot.app.storage.RepositoryLookups;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import net.hypixel.nerdbot.marmalade.storage.database.repository.ReminderRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production {@link ReminderStore} backed by {@link ReminderRepository}. Every reminder is loaded into
 * the cache at startup, so {@link #findByUser} reads the cache. The repository is looked up on each
 * call so the bot still starts without a database.
 */
public class RepositoryReminderStore implements ReminderStore {

    private final Provider<ReminderRepository> repository;

    @Inject
    public RepositoryReminderStore(Provider<ReminderRepository> repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Reminder> findById(UUID reminderId) {
        return RepositoryLookups.toOptional(reminderId.toString(), repository.get().findById(reminderId.toString()));
    }

    @Override
    public List<Reminder> findByUser(String userId) {
        return repository.get().filter(reminder -> userId.equalsIgnoreCase(reminder.getUserId())).stream()
            .sorted(Comparator.comparingLong(Reminder::getTime))
            .toList();
    }

    @Override
    public CompletableFuture<List<Reminder>> loadAll() {
        ReminderRepository reminders = repository.get();
        return reminders.loadAllDocumentsIntoCacheAsync().thenApply(loaded -> reminders.getAll());
    }

    @Override
    public CompletableFuture<Boolean> save(Reminder reminder) {
        return repository.get().cacheAndSaveAsync(reminder)
            .thenApply(result -> result != null && result.wasAcknowledged());
    }

    @Override
    public boolean delete(UUID reminderId) {
        DeleteResult result = repository.get().deleteFromDatabase(reminderId.toString());
        return result != null && result.wasAcknowledged() && result.getDeletedCount() > 0;
    }
}
