package net.hypixel.nerdbot.api.database;

import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class DiscordUser {

    private String discordId;
    private Date lastKnownActivityDate;
    private List<String> agrees, disagrees;

    public DiscordUser() {
    }

    public DiscordUser(String discordId, Date lastKnownActivityDate, List<String> agrees, List<String> disagrees) {
        this.discordId = discordId;
        this.lastKnownActivityDate = lastKnownActivityDate;
        this.agrees = agrees;
        this.disagrees = disagrees;
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    @Nullable
    public Date getLastKnownActivityDate() {
        return lastKnownActivityDate;
    }

    public void setLastKnownActivityDate(@Nullable Date lastKnownActivityDate) {
        this.lastKnownActivityDate = lastKnownActivityDate;
    }

    public List<String> getAgrees() {
        return agrees;
    }

    public void setAgrees(List<String> agrees) {
        this.agrees = agrees;
    }

    public List<String> getDisagrees() {
        return disagrees;
    }

    public void setDisagrees(List<String> disagrees) {
        this.disagrees = disagrees;
    }
}
