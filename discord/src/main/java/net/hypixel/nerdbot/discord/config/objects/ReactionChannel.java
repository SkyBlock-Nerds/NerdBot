package net.hypixel.nerdbot.discord.config.objects;

import java.util.List;

public record ReactionChannel(String name, String discordChannelId, List<String> emojiIds, boolean thread, List<ReactionRule> reactionRules) {

    public List<String> emojiIds() {
        return emojiIds == null ? List.of() : emojiIds;
    }

    public List<ReactionRule> reactionRules() {
        return reactionRules == null ? List.of() : reactionRules;
    }
}