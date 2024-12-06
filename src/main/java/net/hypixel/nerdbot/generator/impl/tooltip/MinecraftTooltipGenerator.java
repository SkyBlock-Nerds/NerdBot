package net.hypixel.nerdbot.generator.impl.tooltip;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;
import net.hypixel.nerdbot.util.Range;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class MinecraftTooltipGenerator<settings extends TooltipSettings> implements Generator {

    public static final int DEFAULT_MAX_LINE_LENGTH = 36;

    protected final String name;
    protected final boolean renderBorder;
    protected final int maxLineLength;
    protected final int padding;
    protected final int alpha;
    protected final String itemLore;
    protected final boolean centeredText; // TODO: implement

    @Override
    public GeneratedObject generate() {
        TooltipSettings settings = new TooltipSettings(
            itemLore,
            name,
            alpha,
            padding,
            maxLineLength,
            renderBorder    // Whether to render a border around the tooltip
        );

        return new GeneratedObject(buildItem((settings) settings));
    }

    /**
     * Builds an item tooltip image from a string of lore.
     *
     * @param settings       The {@link TooltipSettings settings} to use for the generated tooltip image
     *
     * @return The generated tooltip image
     */
    @Nullable
    public BufferedImage buildItem(settings settings) {
        return parseLore(settings).render().getImage();
    }

    public MinecraftTooltip parseLore(settings settings) {
        log.debug("Parsing lore for item: {} with TooltipSettings: {}", settings.getName(), settings);

        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(settings.getPadding())
            .setRenderBorder(settings.isRenderBorder())
            .withAlpha(Range.between(0, 255).fit(settings.getAlpha()));

        if (settings.getName() != null && !settings.getName().isEmpty()) {
            String name = settings.getName();

            builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(name), '&'));
        }

        for (List<LineSegment> line : stringLoreToLineSegements(settings.getLore(), settings.getMaxLineLength())) {
            builder.withLines(line);
        }

        return builder.build();
    }

    protected List<List<LineSegment>> stringLoreToLineSegements (String input, int maxLineLength) {
        List<List<LineSegment>> segments = new ArrayList<>();

        for (String line : TextWrapper.wrapString(input, maxLineLength)) {
            segments.add(LineSegment.fromLegacy(line, '&'));
        }

        return segments;
    }

    public enum TooltipSide {
        LEFT,
        RIGHT
    }

    public static class Builder implements ClassBuilder<MinecraftTooltipGenerator<TooltipSettings>> {
        private String name;
        private String itemLore;
        private Integer alpha;
        private Integer padding;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private transient boolean bypassMaxLineLength;
        private boolean centered;
        private transient boolean renderBorder;

        public MinecraftTooltipGenerator.Builder withName(String name) {
            this.name = name;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withItemLore(String itemLore) {
            this.itemLore = itemLore;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withAlpha(int alpha) {
            this.alpha = alpha;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withPadding(int padding) {
            this.padding = padding;
            return this;
        }

        public MinecraftTooltipGenerator.Builder isTextCentered(boolean centered) {
            this.centered = centered;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withMaxLineLength(int maxLineLength) {
            if (bypassMaxLineLength) {
                this.maxLineLength = maxLineLength;
            } else {
                this.maxLineLength = MinecraftTooltip.LINE_LENGTH.fit(maxLineLength);
            }
            return this;
        }

        public MinecraftTooltipGenerator.Builder bypassMaxLineLength(boolean bypassMaxLineLength) {
            this.bypassMaxLineLength = bypassMaxLineLength;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withRenderBorder(boolean renderBorder) {
            this.renderBorder = renderBorder;
            return this;
        }

        public String buildSlashCommand() {
            StringBuilder commandBuilder = new StringBuilder("/" + GeneratorCommands.BASE_COMMAND + " item full"); //TODO if there ever is a more suitable command use that instead

            if (name != null && !name.isEmpty()) {
                commandBuilder.append(" name:").append(name);
            }

            if (itemLore != null && !itemLore.isEmpty()) {
                commandBuilder.append(" item_lore:").append(itemLore);
            }

            if (alpha != null) {
                commandBuilder.append(" alpha:").append(alpha);
            }

            if (padding != null) {
                commandBuilder.append(" padding:").append(padding);
            }

            commandBuilder.append(" max_line_length:").append(maxLineLength);

            if (bypassMaxLineLength) {
                commandBuilder.append(" bypass_max_line_length:true");
            }

            commandBuilder.append(" centered:").append(centered);

            commandBuilder.append(" render_border:").append(renderBorder);

            return commandBuilder.toString();
        }

        @Override
        public MinecraftTooltipGenerator<TooltipSettings> build() {
            return new MinecraftTooltipGenerator<>(name, renderBorder, maxLineLength, padding, alpha, itemLore, centered);
        }
    }
}