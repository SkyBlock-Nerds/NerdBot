package net.hypixel.nerdbot.generator.impl.tooltip.skyblock.powerstone;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.impl.tooltip.skyblock.SkyblockItemGenerator;
import net.hypixel.nerdbot.generator.impl.tooltip.skyblock.SkyblockItemSettings;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.powerstone.PowerstoneStat;
import net.hypixel.nerdbot.generator.powerstone.PowerstoneUtil;
import net.hypixel.nerdbot.generator.powerstone.ScalingPowerstoneStat;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;
import net.hypixel.nerdbot.util.Range;
import org.apache.commons.lang.NotImplementedException;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SkyblockPowerstoneGenerator extends SkyblockItemGenerator {

    private final List<ScalingPowerstoneStat> scalingStats;
    private final List<PowerstoneStat> staticStats;
    private final int magicalPower;
    private final String combatRequirement;

    protected SkyblockPowerstoneGenerator(String name, Rarity rarity, String itemLore, String type, boolean emptyLine, int alpha, int padding, boolean normalItem, boolean centeredText, int maxLineLength, boolean renderBorder, List<ScalingPowerstoneStat> scalingStats, List<PowerstoneStat> staticStats, int magicalPower, String combatRequirement) {
        super(name, rarity, itemLore, type, emptyLine, alpha, padding, normalItem, centeredText, maxLineLength, renderBorder);
        this.scalingStats = scalingStats;
        this.staticStats = staticStats;
        this.magicalPower = magicalPower;
        this.combatRequirement = combatRequirement;
    }

    @Override
    public GeneratedObject generate() {
        SkyblockPowerstoneSettings settings = new SkyblockPowerstoneSettings(
            itemLore,
            name,
            alpha,
            padding,
            maxLineLength,
            renderBorder,
            rarity,
            emptyLine,
            type,
            normalItem,
            scalingStats,
            staticStats,
            magicalPower,
            combatRequirement
        );

        return new GeneratedObject(buildItem(settings));
    }

    @Override
    public MinecraftTooltip parseLore(SkyblockItemSettings settings) {
        if (settings instanceof SkyblockPowerstoneSettings) {
            SkyblockPowerstoneSettings powerstoneSettings = (SkyblockPowerstoneSettings) settings;

            MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
                .withPadding(powerstoneSettings.getPadding())
                .setRenderBorder(powerstoneSettings.isRenderBorder())
                .withAlpha(Range.between(0,255).fit(powerstoneSettings.getAlpha()));

            if (settings.getName() != null && !settings.getName().isEmpty()) {
                String name;

                if (rarity != null && rarity != Rarity.byName("NONE")) {
                    name = rarity.getColorCode() + settings.getName();
                }
                else {
                     name = settings.getName();
                }

                builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(name), '&'));
            }

            List<List<LineSegment>> defaultStoneFormatSegments = new ArrayList<>();

            for (String line : TextWrapper.wrapString(TextWrapper.parseLine(PowerstoneUtil.DEFAULT_STONE_FORMAT.formatted(powerstoneSettings.getName())), settings.getMaxLineLength())) {
                defaultStoneFormatSegments.add(LineSegment.fromLegacy(line, '&'));
            }

            for (List<LineSegment> line : defaultStoneFormatSegments) {
                builder.withLines(line);
            }

            if (powerstoneSettings.getLore() != null && !powerstoneSettings.getLore().isEmpty()) {
                List<List<LineSegment>> extraLoreSegments = new ArrayList<>();

                for (String line : TextWrapper.wrapString(TextWrapper.parseLine("\\n" + settings.getLore()), settings.getMaxLineLength())) {
                    extraLoreSegments.add(LineSegment.fromLegacy(line, '&'));
                }

                for (List<LineSegment> line : extraLoreSegments) {
                    builder.withLines(line);
                }
            }

            if (powerstoneSettings.getScalingStats() != null && !powerstoneSettings.getScalingStats().isEmpty()) {
                builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                        new StringBuilder("\\n")
                            .append(PowerstoneUtil.SCALING_STATS_HEADER_POWERSTONE_FORMAT.formatted(powerstoneSettings.getMagicalPower()))
                            .append(PowerstoneUtil.parseScalingStatsToString(powerstoneSettings.getScalingStats()))
                            .toString()),
                    '&'));
            }

            if (powerstoneSettings.getStaticStats() != null && !powerstoneSettings.getStaticStats().isEmpty()) {
                builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                        new StringBuilder("\\n")
                            .append(PowerstoneUtil.STATIC_STATS_HEADER)
                            .append(PowerstoneUtil.parseStaticStatsToString(powerstoneSettings.getStaticStats()))
                            .toString()),
                    '&'));
            }

            if (powerstoneSettings.getCombatRequirement() != null && !powerstoneSettings.getCombatRequirement().isEmpty()) {
                builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                    new StringBuilder("\\n")
                        .append(PowerstoneUtil.COMBAT_REQUIREMENT_POWERSTONE_FORMAT.formatted(powerstoneSettings.getCombatRequirement()))
                        .toString()),
                    '&'
                ));
            }

            if (powerstoneSettings.getRarity() != null && powerstoneSettings.getRarity() != Rarity.byName("NONE")) {
                builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(
                        new StringBuilder("\\n")
                                .append(rarity.getFormattedDisplay())
                                .append(" ")
                                .append(powerstoneSettings.getType())
                                .toString()),
                        '&'
                ));
            }

            return builder.build();
        }
        else {
            throw new ClassCastException("Couldn't parse lore due to invalid settings object being passed. Expected SkyblockPowerstoneSettings but got:" + settings);
        }
    }

    public static class Builder implements ClassBuilder<SkyblockPowerstoneGenerator> {
        private String name;
        private Rarity rarity;
        private String extraLore;
        private int alpha;
        private int padding;
        private List<ScalingPowerstoneStat> scalingStats = new ArrayList<>();
        private List<PowerstoneStat> staticStats = new ArrayList<>();
        private int magicalPower;
        private String combatRequirement;

        public SkyblockPowerstoneGenerator.Builder withName(String name) {
            this.name = name;
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withRarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withExtraLore(String extraLore) {
            this.extraLore = extraLore;
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withAlpha(int alpha) {
            this.alpha = alpha;
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withPadding(int padding) {
            this.padding = padding;
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withScalingStats(List<ScalingPowerstoneStat> stats) {
            this.scalingStats.addAll(stats);
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withScalingStat(ScalingPowerstoneStat stat) {
            this.scalingStats.add(stat);
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withStaticStats(List<PowerstoneStat> stats) {
            this.staticStats.addAll(stats);
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withStaticStat(PowerstoneStat stat) {
            this.staticStats.add(stat);
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withMagicalPower(int magicalPower) {
            this.magicalPower = magicalPower;
            return this;
        }

        public SkyblockPowerstoneGenerator.Builder withCombatRequirement(String combatRequirement) {
            this.combatRequirement = combatRequirement;
            return this;
        }

        public String buildSlashCommand() {
            StringBuilder builder =
                    new StringBuilder(
                            "/" + GeneratorCommands.BASE_COMMAND + " item full" +
                            " name:" + this.name +
                            " rarity:" + this.rarity.getName() +
                            " type:" + PowerstoneUtil.POWER_STONE_ITEM_TYPE +
                            " alpha:" + this.alpha +
                            " padding:" + this.padding +
                            " item_lore:" + PowerstoneUtil.DEFAULT_STONE_FORMAT.formatted(this.name)
                    );

            if (this.extraLore != null) {
                builder.append("\\n\\n")
                        .append(extraLore);
            }

            if (!this.scalingStats.isEmpty()) {
                builder.append("\\n\\n")
                        .append(PowerstoneUtil.SCALING_STATS_HEADER_POWERSTONE_FORMAT.formatted(this.magicalPower))
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

            if (this.combatRequirement != null) {
                builder.append("\\n")
                        .append(PowerstoneUtil.COMBAT_REQUIREMENT_POWERSTONE_FORMAT.formatted(this.combatRequirement));
            }

            return builder.toString();
        }

        @Override
        public SkyblockPowerstoneGenerator build() {
            String type = PowerstoneUtil.POWER_STONE_ITEM_TYPE;
            boolean emptyLine = false;
            boolean normalItem = true;
            boolean centeredText = false;
            int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
            boolean renderBorder = true;
            return new SkyblockPowerstoneGenerator(name, rarity, extraLore, type, emptyLine, alpha, padding, normalItem, centeredText, maxLineLength, renderBorder, scalingStats, staticStats, magicalPower, combatRequirement);
        }
    }
}