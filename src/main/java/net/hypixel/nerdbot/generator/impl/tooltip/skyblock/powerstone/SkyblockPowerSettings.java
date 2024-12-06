package net.hypixel.nerdbot.generator.impl.tooltip.skyblock.powerstone;

import lombok.Getter;
import net.hypixel.nerdbot.generator.data.PowerStrength;
import net.hypixel.nerdbot.generator.impl.tooltip.TooltipSettings;
import net.hypixel.nerdbot.generator.powerstone.PowerstoneStat;
import net.hypixel.nerdbot.generator.powerstone.ScalingPowerstoneStat;

import java.util.List;

@Getter
public class SkyblockPowerSettings extends TooltipSettings {

    private final PowerStrength strength;
    private final List<ScalingPowerstoneStat> scalingStats;
    private final List<PowerstoneStat> staticStats;
    private final boolean selected;
    private final int magicalPower;
    private final boolean stonePower;

    public SkyblockPowerSettings(String lore, String name, int alpha, int padding, int maxLineLength, boolean renderBorder, PowerStrength strength, List<ScalingPowerstoneStat> scalingStats, List<PowerstoneStat> staticStats, boolean selected, int magicalPower, boolean stonePower) {
        super(lore, name, alpha, padding, maxLineLength, renderBorder);
        this.strength = strength;
        this.scalingStats = scalingStats;
        this.staticStats = staticStats;
        this.selected = selected;
        this.magicalPower = magicalPower;
        this.stonePower = stonePower;
    }
}