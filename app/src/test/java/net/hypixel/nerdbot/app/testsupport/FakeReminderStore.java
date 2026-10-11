package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.reminder.ReminderStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ReminderStore}. Saves can be made to go unacknowledged or fail, deletes to throw. */
public class FakeReminderStore implements ReminderStore {

    private final Map<UUID, Reminder> reminders = new ConcurrentHashMap<>();
    private WriteOutcome saveOutcome = WriteOutcome.ACKNOWLEDGED;
    private boolean deletesFail;

    public FakeReminderStore seed(Reminder reminder) {
        reminders.put(reminder.getUuid(), reminder);
        return this;
    }

    public FakeReminderStore savesWill(WriteOutcome outcome) {
        this.saveOutcome = outcome;
        return this;
    }

    public FakeReminderStore deletesFail() {
        this.deletesFail = true;
        return this;
    }

    public boolean contains(UUID reminderId) {
        return reminders.containsKey(reminderId);
    }

    public int size() {
        return reminders.size();
    }

    @Override
    public Optional<Reminder> findById(UUID reminderId) {
        return Optional.ofNullable(reminders.get(reminderId));
    }

    @Override
    public List<Reminder> findByUser(String userId) {
        return reminders.values().stream()
            .filter(reminder -> userId.equalsIgnoreCase(reminder.getUserId()))
            .sorted(Comparator.comparingLong(Reminder::getTime))
            .toList();
    }

    @Override
    public CompletableFuture<List<Reminder>> loadAll() {
        return CompletableFuture.completedFuture(List.copyOf(reminders.values()));
    }

    @Override
    public CompletableFuture<Boolean> save(Reminder reminder) {
        return saveOutcome.apply(() -> reminders.put(reminder.getUuid(), reminder));
    }

    @Override
    public boolean delete(UUID reminderId) {
        if (deletesFail) {
            throw new IllegalStateException("database unavailable");
        }
        return reminders.remove(reminderId) != null;
    }
}
