package net.hypixel.nerdbot.api.database.model.user.badge;

import org.jetbrains.annotations.Nullable;

public record BadgeEntry(String badgeId, @Nullable Integer tier, long obtainedAt) {

    public BadgeEntry(String badgeId) {
        this(badgeId, null, System.currentTimeMillis());
    }

    public BadgeEntry(String badgeId, int tier) {
        this(badgeId, tier, System.currentTimeMillis());
    }
}