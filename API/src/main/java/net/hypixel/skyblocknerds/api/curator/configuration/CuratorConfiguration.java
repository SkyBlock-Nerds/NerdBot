package net.hypixel.skyblocknerds.api.curator.configuration;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;

@Getter
@Setter
public class CuratorConfiguration implements IConfiguration {

    /**
     * Whether the {@link net.hypixel.skyblocknerds.api.curator.Curator} system is enabled
     */
    private boolean enabled = true;

    /**
     * The minimum number of reactions required for a suggestion to be considered
     */
    private int minimumReactionsRequired = 25;

    /**
     * The maximum age of a suggestion for it to be considered
     * Default is 6 months
     */
    private long maximumAgeConsidered = 1000L * 60L * 60L * 24L * 30L * 6L;

    /**
     * The minimum percentage of positive->negative reactions required for a suggestion to be considered
     */
    private double minimumReactionRatio = 75.0D;
}
