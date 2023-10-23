package net.hypixel.nerdbot.api.database.model.reminder;

import com.mongodb.client.result.DeleteResult;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.repository.ReminderRepository;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

@Log4j2
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
    private Date time;
    private boolean sendPublicly;

    public Reminder() {
    }

    public Reminder(String description, Date time, String channelId, String userId, boolean sendPublicly) {
        this.uuid = UUID.randomUUID();
        this.description = description;
        this.channelId = channelId;
        this.userId = userId;
        this.time = time;
        this.sendPublicly = sendPublicly;
    }

    class ReminderTask extends TimerTask {
        public void run() {
            ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
            DeleteResult result = reminderRepository.deleteFromDatabase(uuid.toString());

            if (result != null && result.wasAcknowledged() && result.getDeletedCount() > 0) {
                timer.cancel();
                sendReminder(false);
            }
        }
    }

    public void schedule() {
        timer = new Timer();
        timer.schedule(new ReminderTask(), time);
    }

    public void sendReminder(boolean late) {
        ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
        TextChannel channel = ChannelManager.getChannel(channelId);
        User user = NerdBotApp.getBot().getJDA().getUserById(userId);
        String message;
        String timestamp = new DiscordTimestamp(time.getTime()).toLongDateTime();

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

        if (!sendPublicly) {
            String finalMessage = message;
            user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(finalMessage).addEmbeds(new EmbedBuilder().setDescription(description).build()).queue());
            return;
        }

        if (channel != null) {
            if (late) {
                message = user.getAsMention() + ", while I was offline, you asked me to remind you at " + timestamp + " about: ";
            } else {
                message = user.getAsMention() + ", you asked me to remind you at " + timestamp + " about: ";
            }

            channel.sendMessage(message)
                .addEmbeds(new EmbedBuilder().setDescription(description).build())
                .queue();
        } else if (sendPublicly) {
            user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("Reminder: " + description).queue());
        }

        DeleteResult result = reminderRepository.deleteFromDatabase(uuid.toString());
        if (result == null) {
            log.error("Couldn't delete reminder from database: " + uuid + " (result: null)");
            return;
        }

        if (result.wasAcknowledged() && result.getDeletedCount() > 0) {
            log.info("Reminder deleted from database: " + uuid);
        } else {
            log.error("Couldn't delete reminder from database: " + uuid + " (result: " + result + ")");
        }
    }
}
