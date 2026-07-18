package net.hypixel.nerdbot.app.punishment;

import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;

import java.util.Map;

/**
 * A user's punishment tally: the total count plus a per-type breakdown containing only the types
 * with at least one punishment.
 *
 * @param total         the total number of punishments the user has received
 * @param countsByType  count per {@link PunishmentType}, omitting types with a zero count
 */
public record PunishmentStats(long total, Map<PunishmentType, Long> countsByType) {
}
