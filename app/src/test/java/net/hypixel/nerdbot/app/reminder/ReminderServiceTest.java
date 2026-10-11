package net.hypixel.nerdbot.app.reminder;

import net.hypixel.nerdbot.app.reminder.ReminderService.CreateResult;
import net.hypixel.nerdbot.app.reminder.ReminderService.DeleteResult;
import net.hypixel.nerdbot.app.testsupport.FakeReminderStore;
import net.hypixel.nerdbot.app.testsupport.RecordingReminderSender;
import net.hypixel.nerdbot.app.testsupport.WriteOutcome;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String USER = "100";
    private static final String OTHER_USER = "101";

    private final ManualScheduledExecutor executor = new ManualScheduledExecutor();
    private final FakeReminderStore store = new FakeReminderStore();
    private final RecordingReminderSender sender = new RecordingReminderSender();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ReminderScheduler scheduler = new ReminderScheduler(executor, store, sender, clock);
    private final ReminderService service = new ReminderService(store, scheduler, sender, clock);

    @Test
    void createSavesSchedulesAndConfirms() {
        CreateResult result = service.create(USER, "channel", "2h", "water the plants").join();

        Reminder reminder = assertInstanceOf(CreateResult.Created.class, result).reminder();
        assertEquals(USER, reminder.getUserId());
        assertEquals("channel", reminder.getChannelId());
        assertEquals("water the plants", reminder.getDescription());
        assertEquals(NOW.plus(Duration.ofHours(2)).toEpochMilli(), reminder.getTime());
        assertTrue(store.contains(reminder.getUuid()));
        assertEquals(List.of(Duration.ofHours(2)), executor.pendingDelays());
        assertEquals(List.of(reminder.getUuid()), sender.confirmed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankDescriptionIsRejected(String description) {
        assertRejected("❌ You need to provide a description for the reminder!", service.create(USER, "channel", "2h", description).join());
    }

    @Test
    void descriptionAtTheLimitIsAccepted() {
        String description = "x".repeat(ReminderService.MAX_DESCRIPTION_LENGTH);

        assertInstanceOf(CreateResult.Created.class, service.create(USER, "channel", "2h", description).join());
    }

    @Test
    void descriptionOverTheLimitIsRejected() {
        String description = "x".repeat(ReminderService.MAX_DESCRIPTION_LENGTH + 1);

        assertRejected("❌ Description is too long! Please keep it under 4,000 characters.", service.create(USER, "channel", "2h", description).join());
    }

    @Test
    void unparseableTimeIsRejected() {
        assertRejected("❌ I could not parse that date/time format! Please try again.", service.create(USER, "channel", "whenever you like", "x").join());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "1700000000000"})
    void timesThatAreNotInTheFutureAreRejected(String time) {
        assertRejected("❌ You cannot set a reminder in the past!", service.create(USER, "channel", time, "x").join());
    }

    @Test
    void rejectedInputSavesNothing() {
        service.create(USER, "channel", "0s", "x").join();

        assertEquals(0, store.size());
        assertTrue(sender.confirmed.isEmpty());
        assertTrue(executor.pendingDelays().isEmpty());
    }

    @Test
    void unacknowledgedSaveFailsAndSchedulesNothing() {
        store.savesWill(WriteOutcome.UNACKNOWLEDGED);

        CompletionException thrown = assertThrows(CompletionException.class, () -> service.create(USER, "channel", "2h", "x").join());

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertTrue(executor.pendingDelays().isEmpty());
        assertTrue(sender.confirmed.isEmpty());
    }

    @Test
    void failedSaveFailsAndSchedulesNothing() {
        store.savesWill(WriteOutcome.FAILED);

        assertThrows(CompletionException.class, () -> service.create(USER, "channel", "2h", "x").join());
        assertTrue(executor.pendingDelays().isEmpty());
    }

    @Test
    void remindersForListsOnlyTheUsersRemindersSoonestFirst() {
        Reminder later = seed(USER, Duration.ofDays(2));
        Reminder sooner = seed(USER, Duration.ofHours(1));
        seed(OTHER_USER, Duration.ofMinutes(1));

        assertEquals(List.of(sooner, later), service.remindersFor(USER));
    }

    @Test
    void findOnlyReturnsTheUsersOwnReminder() {
        Reminder reminder = seed(USER, Duration.ofHours(1));

        assertEquals(reminder, service.find(USER, reminder.getUuid().toString()).orElseThrow());
        assertTrue(service.find(OTHER_USER, reminder.getUuid().toString()).isEmpty());
    }

    @Test
    void findTreatsBadIdsAsNotFound() {
        assertTrue(service.find(USER, "not-a-uuid").isEmpty());
        assertTrue(service.find(USER, null).isEmpty());
        assertTrue(service.find(USER, "0f8fad5b-d9cb-469f-a165-70867728950e").isEmpty());
    }

    @Test
    void deleteRemovesAndUnschedulesTheReminder() {
        Reminder reminder = service.create(USER, "channel", "2h", "x").thenApply(result -> ((CreateResult.Created) result).reminder()).join();

        assertEquals(DeleteResult.DELETED, service.delete(USER, reminder.getUuid().toString()));

        assertTrue(store.findById(reminder.getUuid()).isEmpty());
        assertTrue(executor.pendingDelays().isEmpty());
        executor.advance(Duration.ofHours(3));
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void someoneElseCannotDeleteIt() {
        Reminder reminder = seed(USER, Duration.ofHours(1));

        assertEquals(DeleteResult.NOT_FOUND, service.delete(OTHER_USER, reminder.getUuid().toString()));
        assertTrue(store.contains(reminder.getUuid()));
    }

    @Test
    void deletingTwiceIsNotFoundTheSecondTime() {
        Reminder reminder = seed(USER, Duration.ofHours(1));

        assertEquals(DeleteResult.DELETED, service.delete(USER, reminder.getUuid().toString()));
        assertEquals(DeleteResult.NOT_FOUND, service.delete(USER, reminder.getUuid().toString()));
    }

    private Reminder seed(String userId, Duration fromNow) {
        Reminder reminder = new Reminder("x", NOW.plus(fromNow).toEpochMilli(), "channel", userId);
        store.seed(reminder);
        return reminder;
    }

    private static void assertRejected(String reason, CreateResult result) {
        assertEquals(reason, assertInstanceOf(CreateResult.Rejected.class, result).reason());
    }
}
