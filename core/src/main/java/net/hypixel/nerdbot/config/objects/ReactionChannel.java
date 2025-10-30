package net.hypixel.nerdbot.config.objects;

import java.util.List;

public record ReactionChannel(String name, String discordChannelId, List<String> emojiIds, boolean thread) {
}