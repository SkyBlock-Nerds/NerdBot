package net.hypixel.skyblocknerds.database.objects.user.activity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@AllArgsConstructor
@Getter
@Setter
public class ChannelActivityEntry {

    /**
     * The ID of the channel where the activity entry took place
     */
    private String channelId;

    /**
     * The last known name of the channel where the activity entry took place
     */
    private String lastKnownDisplayName;

    /**
     * The amount of messages sent in the channel
     */
    private int messageCount;

    /**
     * The {@link Long timestamp} of the last message sent in the channel
     */
    private long lastMessageTimestamp;

    /**
     * The amount of messages sent in the channel per month
     * <p>
     * The key is the MONTH-YEAR (e.g. 01-2021) and the value is the amount of messages sent in that month
     * </p>
     */
    private Map<String, Integer> monthlyMessageCount;

}

