package net.hypixel.nerdbot.generator.powerstone;

import java.util.List;

public class PowerstoneUtil {

    public static final String SCALING_STATS_HEADER_POWER = "&rStats:\\n";
    public static final String SCALING_STATS_HEADER_POWERSTONE_FORMAT = "&rAt &6%d Magical Power&7:\\n"; // %d = Magical power amount
    public static final String STATIC_STATS_HEADER = "&rUnique Power Bonus:\\n";
    public static final String MAGICAL_POWER_FORMAT = "&rYou have: &6%d Magical Power"; // %d = Magical power amount
    public static final String POWER_SELECTED_TRUE = "&r&aPower is selected!";
    public static final String POWER_SELECTED_FALSE = "&r&eClick to select power!";
    public static final String COMBAT_REQUIREMENT_POWERSTONE_FORMAT = "&rRequires &aCombat Skill Level %s&7!";
    public static final String POWER_STONE_ITEM_TYPE = "POWER STONE";
    public static final String DEFAULT_STONE_FORMAT =
        "&r&8Power Stone\\n" +
        "&r&7Combine &a9x &7of this stone at the &6Thaumaturgist &7to permanently unlock the &a%s &7power."; // %s = Powerstone name

    public static String parseScalingStatsToString(List<ScalingPowerstoneStat> stats) {
        StringBuilder output = new StringBuilder();

        stats.forEach(stat -> output.append('&').append(stat.getStat().getColor().getCode()).append(stat.statValue > 0 ? "+" : "").append(stat).append("\\n"));

        return output.toString();
    }

    public static String parseStaticStatsToString(List<PowerstoneStat> stats) {
        StringBuilder output = new StringBuilder();

        stats.forEach(stat -> output.append('&').append(stat.getStat().getColor().getCode()).append(stat.statValue > 0 ? "+" : "").append(stat).append("\\n"));

        return output.toString();
    }
}