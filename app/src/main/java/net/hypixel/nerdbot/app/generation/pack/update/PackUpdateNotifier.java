package net.hypixel.nerdbot.app.generation.pack.update;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.discord.cache.ChannelCache;

import java.awt.Color;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Posts pack update outcomes worth a human's attention to the bot log channel: applied packs,
 * rejected packs, rollbacks, and a single alert when transient failures reach the threshold.
 */
public class PackUpdateNotifier implements PackUpdateListener {

    static final int TRANSIENT_ALERT_THRESHOLD = 4;
    private static final int HASH_PREFIX_LENGTH = 12;
    private static final Pattern SHA1_PATTERN = Pattern.compile("[0-9a-f]{40}");

    @Override
    public void onOutcome(String packId, PackUpdateOutcome outcome) {
        switch (outcome) {
            case PackUpdateOutcome.Applied applied -> ChannelCache.sendToLogChannel(new EmbedBuilder()
                .setTitle("Resource pack updated: " + packId)
                .setColor(Color.GREEN)
                .addField("From", applied.from() == null ? "configured pack" : describe(applied.from()), false)
                .addField("To", describe(applied.to()), false)
                .addField("Items", applied.previousItemCount() + " to " + applied.itemCount(), false)
                .setTimestamp(Instant.now())
                .build());
            case PackUpdateOutcome.Rejected rejected -> ChannelCache.sendToLogChannel(new EmbedBuilder()
                .setTitle("Resource pack update rejected: " + packId)
                .setColor(Color.RED)
                .addField("Version", "format " + rejected.packFormat() + ", " + shortHash(rejected.sha1()), false)
                .addField("Reason", fieldValue(rejected.reason()), false)
                .setFooter("The current pack stays live. This version is retried only if the update settings change.")
                .setTimestamp(Instant.now())
                .build());
            case PackUpdateOutcome.RolledBack rolledBack -> ChannelCache.sendToLogChannel(new EmbedBuilder()
                .setTitle("Resource pack rolled back: " + packId)
                .setColor(Color.ORANGE)
                .addField("From", describe(rolledBack.from()), false)
                .addField("To", describe(rolledBack.to()), false)
                .setTimestamp(Instant.now())
                .build());
            case PackUpdateOutcome.TransientFailure failure when failure.consecutiveFailures() == TRANSIENT_ALERT_THRESHOLD ->
                ChannelCache.sendToLogChannel(new EmbedBuilder()
                    .setTitle("Resource pack update checks are failing: " + packId)
                    .setColor(Color.YELLOW)
                    .addField("Latest reason", fieldValue(failure.reason()), false)
                    .setFooter("Posted once until a check succeeds.")
                    .setTimestamp(Instant.now())
                    .build());
            default -> {
                // UpToDate, Busy, Unavailable and other transient failures are logged by the updater only
            }
        }
    }

    /** One-line description of an applied pack, shared with the /pack command replies. */
    public static String describe(PackState.AppliedPack pack) {
        return "format " + pack.packFormat() + ", " + shortHash(pack.sha1()) + ", deploy " + pack.deployId();
    }

    /** The first 12 characters of a SHA-1; anything that is not a 40 character lowercase hex SHA-1 (a deploy key) is returned unchanged. */
    public static String shortHash(String value) {
        return SHA1_PATTERN.matcher(value).matches() ? value.substring(0, HASH_PREFIX_LENGTH) : value;
    }

    /** Reasons can be long (exception text); Discord rejects embed field values over its limit. */
    private static String fieldValue(String text) {
        if (text == null || text.isBlank()) {
            return "(no reason given)";
        }
        int max = MessageEmbed.VALUE_MAX_LENGTH;
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
