package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.badge.BadgeCatalog;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class MapBadgeCatalog implements BadgeCatalog {

    private final Map<String, Badge> badges = new LinkedHashMap<>();

    public MapBadgeCatalog with(Badge badge) {
        badges.put(badge.getId(), badge);
        return this;
    }

    @Override
    public Optional<Badge> find(String badgeId) {
        return Optional.ofNullable(badges.get(badgeId));
    }

    @Override
    public Map<String, Badge> all() {
        return Map.copyOf(badges);
    }
}
