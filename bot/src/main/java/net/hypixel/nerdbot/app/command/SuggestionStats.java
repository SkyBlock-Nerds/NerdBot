package net.hypixel.nerdbot.app.command;

public record SuggestionStats(int totalSuggestions, int greenlitSuggestions, int totalAgrees, int totalDisagrees) {

    public int totalReactions() {
        return totalAgrees + totalDisagrees;
    }

    public int greenlitPercentage() {
        if (totalSuggestions == 0) {
            return 0;
        }

        return (int) Math.round((greenlitSuggestions / (double) totalSuggestions) * 100.0);
    }
}