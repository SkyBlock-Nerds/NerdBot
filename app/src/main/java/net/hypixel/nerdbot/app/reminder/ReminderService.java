package net.hypixel.nerdbot.app.reminder;

import jakarta.inject.Inject;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Creating, listing and deleting a user's reminders. The commands keep the Discord panels. */
public class ReminderService {

    public static final int MAX_DESCRIPTION_LENGTH = 4_000;

    public sealed interface CreateResult {

        record Created(Reminder reminder) implements CreateResult {
        }

        /** The input can't make a reminder; {@code reason} is shown to the user as is. */
        record Rejected(String reason) implements CreateResult {
        }
    }

    public enum DeleteResult {
        DELETED,
        /** It doesn't exist, belongs to someone else, or was sent or deleted in the meantime. */
        NOT_FOUND
    }

    private final ReminderStore store;
    private final ReminderScheduler scheduler;
    private final ReminderSender sender;
    private final Clock clock;

    @Inject
    public ReminderService(ReminderStore store, ReminderScheduler scheduler, ReminderSender sender, Clock clock) {
        this.store = store;
        this.scheduler = scheduler;
        this.sender = sender;
        this.clock = clock;
    }

    /**
     * Validates the input, then saves, schedules and confirms the reminder by DM.
     *
     * @return a rejection for bad input; fails if the database didn't acknowledge the write
     */
    public CompletableFuture<CreateResult> create(String userId, String channelId, String timeInput, String description) {
        if (description == null || description.isBlank()) {
            return rejected("❌ You need to provide a description for the reminder!");
        }

        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            return rejected("❌ Description is too long! Please keep it under 4,000 characters.");
        }

        Optional<Instant> time = ReminderTimes.parse(timeInput, clock);
        if (time.isEmpty()) {
            return rejected("❌ I could not parse that date/time format! Please try again.");
        }

        if (!time.get().isAfter(clock.instant())) {
            return rejected("❌ You cannot set a reminder in the past!");
        }

        Reminder reminder = new Reminder(description, time.get().toEpochMilli(), channelId, userId);
        return store.save(reminder).thenApply(acknowledged -> {
            if (!acknowledged) {
                throw new IllegalStateException("Database did not acknowledge reminder " + reminder.getUuid());
            }

            scheduler.schedule(reminder);
            sender.confirmCreated(reminder);
            return new CreateResult.Created(reminder);
        });
    }

    /** The user's reminders, soonest first. */
    public List<Reminder> remindersFor(String userId) {
        return store.findByUser(userId);
    }

    /** Empty when the id isn't a reminder id, doesn't exist, or belongs to someone else. */
    public Optional<Reminder> find(String userId, String reminderId) {
        return parseId(reminderId)
            .flatMap(store::findById)
            .filter(reminder -> userId.equalsIgnoreCase(reminder.getUserId()));
    }

    public DeleteResult delete(String userId, String reminderId) {
        Optional<Reminder> reminder = find(userId, reminderId);
        if (reminder.isEmpty() || !store.delete(reminder.get().getUuid())) {
            return DeleteResult.NOT_FOUND;
        }

        scheduler.cancel(reminder.get().getUuid());
        return DeleteResult.DELETED;
    }

    private static Optional<UUID> parseId(String reminderId) {
        if (reminderId == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(reminderId));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    private static CompletableFuture<CreateResult> rejected(String reason) {
        return CompletableFuture.completedFuture(new CreateResult.Rejected(reason));
    }
}
