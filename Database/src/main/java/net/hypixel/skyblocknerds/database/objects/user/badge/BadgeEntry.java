package net.hypixel.skyblocknerds.database.objects.user.badge;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class BadgeEntry {

    private final String badgeId;
    private final Integer tier;
    private final long obtainedAt;

    /**
     * Creates a new {@link BadgeEntry} with the given badge ID, tier and obtained at timestamp
     *
     * @param badgeId    The ID of the badge
     * @param tier       The tier of the badge
     * @param obtainedAt The timestamp when the badge was obtained
     */
    public BadgeEntry(String badgeId, Integer tier, long obtainedAt) {
        this.badgeId = badgeId;
        this.tier = tier;
        this.obtainedAt = obtainedAt;
    }

    /**
     * Creates a new {@link BadgeEntry} with the given badge ID and sets the obtained at timestamp to the current time
     *
     * @param badgeId The ID of the badge
     */
    public BadgeEntry(String badgeId) {
        this(badgeId, null, System.currentTimeMillis());
    }

    /**
     * Creates a new {@link BadgeEntry} with the given badge ID, tier and sets the obtained at timestamp to the current time
     *
     * @param badgeId The ID of the badge
     * @param tier    The tier of the badge
     */
    public BadgeEntry(String badgeId, int tier) {
        this(badgeId, tier, System.currentTimeMillis());
    }
}

