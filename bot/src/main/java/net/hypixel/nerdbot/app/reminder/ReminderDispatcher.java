package net.hypixel.nerdbot.app.reminder;

import com.mongodb.client.result.DeleteResult;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.core.DiscordTimestamp;
import net.hypixel.nerdbot.discord.storage.database.model.reminder.Reminder;
import net.hypixel.nerdbot.discord.storage.database.repository.ReminderRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.Timer;
import java.util.TimerTask;

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

    public void dispatch(Reminder reminder, boolean late) {
        ReminderRepository reminderRepository = DiscordBotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
        User user = DiscordBotEnvironment.getBot().getJDA().getUserById(reminder.getUserId());
        String timestamp = DiscordTimestamp.toLongDateTime(reminder.getTime());

        if (user == null) {
            log.error("Couldn't find user with ID '{}' to send reminder {}!", reminder.getUserId(), reminder.getUuid());
            reminderRepository.deleteFromDatabase(reminder.getUuid().toString());
            return;
        }

        String message = late
            ? user.getAsMention() + ", while I was offline, you asked me to remind you at " + timestamp + " about: "
            : user.getAsMention() + ", you asked me to remind you at " + timestamp + " about: ";

        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(message)
            .addEmbeds(new EmbedBuilder().setDescription(reminder.getDescription()).build())
            .queue((success) -> log.info("Sent reminder '{}' message to user {}", reminder.getUuid(), reminder.getUserId()),
                (failure) -> log.error("Couldn't send reminder message to user: {} (reminder: {})", reminder.getUserId(), reminder.getUuid())));
    }

    private static class ReminderTask extends TimerTask {

        private final Reminder reminder;

        private ReminderTask(Reminder reminder) {
            this.reminder = reminder;
        }

        @Override
        public void run() {
            ReminderRepository reminderRepository = DiscordBotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
            DeleteResult result = reminderRepository.deleteFromDatabase(reminder.getUuid().toString());

            if (result != null && result.wasAcknowledged() && result.getDeletedCount() > 0) {
                reminder.cancelTimer();
                ReminderDispatcher.dispatch(reminder, false);
                log.info("Reminder '{}' successfully deleted (result: {})", reminder.getUuid(), result);
            }
        }
    }
}