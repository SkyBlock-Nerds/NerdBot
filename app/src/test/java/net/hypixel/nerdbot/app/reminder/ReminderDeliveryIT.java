package net.hypixel.nerdbot.app.reminder;

import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import net.hypixel.nerdbot.app.testsupport.RecordingReminderSender;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import net.hypixel.nerdbot.marmalade.storage.database.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reminder storage and the delete-then-send rule against a real MongoDB. */
class ReminderDeliveryIT {

    private static final String USER = "500000000000000001";

    private String database;
    private ReminderRepository repository;
    private RepositoryReminderStore store;
    private final RecordingReminderSender sender = new RecordingReminderSender();
    private final ManualScheduledExecutor executor = new ManualScheduledExecutor();
    private ReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        database = MongoTestDatabase.freshDatabaseName();
        repository = new ReminderRepository(MongoTestDatabase.client(), database);
        store = new RepositoryReminderStore(() -> repository);
        scheduler = new ReminderScheduler(executor, store, sender, Clock.systemUTC());
    }

    private Reminder savedReminder(Duration fromNow) {
        Reminder reminder = new Reminder("water the plants", System.currentTimeMillis() + fromNow.toMillis(), "channel", USER);
        assertTrue(store.save(reminder).join());
        return reminder;
    }

    @Test
    void anOverdueReminderIsSentOnceAndRemoved() {
        Reminder reminder = savedReminder(Duration.ofMinutes(-1));

        scheduler.sendIfStillPending(reminder, true);
        scheduler.sendIfStillPending(reminder, true);

        assertEquals(1, sender.sent.size());
        assertEquals(List.of(), repository.getAllDocuments());
    }

    @Test
    void theScheduledRunAndTheStartupCatchUpRacingStillSendOnce() throws InterruptedException {
        Reminder reminder = savedReminder(Duration.ofMinutes(-1));
        CountDownLatch start = new CountDownLatch(1);

        Runnable deliver = () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler.sendIfStillPending(reminder, false);
        };
        Thread scheduled = new Thread(deliver);
        Thread startup = new Thread(deliver);
        scheduled.start();
        startup.start();
        start.countDown();
        scheduled.join(10_000);
        startup.join(10_000);

        assertEquals(1, sender.sent.size());
        assertTrue(repository.getAllDocuments().isEmpty());
    }

    @Test
    void restoreAfterARestartReadsWhatWasSaved() {
        Reminder overdue = savedReminder(Duration.ofHours(-1));
        Reminder upcoming = savedReminder(Duration.ofHours(1));

        ReminderRepository restarted = new ReminderRepository(MongoTestDatabase.client(), database);
        RepositoryReminderStore restartedStore = new RepositoryReminderStore(() -> restarted);
        new ReminderScheduler(executor, restartedStore, sender, Clock.systemUTC()).restoreAll().join();

        assertEquals(List.of(new RecordingReminderSender.Due(overdue.getUuid(), true)), sender.sent);
        assertEquals(1, executor.pendingDelays().size());
        assertEquals(List.of(upcoming.getUuid()), restartedStore.findByUser(USER).stream().map(Reminder::getUuid).toList());
    }

    @Test
    void findByUserIsSoonestFirstAndOnlyTheirs() {
        Reminder later = savedReminder(Duration.ofDays(2));
        Reminder sooner = savedReminder(Duration.ofHours(1));
        Reminder someoneElses = new Reminder("not mine", System.currentTimeMillis() + 1000, "channel", "500000000000000002");
        assertTrue(store.save(someoneElses).join());

        assertEquals(List.of(sooner.getUuid(), later.getUuid()), store.findByUser(USER).stream().map(Reminder::getUuid).toList());
    }

    @Test
    void deleteIsTrueOnlyForTheCallThatRemovedIt() {
        Reminder reminder = savedReminder(Duration.ofHours(1));

        assertTrue(store.delete(reminder.getUuid()));
        assertFalse(store.delete(reminder.getUuid()));
        assertFalse(store.delete(UUID.randomUUID()));
        assertTrue(store.findById(reminder.getUuid()).isEmpty());
    }
}
