package net.hypixel.nerdbot.api.database;

import org.jetbrains.annotations.Nullable;

import java.util.Date;

public class DiscordUser {

    private String discordId;
    private int totalSuggestionReactions, totalAgrees, totalDisagrees;
    @Nullable
    private Date lastKnownActivityDate;

    public DiscordUser() {
    }

    public DiscordUser(String discordId, int totalSuggestionReactions, int totalAgrees, int totalDisagrees, @Nullable Date lastKnownActivityDate) {
        this.discordId = discordId;
        this.totalSuggestionReactions = totalSuggestionReactions;
        this.totalAgrees = totalAgrees;
        this.totalDisagrees = totalDisagrees;
        this.lastKnownActivityDate = lastKnownActivityDate;
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public int getTotalSuggestionReactions() {
        return totalSuggestionReactions;
    }

    public void setTotalSuggestionReactions(int totalSuggestionReactions) {
        this.totalSuggestionReactions = totalSuggestionReactions;
    }

    public int getTotalAgrees() {
        return totalAgrees;
    }

    public void setTotalAgrees(int totalAgrees) {
        this.totalAgrees = totalAgrees;
    }

    public int getTotalDisagrees() {
        return totalDisagrees;
    }

    public void setTotalDisagrees(int totalDisagrees) {
        this.totalDisagrees = totalDisagrees;
    }

    @Nullable
    public Date getLastKnownActivityDate() {
        return lastKnownActivityDate;
    }

    public void setLastKnownActivityDate(@Nullable Date lastKnownActivityDate) {
        this.lastKnownActivityDate = lastKnownActivityDate;
    }
}
