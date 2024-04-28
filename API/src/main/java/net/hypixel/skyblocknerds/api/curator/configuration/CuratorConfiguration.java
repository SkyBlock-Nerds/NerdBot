package net.hypixel.skyblocknerds.api.curator.configuration;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;

@Getter
@Setter
public class CuratorConfiguration implements IConfiguration {

    /**
     * Whether the {@link net.hypixel.skyblocknerds.api.curator.Curator} system is enabled
     * <p>
     * Default is true
     */
    private boolean enabled = true;

    /**
     * The minimum number of reactions required for a suggestion to be considered
     * <p>
     * Default is 25 reactions
     */
    private int minimumReactionsRequired = 25;

    /**
     * The maximum age of a suggestion for it to be considered
     * <p>
     * Default is 6 months
     */
    private long maximumAgeConsidered = 1000L * 60L * 60L * 24L * 30L * 6L;

    /**
     * The minimum percentage of positive->negative reactions required for a suggestion to be considered
     * <p>
     * Default is 75.0 (75%)
     */
    private double minimumReactionRatio = 75.0D;

    /**
     * Whether threads should be archived after being greenlit
     * <p>
     * Default is true
     */
    private boolean archiveGreenlitThreads = true;

    /**
     * Whether greenlit threads should be locked after being greenlit
     * <p>
     * Default is false
     */
    private boolean lockGreenlitThreads = false;
}
