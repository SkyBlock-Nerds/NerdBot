package net.hypixel.nerdbot.internalapi.database.model.user.badge;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public class BadgeEntry {

    private final String badgeId;
    @Nullable
    private final Integer tier;
    private final long obtainedAt;

    public BadgeEntry(String badgeId, @Nullable Integer tier, long obtainedAt) {
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
