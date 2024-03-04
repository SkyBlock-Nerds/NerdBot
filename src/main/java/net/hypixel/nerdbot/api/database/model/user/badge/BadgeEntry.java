package net.hypixel.nerdbot.api.database.model.user.badge;

import lombok.Getter;

@Getter
public class BadgeEntry {

    private final String badgeId;
    private final int tier;
    private final long obtainedAt;

    public BadgeEntry(String badgeId, int tier, long obtainedAt) {
        this.badgeId = badgeId;
        this.tier = tier;
        this.obtainedAt = obtainedAt;
    }

    public BadgeEntry(String badgeId, long obtainedAt) {
        this(badgeId, -1, obtainedAt);
    }

    public BadgeEntry(String badgeId) {
        this(badgeId, System.currentTimeMillis());
    }

    public BadgeEntry(String badgeId, int tier) {
        this(badgeId, tier, System.currentTimeMillis());
    }
}
