package net.hypixel.nerdbot.api.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BotConfig {

    private String prefix, guildId;
    private int minimumThreshold, messageLimit;
    private double percentage;
    private long interval;
    private Emojis emojis;

}
