package net.hypixel.nerdbot.app.reminder;

import com.mongodb.client.result.DeleteResult;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import net.hypixel.nerdbot.marmalade.storage.database.repository.ReminderRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;
import java.util.function.Function;

@UtilityClass
@Slf4j
public class ReminderDispatcher {

    public void schedule(Reminder reminder) {
        Timer timer = new Timer();
        reminder.assignTimer(timer);
        timer.schedule(new ReminderTask(reminder), reminder.getTimeAsDate());
    }

    public void cancel(Reminder reminder) {
        reminder.cancelTimer();
    }

    public void sendIfStillPending(Reminder reminder, boolean late) {
        ReminderRepository reminderRepository = DiscordBotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
        sendIfStillPending(reminder, late, reminderRepository::deleteFromDatabase, ReminderDispatcher::dispatch);
    }

    /**
     * Deleting first and only sending when the delete actually removed the document means a
     * reminder is sent at most once, even if the timer and the startup catch-up both fire for it.
     */
    static void sendIfStillPending(Reminder reminder, boolean late, Function<String, DeleteResult> delete, BiConsumer<Reminder, Boolean> send) {
        DeleteResult result = delete.apply(reminder.getUuid().toString());

        if (result == null || !result.wasAcknowledged() || result.getDeletedCount() == 0) {
            log.info("Reminder '{}' was already sent or removed, skipping", reminder.getUuid());
            return;
        }

        reminder.cancelTimer();
        send.accept(reminder, late);
    }

    private void dispatch(Reminder reminder, boolean late) {
        DiscordBotEnvironment.getBot().getJDA().retrieveUserById(reminder.getUserId()).queue(
            user -> sendDirectMessage(user, reminder, late),
            failure -> log.warn("Couldn't find user with ID '{}' to send reminder {}", reminder.getUserId(), reminder.getUuid(), failure)
        );
    }

    private void sendDirectMessage(User user, Reminder reminder, boolean late) {
        String timestamp = DiscordTimestamp.toLongDateTime(reminder.getTime());
        String message = late
            ? user.getAsMention() + ", while I was offline, you asked me to remind you at " + timestamp + " about: "
            : user.getAsMention() + ", you asked me to remind you at " + timestamp + " about: ";

        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(message)
            .addEmbeds(new EmbedBuilder().setDescription(reminder.getDescription()).build())
            .queue((success) -> log.info("Sent reminder '{}' message to user {}", reminder.getUuid(), reminder.getUserId()),
                (failure) -> log.warn("Couldn't send reminder message to user: {} (reminder: {})", reminder.getUserId(), reminder.getUuid())));
    }

    private static class ReminderTask extends TimerTask {

        private final Reminder reminder;

        private ReminderTask(Reminder reminder) {
            this.reminder = reminder;
        }

        @Override
        public void run() {
            ReminderDispatcher.sendIfStillPending(reminder, false);
        }
    }
}
