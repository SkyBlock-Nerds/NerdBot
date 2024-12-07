package net.hypixel.nerdbot.generator.impl.tooltip.skyblock.powerstone;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.data.PowerStrength;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.impl.tooltip.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.powerstone.PowerstoneStat;
import net.hypixel.nerdbot.generator.powerstone.PowerstoneUtil;
import net.hypixel.nerdbot.generator.powerstone.ScalingPowerstoneStat;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;
import net.hypixel.nerdbot.util.Range;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SkyblockPowerGenerator extends MinecraftTooltipGenerator<SkyblockPowerSettings> {

    private final PowerStrength strength;
    private final List<ScalingPowerstoneStat> scalingStats;
    private final List<PowerstoneStat> staticStats;
    private final boolean selected;
    private final int magicalPower;
    private final boolean stonePower;

    protected SkyblockPowerGenerator(String name, boolean renderBorder, int maxLineLength, int padding, int alpha, boolean centeredText, PowerStrength strength, List<ScalingPowerstoneStat> scalingStats, List<PowerstoneStat> staticStats, boolean selected, int magicalPower, boolean stonePower) {
        super(name, renderBorder, maxLineLength, padding, alpha, "", centeredText);
        this.strength = strength;
        this.scalingStats = scalingStats;
        this.staticStats = staticStats;
        this.selected = selected;
        this.magicalPower = magicalPower;
        this.stonePower = stonePower;
    }

    @Override
    public GeneratedObject generate() {
        SkyblockPowerSettings settings = new SkyblockPowerSettings(
            itemLore,
            name,
            alpha,
            padding,
            maxLineLength,
            renderBorder,
            strength,
            scalingStats,
            staticStats,
            selected,
            magicalPower,
            stonePower
        );

        return new GeneratedObject(buildItem(settings));
    }

    @Override
    public MinecraftTooltip parseLore(SkyblockPowerSettings settings) {
        log.debug("Parsing lore for item: {} with SkyblockPowerSettings: {}", settings.getName(), settings);

        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(settings.getPadding())
            .setRenderBorder(settings.isRenderBorder())
            .withAlpha(Range.between(0, 255).fit(settings.getAlpha()));

        if (settings.getName() != null && !settings.getName().isEmpty()) {
            String name = "&a" + settings.getName();

            builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(name), '&'));
        }

        builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                "&r&8" +
                        strength.getFormattedDisplay()),
                '&'
        ));

        if (settings.getScalingStats() != null && !settings.getScalingStats().isEmpty()) {
            builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                    "\\n" +
                            PowerstoneUtil.SCALING_STATS_HEADER_POWER +
                            PowerstoneUtil.parseScalingStatsToString(settings.getScalingStats())),
                    '&'
            ));
        }

        if (settings.getStaticStats() != null && !settings.getStaticStats().isEmpty()) {
            builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                    "\\n" +
                            PowerstoneUtil.STATIC_STATS_HEADER +
                            PowerstoneUtil.parseStaticStatsToString(settings.getStaticStats())),
                    '&'
            ));
        }

        builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                "\\n" +
                        PowerstoneUtil.MAGICAL_POWER_FORMAT.formatted(settings.getMagicalPower())),
                '&'
        ));

        builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                "\\n" +
                        (selected ? PowerstoneUtil.POWER_SELECTED_TRUE : PowerstoneUtil.POWER_SELECTED_FALSE)),
            '&'
        ));

        return builder.build();
    }


    public static class Builder implements ClassBuilder<SkyblockPowerGenerator> {
        private String name;
        private int padding;
        private int alpha;
        private PowerStrength strength;
        private List<ScalingPowerstoneStat> scalingStats = new ArrayList<>();
        private List<PowerstoneStat> staticStats = new ArrayList<>();
        private boolean selected;
        private int magicalPower;
        private boolean stonePower;

        public SkyblockPowerGenerator.Builder withName(String name) {
            this.name = name;
            return this;
        }

        public SkyblockPowerGenerator.Builder withPadding(int padding) {
            this.padding = padding;
            return this;
        }

        public SkyblockPowerGenerator.Builder withAlpha(int alpha) {
            this.alpha = alpha;
            return this;
        }

        public SkyblockPowerGenerator.Builder withStrength(PowerStrength strength) {
            this.strength = strength;
            return this;
        }

        public SkyblockPowerGenerator.Builder withScalingStats(List<ScalingPowerstoneStat> stats) {
            this.scalingStats.addAll(stats);
            return this;
        }

        public SkyblockPowerGenerator.Builder withScalingStat(ScalingPowerstoneStat stat) {
            this.scalingStats.add(stat);
            return this;
        }

        public SkyblockPowerGenerator.Builder withStaticStats(List<PowerstoneStat> stats) {
            this.staticStats.addAll(stats);
            return this;
        }

        public SkyblockPowerGenerator.Builder withStaticStat(PowerstoneStat stat) {
            this.staticStats.add(stat);
            return this;
        }

        public SkyblockPowerGenerator.Builder withSelected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public SkyblockPowerGenerator.Builder withMagicalPower(int magicalPower) {
            this.magicalPower = magicalPower;
            return this;
        }

        public SkyblockPowerGenerator.Builder withStonePower(boolean stonePower) {
            this.stonePower = stonePower;
            return this;
        }

        public String buildSlashCommand() {
            StringBuilder builder =
                    new StringBuilder(
                            "/" + GeneratorCommands.BASE_COMMAND + " item full" +
                            " name:&a" + this.name +
                            " alpha:" + this.alpha +
                            " padding:" + this.padding +
                            " item_lore:&r&8" + strength.getFormattedDisplay()
                    );

            if (!this.scalingStats.isEmpty()) {
                builder.append("\\n\\n")
                        .append(PowerstoneUtil.SCALING_STATS_HEADER_POWER)
                        .append(PowerstoneUtil.parseScalingStatsToString(this.scalingStats));
            }
            else {
                builder.append("\\n");
            }

            if (!this.staticStats.isEmpty()) {
                builder.append("\\n")
                        .append(PowerstoneUtil.STATIC_STATS_HEADER)
                        .append(PowerstoneUtil.parseStaticStatsToString(this.staticStats));
            }
            else {
                builder.append("\\n");
            }

            builder.append("\\n")
                    .append(PowerstoneUtil.MAGICAL_POWER_FORMAT.formatted(this.magicalPower));

            builder.append("\\n\\n")
                    .append(this.selected ? PowerstoneUtil.POWER_SELECTED_TRUE : PowerstoneUtil.POWER_SELECTED_FALSE);

            return builder.toString();
            // TODO see below
            // NOTE: Currently gen full still needs the fields 'rarity' and 'type' and this builder doesn't add those so for now the user still has to add it them themselves.
            // Why not add them? I already made a PR (https://github.com/SkyBlock-Nerds/NerdBot/pull/340) that removes them, I'm sure that if I do add them now I'm going to forget to remove it so instead adding a to do.
            // So if you are reading this and the above pr is merged remove these comments please, thank you! -Socks
        }

        @Override
        public SkyblockPowerGenerator build() {
            boolean renderBorder = true;
            int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
            boolean centeredText = false;
            return new SkyblockPowerGenerator(name, renderBorder, maxLineLength, padding, alpha, centeredText, strength, scalingStats, staticStats, selected, magicalPower, stonePower);
        }
    }
}