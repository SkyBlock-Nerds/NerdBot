package net.hypixel.nerdbot.app.reminder;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sends each reminder when it comes due, on the bot's shared scheduler. Reminders are only held
 * here while pending; the database is what survives a restart, and {@link #restoreAll()} schedules
 * them again on startup.
 */
@Slf4j
@Singleton
public class ReminderScheduler {

    private final ScheduledExecutorService executor;
    private final ReminderStore store;
    private final ReminderSender sender;
    private final Clock clock;
    private final Map<UUID, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    @Inject
    public ReminderScheduler(ScheduledExecutorService executor, ReminderStore store, ReminderSender sender, Clock clock) {
        this.executor = executor;
        this.store = store;
        this.sender = sender;
        this.clock = clock;
    }

    /** Schedules the reminder, replacing any earlier schedule for the same reminder. */
    public void schedule(Reminder reminder) {
        long delayMs = Math.max(0, reminder.getTime() - clock.millis());
        ScheduledFuture<?> future = executor.schedule(() -> sendIfStillPending(reminder, false), delayMs, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> previous = pending.put(reminder.getUuid(), future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public void cancel(UUID reminderId) {
        ScheduledFuture<?> future = pending.remove(reminderId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * Deletes the reminder and sends it only if this call was the one that deleted it, so it's sent
     * at most once even when its scheduled run and the startup catch-up both reach it.
     */
    public void sendIfStillPending(Reminder reminder, boolean late) {
        cancel(reminder.getUuid());

        boolean deleted;
        try {
            deleted = store.delete(reminder.getUuid());
        } catch (RuntimeException exception) {
            // Still stored, so the next startup sends it late rather than it being lost
            log.error("Couldn't delete reminder '{}' before sending it; it will be retried on the next startup", reminder.getUuid(), exception);
            return;
        }

        if (!deleted) {
            log.info("Reminder '{}' was already sent or removed, skipping", reminder.getUuid());
            return;
        }

        sender.sendDue(reminder, late);
    }

    /**
     * Loads every stored reminder, sends the ones that came due while the bot was offline and
     * schedules the rest.
     */
    public CompletableFuture<Void> restoreAll() {
        return store.loadAll().thenAccept(this::restore);
    }

    private void restore(List<Reminder> reminders) {
        long now = clock.millis();
        int overdue = 0;
        for (Reminder reminder : reminders) {
            if (reminder.getTime() < now) {
                overdue++;
                sendIfStillPending(reminder, true);
            } else {
                schedule(reminder);
            }
        }
        log.info("Restored {} reminders ({} overdue and sent now)", reminders.size(), overdue);
    }

    int pendingCount() {
        return pending.size();
    }
}
