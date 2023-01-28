package net.hypixel.nerdbot.channel;

import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@ToString
public class ReactionChannel {

    private final String name;
    private final String discordChannelId;
    private final List<String> emojiIds;

    public ReactionChannel(String name, String discordChannelId, List<String> emojiIds) {
        this.name = name;
        this.discordChannelId = discordChannelId;
        this.emojiIds = emojiIds;
    }

    public ReactionChannel(String name, String discordChannelId) {
        this(name, discordChannelId, new ArrayList<>());
    }
}
