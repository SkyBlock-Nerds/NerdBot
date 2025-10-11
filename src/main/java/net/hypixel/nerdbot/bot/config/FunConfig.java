package net.hypixel.nerdbot.bot.config;

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
}
