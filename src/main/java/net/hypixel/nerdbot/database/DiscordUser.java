package net.hypixel.nerdbot.database;

import org.jetbrains.annotations.Nullable;

import java.util.Date;

public class DiscordUser {

    private String discordId;
    private int totalSuggestionReactions, totalAgrees, totalDisagrees;
    @Nullable
    private Date lastReactionDate;

    public DiscordUser() {
    }

    public DiscordUser(String discordId, int totalSuggestionReactions, int totalAgrees, int totalDisagrees, @Nullable Date lastReactionDate) {
        this.discordId = discordId;
        this.totalSuggestionReactions = totalSuggestionReactions;
        this.totalAgrees = totalAgrees;
        this.totalDisagrees = totalDisagrees;
        this.lastReactionDate = lastReactionDate;
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
    public Date getLastReactionDate() {
        return lastReactionDate;
    }

    public void setLastReactionDate(@Nullable Date lastReactionDate) {
        this.lastReactionDate = lastReactionDate;
    }
}
