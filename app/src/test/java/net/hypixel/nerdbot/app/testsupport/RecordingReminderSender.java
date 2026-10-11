package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.reminder.ReminderSender;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingReminderSender implements ReminderSender {

    public record Due(UUID reminderId, boolean late) {
    }

    public final List<Due> sent = new CopyOnWriteArrayList<>();
    public final List<UUID> confirmed = new CopyOnWriteArrayList<>();

    @Override
    public void sendDue(Reminder reminder, boolean late) {
        sent.add(new Due(reminder.getUuid(), late));
    }

    @Override
    public void confirmCreated(Reminder reminder) {
        confirmed.add(reminder.getUuid());
    }
}
