package net.hypixel.nerdbot.generator.powerstone;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.hypixel.nerdbot.generator.data.Stat;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class PowerstoneStat {

    protected final Stat stat;
    protected final int statValue;

    /**
     * Creates a {@link List} of {@link PowerstoneStat} from a properly formatted string.
     *
     * @param stats Formatted string: {@link String `statName1:statValue1,statName2:statValue2`}.
     *
     * @return A {@link List} of {@link PowerstoneStat}.
     *
     * @throws InvalidPowerstoneStatFormatException If the format in the {@link String stats} param isn't correct.
     */
    public static List<PowerstoneStat> statsFromString(String stats) {
        List<PowerstoneStat> statsList = new ArrayList<>();

        if (stats != null) {
            String[] entries = stats.split(",");

            for (String entry : entries) {
                statsList.add(statFromString(entry));
            }
        }

        return statsList;
    }

    /**
     * Creates a {@link PowerstoneStat} from a properly formatted string.
     *
     * @param stat Formatted string: {@link String `statName:statValue`}.
     *
     * @return A {@link PowerstoneStat}.
     *
     * @throws InvalidPowerstoneStatFormatException If the format in the {@link String stat} param isn't correct.
     */
    public static PowerstoneStat statFromString(String stat) {

        String[] statSplit = stat.split(":");

        if (statSplit.length != 2 || statSplit[0].trim().isEmpty() || statSplit[1].trim().isEmpty()) {
            throw new InvalidPowerstoneStatFormatException("Stat `" + stat + "` is using an invalid format");
        }

        String statName = statSplit[0].trim();

        int statValue;

        try {
            statValue = Integer.parseInt(statSplit[1].trim());
        } catch (NumberFormatException e) {
            throw new InvalidPowerstoneStatFormatException("Invalid number for stat `" + statName + "`: " + statSplit[1].trim());
        }

        return new PowerstoneStat(Stat.byName(statName), statValue);
    }

    @Override
    public String toString() {
        return "%%" + stat.getName() + ":" + statValue + "%%";
    }
}