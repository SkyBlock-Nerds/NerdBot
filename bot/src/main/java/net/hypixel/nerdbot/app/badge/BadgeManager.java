package net.hypixel.nerdbot.app.badge;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.BotEnvironment;
import net.hypixel.nerdbot.core.api.badge.Badge;
import net.hypixel.nerdbot.core.api.badge.TieredBadge;
import net.hypixel.nerdbot.discord.api.bot.DiscordBot;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BadgeManager {
    private static final Map<String, Badge> BADGE_MAP = new HashMap<>();

    public static void loadBadges() {
        log.info("Loading badges from config file...");

        if (!(BotEnvironment.getBot() instanceof DiscordBot discordBot)) {
            log.error("Bot is not a DiscordBot instance, cannot load badges");
            return;
        }

        discordBot.getConfig().getBadgeConfig().getBadges().forEach(badge -> {
            BADGE_MAP.put(badge.getId(), badge);
            log.info("Loaded badge: " + badge);
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