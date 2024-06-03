package net.hypixel.skyblocknerds.database.objects.user.activity;

import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class ActivityData {

    private long lastMessageTimestamp;

    private List<ChannelActivityEntry> channelActivityEntries;
    private List<VoteActivityEntry> voteActivityEntries;
}
