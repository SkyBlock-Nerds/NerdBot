package net.hypixel.skyblocknerds.api.badge;

import net.hypixel.skyblocknerds.api.badge.config.BadgeConfiguration;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;

import java.util.HashMap;
import java.util.Map;

public class BadgeManager {
    private static final Map<String, Badge> BADGE_MAP = new HashMap<>();

    public static void loadBadges() {
        BadgeConfiguration badgeConfiguration = ConfigurationManager.loadConfig(BadgeConfiguration.class);

        if (badgeConfiguration == null) {
            throw new IllegalStateException("Badge configuration is null!");
        }

        // log.info("Loading badges from config file...");
        System.out.println("Loading badges from config file...");

        if (badgeConfiguration.getBadges() == null || badgeConfiguration.getBadges().isEmpty()) {
            // log.error("No badges found in config file!");
            System.out.println("No badges found in config file!");
            return;
        }

        badgeConfiguration.getBadges().forEach(badge -> {
            BADGE_MAP.put(badge.getId(), badge);
            System.out.println("Loaded badge: " + badge);
            // log.info("Loaded badge: " + badge);
        });
    }

    public static Badge getBadgeById(String badgeId) {
        return BADGE_MAP.get(badgeId);
    }

    public static TieredBadge getTieredBadgeById(String badgeId) {
        return (TieredBadge) BADGE_MAP.get(badgeId);
    }

    public static boolean isTieredBadge(String badgeId) {
        return BADGE_MAP.get(badgeId) instanceof TieredBadge;
    }

    public static Map<String, Badge> getBadgeMap() {
        return BADGE_MAP;
    }
}
