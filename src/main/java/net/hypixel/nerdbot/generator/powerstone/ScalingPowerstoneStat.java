package net.hypixel.nerdbot.generator.powerstone;

import lombok.Getter;
import net.hypixel.nerdbot.generator.data.Stat;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ScalingPowerstoneStat extends PowerstoneStat {

    private final Integer basePower;

    public ScalingPowerstoneStat(int basePower, Stat stat, int magicalPower) {
        super(
            stat,
            (int)calculateScalingPowerStoneStat(stat, basePower, magicalPower)
        );
        this.basePower = basePower;
    }

    /**
     * Creates a {@link ScalingPowerstoneStat} from a properly formatted string (and Magical Power).
     *
     * @param string        Formatted string: {@link String `statName:basePower`}.
     * @param magicalPower  The Magical Power the stats will be scaled with.
     *
     * @return              A {@link ScalingPowerstoneStat} with the scaled StatValue.
     */
    public static ScalingPowerstoneStat scalingStatfromString(String string, int magicalPower) {
        PowerstoneStat nonScaledStat = PowerstoneStat.statFromString(string);

        return new ScalingPowerstoneStat(nonScaledStat.getStatValue(), nonScaledStat.getStat(), magicalPower);
    }

    /**
     * Creates a {@link List} of {@link ScalingPowerstoneStat} from a properly formatted string (and Magical Power).
     *
     * @param stats         Formatted string: {@link String `statName1:statValue1,statName2:statValue2`}.
     * @param magicalPower  The Magical Power the stats will be scaled with.
     *
     * @return              A {@link ScalingPowerstoneStat} with the scaled StatValue.
     */
    public static List<ScalingPowerstoneStat> scalingStatsfromString(String stats, int magicalPower) {
        List<ScalingPowerstoneStat> statsList = new ArrayList<ScalingPowerstoneStat>();

        if (stats != null) {
            String[] entries = stats.split(",");

            for (String entry : entries) {
                statsList.add(scalingStatfromString(entry, magicalPower));
            }
        }

        return statsList;
    }

    /**
     * Calculates the stat value for a scaling Power Stone stat based on the base power and magical power.
     *
     * @param stat         The {@link Stat} to calculate the value for
     * @param basePower    The base power of the stat
     * @param magicalPower The magical power of the Power Stone
     *
     * @return The calculated stat value
     */
    public static double calculateScalingPowerStoneStat(Stat stat, int basePower, int magicalPower) {
        double statMultiplier = stat.getPowerScalingMultiplier() != null ? stat.getPowerScalingMultiplier() : 1;
        return ((double) basePower / 100) * statMultiplier * 719.28 * Math.pow(Math.log(1 + (0.0019 * magicalPower)), 1.2);
    }
}