package net.hypixel.nerdbot.discord.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class FeatureConfig {

    /**
     * Whether this feature should be enabled at startup.
     * Defaults to {@code true}
     */
    private boolean enabled = true;

    /**
     * Fully-qualified class name of the feature implementation.
     * Must implement {@code net.hypixel.nerdbot.discord.api.feature.BotFeature}
     * and provide a public no-argument constructor.
     */
    @ExampleValue("net.hypixel.nerdbot.app.feature.CurateFeature")
    private String className;

    /**
     * Optional initial delay in milliseconds before the feature's periodic task first runs.
     */
    @ExampleValue("0")
    private Long initialDelayMs;

    /**
     * Optional repeating period in milliseconds for the feature's task.
     */
    @ExampleValue("3600000")
    private Long periodMs;

}
