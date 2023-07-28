package net.hypixel.nerdbot.api.database.model.user.stats;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.NerdBotApp;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
public class MojangProfile {

    @SerializedName("id")
    private UUID uniqueId;
    @SerializedName("name")
    private String username;
    private String errorMessage;
    private long lastUpdated;

    public MojangProfile() {
        this.lastUpdated = System.currentTimeMillis();
    }

    @BsonIgnore
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(this.errorMessage);
    }

    public boolean requiresCacheUpdate() {
        return Duration.of(System.currentTimeMillis(), ChronoUnit.MILLIS)
            .minus(NerdBotApp.getBot().getConfig().getMojangUsernameCacheTTL(), ChronoUnit.HOURS)
            .toMillis() > this.lastUpdated;
    }
}
