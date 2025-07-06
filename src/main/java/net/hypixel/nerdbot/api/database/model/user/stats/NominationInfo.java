package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.util.Date;
import java.util.Optional;

@Getter
@Setter
@ToString
public class NominationInfo {

    private int totalNominations = 0;
    private Date lastNominationDate = null;
    private int totalInactivityWarnings = 0;
    private Date lastInactivityWarningDate = null;
    private int totalRoleRestrictedInactivityWarnings = 0;
    private Date lastRoleRestrictedInactivityWarningDate = null;

    public void increaseNominations() {
        this.totalNominations++;
        this.lastNominationDate = new Date();
    }

    public String getLastNominationDateString() {
        if (lastNominationDate == null) {
            return "Never";
        }

        return DiscordTimestamp.toShortDate(lastNominationDate.getTime());
    }

    public Optional<Date> getLastNominationDate() {
        return Optional.ofNullable(lastNominationDate);
    }

    public void increaseInactivityWarnings() {
        this.totalInactivityWarnings++;
        this.lastInactivityWarningDate = new Date();
    }

    public String getLastInactivityWarningDateString() {
        if (lastInactivityWarningDate == null) {
            return "Never";
        }

        return DiscordTimestamp.toShortDate(lastInactivityWarningDate.getTime());
    }

    public Optional<Date> getLastInactivityWarningDate() {
        return Optional.ofNullable(lastInactivityWarningDate);
    }

    public void increaseRoleRestrictedInactivityWarnings() {
        this.totalRoleRestrictedInactivityWarnings++;
        this.lastRoleRestrictedInactivityWarningDate = new Date();
    }

    public String getLastRoleRestrictedInactivityWarningDateString() {
        if (lastRoleRestrictedInactivityWarningDate == null) {
            return "Never";
        }

        return DiscordTimestamp.toShortDate(lastRoleRestrictedInactivityWarningDate.getTime());
    }

    public Optional<Date> getLastRoleRestrictedInactivityWarningDate() {
        return Optional.ofNullable(lastRoleRestrictedInactivityWarningDate);
    }
}
