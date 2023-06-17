package net.hypixel.nerdbot.api.database.model.reminder;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.jetbrains.annotations.Nullable;

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
    private Timer timer;
    private String description;
    private String channelId;
    private String userId;
    private Date time;

    public Reminder() {
    }

    public Reminder(String description, Date time, String channelId, String userId) {
        this.uuid = UUID.randomUUID();
        this.description = description;
        this.channelId = channelId;
        this.userId = userId;
        this.time = time;
    }

    class ReminderTask extends TimerTask {
        public void run() {
            DeleteResult result = delete();

            if (result != null && result.wasAcknowledged() && result.getDeletedCount() > 0) {
                timer.cancel();
                sendReminder();
            }
        }
    }

    public void schedule() {
        timer = new Timer();
        timer.schedule(new ReminderTask(), time);
    }

    public void sendReminder() {
        TextChannel channel = NerdBotApp.getBot().getJDA().getTextChannelById(channelId);
        User user = NerdBotApp.getBot().getJDA().getUserById(userId);

        if (channel != null && user != null) {
            channel.sendMessage(user.getAsMention() + ", you asked me to remind you at " + new DiscordTimestamp(time.getTime()).toLongDateTime() + " about: ")
                    .addEmbeds(new EmbedBuilder().setDescription(description).build())
                    .queue();
        } else if (channel == null && user != null) {
            user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("Reminder: " + description).queue());
        } else {
            log.error("Couldn't send reminder to user '" + userId + "' in channel '" + channelId + "'!");
        }

        DeleteResult result = delete();
        if (result != null && result.wasAcknowledged() && result.getDeletedCount() > 0) {
            log.info("Reminder deleted from database: " + uuid);
        }
    }

    @Nullable
    public InsertOneResult save() {
        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            log.error("Database is not connected!");
            return null;
        }

        return NerdBotApp.getBot().getDatabase().insertDocument(NerdBotApp.getBot().getDatabase().getCollection(COLLECTION_NAME, Reminder.class), this);
    }

    @Nullable
    public DeleteResult delete() {
        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            log.error("Database is not connected!");
            return null;
        }

        Reminder reminder = NerdBotApp.getBot().getDatabase().findDocument(NerdBotApp.getBot().getDatabase().getCollection(COLLECTION_NAME, Reminder.class), "uuid", uuid)
                .limit(1)
                .first();

        if (reminder == null) {
            return null;
        }

        return NerdBotApp.getBot().getDatabase().deleteDocument(NerdBotApp.getBot().getDatabase().getCollection(COLLECTION_NAME, Reminder.class), "uuid", uuid);
    }
}
