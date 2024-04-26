package net.hypixel.skyblocknerds.database.objects.user.activity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@AllArgsConstructor
@Getter
@Setter
public class ChannelActivityEntry {

    private String channelId;
    private String lastKnownDisplayName;
    private int messageCount;
    private long lastMessageTimestamp;
    private Map<String, Integer> monthlyMessageCount;

}
