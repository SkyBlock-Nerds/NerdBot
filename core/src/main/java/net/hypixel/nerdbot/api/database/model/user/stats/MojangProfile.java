package net.hypixel.nerdbot.api.database.model.user.stats;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.BotEnvironment;

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

    public boolean requiresCacheUpdate() {
        return Duration.of(System.currentTimeMillis(), ChronoUnit.MILLIS)
            .minus(BotEnvironment.getBot().getConfig().getMojangUsernameCacheTTL(), ChronoUnit.HOURS)
            .toMillis() > this.lastUpdated;
    }
}