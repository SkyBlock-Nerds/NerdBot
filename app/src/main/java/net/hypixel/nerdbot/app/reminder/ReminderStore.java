package net.hypixel.nerdbot.app.reminder;

import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Narrow view over {@link Reminder} storage, so the scheduler, service and commands can be tested
 * against an in-memory fake. Production code is backed by {@link RepositoryReminderStore}.
 */
public interface ReminderStore {

    Optional<Reminder> findById(UUID reminderId);

    /** The user's reminders, soonest first. */
    List<Reminder> findByUser(String userId);

    /** Every stored reminder, for scheduling them again after a restart. */
    CompletableFuture<List<Reminder>> loadAll();

    /**
     * @return whether the database acknowledged the write
     */
    CompletableFuture<Boolean> save(Reminder reminder);

    /**
     * @return whether this call removed the reminder; false when it was already gone, which is how a
     * reminder is sent at most once
     */
    boolean delete(UUID reminderId);
}
