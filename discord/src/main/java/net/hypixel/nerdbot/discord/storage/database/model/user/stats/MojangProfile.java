package net.hypixel.nerdbot.discord.storage.database.model.user.stats;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Getter
@Setter
@ToString
public class MojangProfile {

    @SerializedName(value = "uuid", alternate = {"id"})
    private UUID uniqueId;
    @SerializedName(value = "name", alternate = {"username"})
    private String username;
    private String errorMessage;
    private long lastUpdated;

    public MojangProfile() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean requiresCacheUpdate(long cacheTTLHours) {
        return Duration.of(System.currentTimeMillis(), ChronoUnit.MILLIS)
            .minus(cacheTTLHours, ChronoUnit.HOURS)
            .toMillis() > this.lastUpdated;
    }
}