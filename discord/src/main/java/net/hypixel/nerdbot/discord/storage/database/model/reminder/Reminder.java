package net.hypixel.nerdbot.discord.storage.database.model.reminder;

import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.core.gson.adapter.EpochMillisAdapter;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Date;
import java.util.Timer;
import java.util.UUID;

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
        this(description, time.getTime(), channelId, userId);
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

    public void assignTimer(Timer timer) {
        this.timer = timer;
    }

    public void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public String getCollectionName() {
        return COLLECTION_NAME;
    }
}