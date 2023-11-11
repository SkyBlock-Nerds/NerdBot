package net.hypixel.nerdbot.api.database.model.user.stats;

public record ReactionHistory(String channelId, String reactionName, long suggestionTimestamp, long reactionTimestamp) {

    public ReactionHistory(String channelId, String reactionName, long suggestionTimestamp) {
        this(channelId, reactionName, suggestionTimestamp, System.currentTimeMillis());
    }
}
