package net.hypixel.nerdbot.app.badge;

import jakarta.inject.Inject;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;

import java.util.Map;
import java.util.Optional;

/** The badges loaded from the config at startup, see {@link BadgeManager#loadBadges()}. */
public class ConfiguredBadgeCatalog implements BadgeCatalog {

    @Inject
    public ConfiguredBadgeCatalog() {
    }

    @Override
    public Optional<Badge> find(String badgeId) {
        return Optional.ofNullable(BadgeManager.getBadgeById(badgeId));
    }

    @Override
    public Map<String, Badge> all() {
        return Map.copyOf(BadgeManager.getBadgeMap());
    }
}
