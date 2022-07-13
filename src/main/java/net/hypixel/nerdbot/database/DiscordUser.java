package net.hypixel.nerdbot.database;

public class DiscordUser {

    private String discordId;
    private int totalSuggestionReactions, totalAgrees, totalDisagrees;

    public DiscordUser() {
    }

    public DiscordUser(String id, int totalSuggestionReactions, int totalAgrees, int totalDisagrees) {
        this.discordId = id;
        this.totalSuggestionReactions = totalSuggestionReactions;
        this.totalAgrees = totalAgrees;
        this.totalDisagrees = totalDisagrees;
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
}
