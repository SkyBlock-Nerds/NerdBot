package net.hypixel.nerdbot.generator.impl.tooltip.skyblock.powerstone;

import lombok.Getter;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.impl.tooltip.skyblock.SkyblockItemSettings;
import net.hypixel.nerdbot.generator.powerstone.PowerstoneStat;
import net.hypixel.nerdbot.generator.powerstone.ScalingPowerstoneStat;

import java.util.List;

@Getter
public class SkyblockPowerstoneSettings extends SkyblockItemSettings {

    private final List<ScalingPowerstoneStat> scalingStats;
    private final List<PowerstoneStat> staticStats;
    private final int magicalPower;
    private final String combatRequirement;

    public SkyblockPowerstoneSettings(String lore, String name, int alpha, int padding, int maxLineLength, boolean renderBorder, Rarity rarity, boolean emptyLine, String type, boolean paddingFirstLine, List<ScalingPowerstoneStat> scalingStats, List<PowerstoneStat> staticStats, int magicalPower, String combatRequirement) {
        super(lore, name, alpha, padding, maxLineLength, renderBorder, rarity, emptyLine, type, paddingFirstLine);
        this.scalingStats = scalingStats;
        this.staticStats = staticStats;
        this.magicalPower = magicalPower;
        this.combatRequirement = combatRequirement;
    }
}