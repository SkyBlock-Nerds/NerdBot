package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ChannelActivityEntry {

    private String channelId;
    private String lastKnownDisplayName;
    private int messageCount;
    private long lastMessageTimestamp;
}
