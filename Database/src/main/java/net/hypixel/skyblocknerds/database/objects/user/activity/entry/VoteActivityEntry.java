package net.hypixel.skyblocknerds.database.objects.user.activity.entry;

import net.hypixel.skyblocknerds.database.objects.user.activity.ActivityType;

public class VoteActivityEntry {

    /**
     * The ID of the thread where the activity entry took place
     */
    private String threadId;

    /**
     * The category of the activity, e.g. REGULAR, ALPHA, PROJECT
     */
    private ActivityType voteType;

    /**
     * The {@link Long timestamp} of the vote
     */
    private long timestamp;

}
