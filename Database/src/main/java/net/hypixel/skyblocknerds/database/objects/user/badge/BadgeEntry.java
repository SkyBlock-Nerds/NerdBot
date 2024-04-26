package net.hypixel.skyblocknerds.database.objects.user.badge;

import lombok.Getter;

@Getter
public class BadgeEntry {

    private final String badgeId;
    private final Integer tier;
    private final long obtainedAt;

    public BadgeEntry(String badgeId, Integer tier, long obtainedAt) {
        this.badgeId = badgeId;
        this.tier = tier;
        this.obtainedAt = obtainedAt;
    }

    public BadgeEntry(String badgeId) {
        this(badgeId, null, System.currentTimeMillis());
    }

    public BadgeEntry(String badgeId, int tier) {
        this(badgeId, tier, System.currentTimeMillis());
    }
}

