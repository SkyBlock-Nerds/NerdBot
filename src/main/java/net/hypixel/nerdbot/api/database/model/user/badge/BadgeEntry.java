package net.hypixel.nerdbot.api.database.model.user.badge;

import lombok.Getter;

import java.util.Date;

@Getter
public class BadgeEntry {

    private final String badgeId;
    private final int tier;
    private final Date obtainedAt;

    public BadgeEntry(String badgeId, int tier, Date obtainedAt) {
        this.badgeId = badgeId;
        this.tier = tier;
        this.obtainedAt = obtainedAt;
    }

    public BadgeEntry(String badgeId, Date obtainedAt) {
        this(badgeId, -1, obtainedAt);
    }

    public BadgeEntry(String badgeId) {
        this(badgeId, new Date());
    }

    public BadgeEntry(String badgeId, int tier) {
        this(badgeId, tier, new Date());
    }
}
