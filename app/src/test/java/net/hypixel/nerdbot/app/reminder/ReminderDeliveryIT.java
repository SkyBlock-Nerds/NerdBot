package net.hypixel.nerdbot.app.reminder;

import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import net.hypixel.nerdbot.marmalade.storage.database.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The delete-then-send rule against a real MongoDB delete. */
class ReminderDeliveryIT {

    private ReminderRepository repository;
    private final AtomicInteger sends = new AtomicInteger();

    @BeforeEach
    void setUp() {
        repository = new ReminderRepository(MongoTestDatabase.client(), MongoTestDatabase.freshDatabaseName());
    }

    private Reminder savedReminder() {
        Reminder reminder = new Reminder("water the plants", System.currentTimeMillis() - 60_000, "channel", "500000000000000001");
        repository.saveToDatabase(reminder);
        return reminder;
    }

    @Test
    void anOverdueReminderIsSentOnceAndRemoved() {
        Reminder reminder = savedReminder();

        ReminderDispatcher.sendIfStillPending(reminder, true, repository::deleteFromDatabase, (r, late) -> sends.incrementAndGet());
        ReminderDispatcher.sendIfStillPending(reminder, true, repository::deleteFromDatabase, (r, late) -> sends.incrementAndGet());

        assertEquals(1, sends.get());
        assertEquals(List.of(), repository.getAllDocuments());
    }

    @Test
    void theTimerAndTheStartupCatchUpRacingStillSendOnce() throws InterruptedException {
        Reminder reminder = savedReminder();
        CountDownLatch start = new CountDownLatch(1);

        Runnable deliver = () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ReminderDispatcher.sendIfStillPending(reminder, false, repository::deleteFromDatabase, (r, late) -> sends.incrementAndGet());
        };
        Thread timer = new Thread(deliver);
        Thread startup = new Thread(deliver);
        timer.start();
        startup.start();
        start.countDown();
        timer.join(10_000);
        startup.join(10_000);

        assertEquals(1, sends.get());
        assertTrue(repository.getAllDocuments().isEmpty());
    }
}
