package net.hypixel.nerdbot.app.reminder;

import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;

/** Direct messages about reminders. Failures are logged, never thrown. */
public interface ReminderSender {

    /**
     * @param late whether the reminder came due while the bot was offline
     */
    void sendDue(Reminder reminder, boolean late);

    void confirmCreated(Reminder reminder);
}
