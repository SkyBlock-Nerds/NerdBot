package net.hypixel.nerdbot.discord.storage.database.model.user.stats;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.core.DiscordTimestamp;
import net.hypixel.nerdbot.core.gson.adapter.EpochMillisAdapter;

import java.util.Optional;

@Getter
@Setter
@ToString
public class NominationInfo {

    private int totalNominations = 0;
    @JsonAdapter(EpochMillisAdapter.class)
    @SerializedName(value = "lastNominationTimestamp", alternate = {"lastNominationDate"})
    private Long lastNominationTimestamp = null;
    private int totalInactivityWarnings = 0;
    @JsonAdapter(EpochMillisAdapter.class)
    @SerializedName(value = "lastInactivityWarningTimestamp", alternate = {"lastInactivityWarningDate"})
    private Long lastInactivityWarningTimestamp = null;
    private int totalRoleRestrictedInactivityWarnings = 0;
    @JsonAdapter(EpochMillisAdapter.class)
    @SerializedName(value = "lastRoleRestrictedInactivityWarningTimestamp", alternate = {"lastRoleRestrictedInactivityWarningDate"})
    private Long lastRoleRestrictedInactivityWarningTimestamp = null;

    public void increaseNominations() {
        this.totalNominations++;
        this.lastNominationTimestamp = System.currentTimeMillis();
    }

    public String getLastNominationDateString() {
        if (lastNominationTimestamp == null) {
            return "Never";
        }

        return DiscordTimestamp.toShortDate(lastNominationTimestamp);
    }

    public Optional<Long> getLastNominationTimestamp() {
        return Optional.ofNullable(lastNominationTimestamp);
    }

    public void increaseInactivityWarnings() {
        this.totalInactivityWarnings++;
        this.lastInactivityWarningTimestamp = System.currentTimeMillis();
    }

    public String getLastInactivityWarningDateString() {
        if (lastInactivityWarningTimestamp == null) {
            return "Never";
        }

        return DiscordTimestamp.toShortDate(lastInactivityWarningTimestamp);
    }

    public Optional<Long> getLastInactivityWarningTimestamp() {
        return Optional.ofNullable(lastInactivityWarningTimestamp);
    }

    public void increaseRoleRestrictedInactivityWarnings() {
        this.totalRoleRestrictedInactivityWarnings++;
        this.lastRoleRestrictedInactivityWarningTimestamp = System.currentTimeMillis();
    }

    public String getLastRoleRestrictedInactivityWarningDateString() {
        if (lastRoleRestrictedInactivityWarningTimestamp == null) {
            return "Never";
        }

        return DiscordTimestamp.toShortDate(lastRoleRestrictedInactivityWarningTimestamp);
    }

    public Optional<Long> getLastRoleRestrictedInactivityWarningTimestamp() {
        return Optional.ofNullable(lastRoleRestrictedInactivityWarningTimestamp);
    }
}