package net.hypixel.nerdbot.api.database.model.reminder;

import com.google.gson.annotations.JsonAdapter;
import com.mongodb.client.result.DeleteResult;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.repository.ReminderRepository;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import net.hypixel.nerdbot.util.json.adapter.EpochMillisAdapter;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Date;

@Slf4j
@Getter
@Setter
@ToString
public class Reminder {

    private static final String COLLECTION_NAME = "reminders";

    private UUID uuid;
    @BsonIgnore
    private transient Timer timer;
    private String description;
    private String channelId;
    private String userId;
    @JsonAdapter(EpochMillisAdapter.class)
    private long time;

    public Reminder() {
    }

    public Reminder(String description, Date time, String channelId, String userId) {
        this.uuid = UUID.randomUUID();
        this.description = description;
        this.channelId = channelId;
        this.userId = userId;
        this.time = time.getTime();
    }

    public Reminder(String description, long time, String channelId, String userId) {
        this.uuid = UUID.randomUUID();
        this.description = description;
        this.channelId = channelId;
        this.userId = userId;
        this.time = time;
    }

    public Date getTimeAsDate() {
        return new Date(time);
    }

    public void schedule() {
        timer = new Timer();
        timer.schedule(new ReminderTask(), getTimeAsDate());
    }

    public void sendReminder(boolean late) {
        ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
        User user = NerdBotApp.getBot().getJDA().getUserById(userId);
        String message;
        String timestamp = DiscordTimestamp.toLongDateTime(time);

        if (user == null) {
            log.error("Couldn't find user with ID '" + userId + "' to send reminder " + uuid + "!");
            reminderRepository.deleteFromDatabase(uuid.toString());
            return;
        }

        if (late) {
            message = user.getAsMention() + ", while I was offline, you asked me to remind you at " + timestamp + " about: ";
        } else {
            message = user.getAsMention() + ", you asked me to remind you at " + timestamp + " about: ";
        }

        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(message)
            .addEmbeds(new EmbedBuilder().setDescription(description).build())
            .queue((success) -> {
                log.info("Sent reminder '" + uuid + "' message to user " + userId);
            }, (failure) -> {
                log.error("Couldn't send reminder message to user: " + userId + " (reminder: " + uuid + ")");
            })
        );
    }

    class ReminderTask extends TimerTask {
        public void run() {
            ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
            DeleteResult result = reminderRepository.deleteFromDatabase(uuid.toString());

            if (result != null && result.wasAcknowledged() && result.getDeletedCount() > 0) {
                timer.cancel();
                sendReminder(false);
                log.info("Reminder '" + uuid + "' successfully deleted (result: " + result + ")");
            }
        }
    }
}
