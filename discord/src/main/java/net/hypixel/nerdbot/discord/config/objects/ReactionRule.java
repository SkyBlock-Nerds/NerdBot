package net.hypixel.nerdbot.discord.config.objects;

import java.util.List;

/**
 * Defines a set of reactions that should be applied to a message when it matches the provided filters.
 */
public record ReactionRule(String name,
                           List<String> emojiIds,
                           boolean thread,
                           List<String> contentContains,
                           List<String> embedTitleContains,
                           List<String> embedDescriptionContains) {

    public List<String> emojiIds() {
        return emojiIds == null ? List.of() : emojiIds;
    }

    public List<String> contentContains() {
        return contentContains == null ? List.of() : contentContains;
    }

    public List<String> embedTitleContains() {
        return embedTitleContains == null ? List.of() : embedTitleContains;
    }

    public List<String> embedDescriptionContains() {
        return embedDescriptionContains == null ? List.of() : embedDescriptionContains;
    }
}