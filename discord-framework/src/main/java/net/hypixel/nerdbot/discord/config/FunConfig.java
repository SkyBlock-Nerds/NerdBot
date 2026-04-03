package net.hypixel.nerdbot.discord.config;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class FunConfig {

    /**
     * The {@link Map} of auto reactions for the bot to use
     * <br><br>
     * Key: The user ID to react to when they send a message
     * <br>
     * Value: The emoji value to react with
     */
    private final Map<String, String> autoReactions = new HashMap<>();

    /**
     * The chance (0.0 to 1.0) that the bot will react to a random message with the April Fools emoji on April 1st.
     * <br>
     * If not set (null), the feature is disabled.
     */
    private Double aprilFoolsReactionChance;

    /**
     * The emoji to react with on April 1st when the random chance is met.
     * <br>
     * Supports both unicode emoji and custom emoji IDs.
     */
    private String aprilFoolsReactionEmoji;

    /**
     * The timezone to use for the April Fools date check (e.g. "Europe/London").
     * <br>
     * If not set (null), defaults to the JVM's system timezone.
     */
    private String aprilFoolsTimezone;

}