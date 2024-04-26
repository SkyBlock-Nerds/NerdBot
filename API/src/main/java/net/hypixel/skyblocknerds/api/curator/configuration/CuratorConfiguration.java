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
    private int minimumReactionsRequired = 15;

    /**
     * The minimum age of a suggestion required for it to be considered
     */
    private long minimumTimeRequired = 1000 * 60 * 60 * 24 * 7;

    /**
     * The minimum percentage of positive->negative reactions required for a suggestion to be considered
     */
    private double minimumReactionRatio = 75.0D;
}
