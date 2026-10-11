package net.hypixel.nerdbot.app.reminder;

import net.hypixel.nerdbot.app.testsupport.FakeReminderStore;
import net.hypixel.nerdbot.app.testsupport.RecordingReminderSender;
import net.hypixel.nerdbot.app.testsupport.RecordingReminderSender.Due;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private final ManualScheduledExecutor executor = new ManualScheduledExecutor();
    private final FakeReminderStore store = new FakeReminderStore();
    private final RecordingReminderSender sender = new RecordingReminderSender();
    private final ReminderScheduler scheduler = new ReminderScheduler(executor, store, sender, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sendsTheReminderWhenItComesDue() {
        Reminder reminder = stored(Duration.ofHours(1));

        scheduler.schedule(reminder);
        assertEquals(List.of(Duration.ofHours(1)), executor.pendingDelays());

        executor.advance(Duration.ofMinutes(59));
        assertTrue(sender.sent.isEmpty());

        executor.advance(Duration.ofMinutes(1));
        assertEquals(List.of(new Due(reminder.getUuid(), false)), sender.sent);
        assertFalse(store.contains(reminder.getUuid()));
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void aReminderAlreadyDueRunsStraightAway() {
        Reminder reminder = stored(Duration.ofMinutes(-5));

        scheduler.schedule(reminder);

        assertEquals(List.of(Duration.ZERO), executor.pendingDelays());
    }

    @Test
    void cancelledRemindersAreNotSent() {
        Reminder reminder = stored(Duration.ofHours(1));
        scheduler.schedule(reminder);

        scheduler.cancel(reminder.getUuid());
        executor.advance(Duration.ofHours(2));

        assertTrue(sender.sent.isEmpty());
        assertTrue(executor.pendingDelays().isEmpty());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void cancellingSomethingNotScheduledIsHarmless() {
        scheduler.cancel(stored(Duration.ofHours(1)).getUuid());

        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void schedulingAgainReplacesTheEarlierSchedule() {
        Reminder reminder = stored(Duration.ofHours(1));

        scheduler.schedule(reminder);
        scheduler.schedule(reminder);

        assertEquals(List.of(Duration.ofHours(1)), executor.pendingDelays());
        assertEquals(1, scheduler.pendingCount());

        executor.advance(Duration.ofHours(2));
        assertEquals(1, sender.sent.size());
    }

    @Test
    void aReminderIsSentAtMostOnce() {
        Reminder reminder = stored(Duration.ofMinutes(-1));

        scheduler.sendIfStillPending(reminder, true);
        scheduler.sendIfStillPending(reminder, false);

        assertEquals(List.of(new Due(reminder.getUuid(), true)), sender.sent);
    }

    @Test
    void aReminderDeletedByItsOwnerIsNotSent() {
        Reminder reminder = new Reminder("gone", NOW.toEpochMilli(), "channel", "user");

        scheduler.sendIfStillPending(reminder, false);

        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void aDatabaseErrorLeavesTheReminderStoredForTheNextStartup() {
        Reminder reminder = stored(Duration.ofMinutes(-1));
        store.deletesFail();

        scheduler.sendIfStillPending(reminder, false);

        assertTrue(sender.sent.isEmpty());
        assertTrue(store.contains(reminder.getUuid()));
    }

    @Test
    void restoreSendsOverdueRemindersLateAndSchedulesTheRest() {
        Reminder overdue = stored(Duration.ofHours(-3));
        Reminder upcoming = stored(Duration.ofHours(2));

        scheduler.restoreAll().join();

        assertEquals(List.of(new Due(overdue.getUuid(), true)), sender.sent);
        assertEquals(List.of(Duration.ofHours(2)), executor.pendingDelays());

        executor.advance(Duration.ofHours(2));
        assertEquals(new Due(upcoming.getUuid(), false), sender.sent.getLast());
        assertEquals(0, store.size());
    }

    @Test
    void restoringTwiceDoesNotDoubleSend() {
        stored(Duration.ofHours(-3));
        stored(Duration.ofHours(2));

        scheduler.restoreAll().join();
        scheduler.restoreAll().join();
        executor.advance(Duration.ofHours(3));

        assertEquals(2, sender.sent.size());
    }

    private Reminder stored(Duration fromNow) {
        Reminder reminder = new Reminder("water the plants", NOW.plus(fromNow).toEpochMilli(), "channel", "user");
        store.seed(reminder);
        return reminder;
    }
}
