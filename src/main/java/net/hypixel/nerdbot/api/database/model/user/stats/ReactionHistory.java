package net.hypixel.nerdbot.api.database.model.user.stats;

public record ReactionHistory(String channelId, String reactionName, long timestamp) {

    public ReactionHistory(String channelId, String reactionName) {
        this(channelId, reactionName, System.currentTimeMillis());
    }
}
