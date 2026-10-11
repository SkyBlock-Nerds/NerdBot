package net.hypixel.nerdbot.app.badge;

import net.hypixel.nerdbot.marmalade.storage.badge.Badge;

import java.util.Map;
import java.util.Optional;

/** The badges that can be given out, by id. */
public interface BadgeCatalog {

    Optional<Badge> find(String badgeId);

    Map<String, Badge> all();
}
