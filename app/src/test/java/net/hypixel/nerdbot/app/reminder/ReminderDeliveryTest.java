package net.hypixel.nerdbot.app.reminder;

import com.mongodb.client.result.DeleteResult;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderDeliveryTest {

    private final List<String> deletedIds = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();

    @Test
    void overdueReminderIsDeletedBeforeItIsSent() {
        Reminder reminder = reminder();

        ReminderDispatcher.sendIfStillPending(reminder, true, deleting(1), this::recordSend);

        assertEquals(List.of(reminder.getUuid().toString()), deletedIds);
        assertEquals(List.of("late"), sent);
    }

    @Test
    void onTimeReminderIsSentAsNotLate() {
        ReminderDispatcher.sendIfStillPending(reminder(), false, deleting(1), this::recordSend);

        assertEquals(List.of("on time"), sent);
    }

    @Test
    void reminderThatWasAlreadyDeletedIsNotSentAgain() {
        ReminderDispatcher.sendIfStillPending(reminder(), true, deleting(0), this::recordSend);

        assertTrue(sent.isEmpty());
    }

    @Test
    void unacknowledgedDeleteDoesNotSend() {
        ReminderDispatcher.sendIfStillPending(reminder(), true, id -> DeleteResult.unacknowledged(), this::recordSend);

        assertTrue(sent.isEmpty());
    }

    @Test
    void nullDeleteResultDoesNotSend() {
        ReminderDispatcher.sendIfStillPending(reminder(), true, id -> null, this::recordSend);

        assertTrue(sent.isEmpty());
    }

    @Test
    void sendingCancelsAnyScheduledTimer() {
        Reminder reminder = reminder();
        reminder.assignTimer(new Timer(true));

        ReminderDispatcher.sendIfStillPending(reminder, false, deleting(1), this::recordSend);

        assertNull(reminder.getTimer());
    }

    private Function<String, DeleteResult> deleting(long deletedCount) {
        return id -> {
            deletedIds.add(id);
            return DeleteResult.acknowledged(deletedCount);
        };
    }

    private void recordSend(Reminder reminder, boolean late) {
        sent.add(late ? "late" : "on time");
    }

    private static Reminder reminder() {
        return new Reminder("water the plants", System.currentTimeMillis() - 60_000, "channel", "user");
    }
}
