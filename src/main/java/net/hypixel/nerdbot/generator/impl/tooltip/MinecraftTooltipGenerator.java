package net.hypixel.nerdbot.generator.impl.tooltip;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.generator.parser.text.ColorCodeParser;
import net.hypixel.nerdbot.generator.parser.text.GemstoneParser;
import net.hypixel.nerdbot.generator.parser.text.IconParser;
import net.hypixel.nerdbot.generator.parser.text.StatParser;
import net.hypixel.nerdbot.generator.skyblock.Rarity;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;
import net.hypixel.nerdbot.util.Range;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftTooltipGenerator implements Generator {

    public static final int DEFAULT_MAX_LINE_LENGTH = 32;

    private final String name;
    private final Rarity rarity;
    private final String itemLore;
    private final String type;
    private final boolean emptyLine;
    private final int alpha;
    private final int padding;
    private final boolean normalItem;
    private final boolean centeredText;
    private final int maxLineLength;
    private final boolean renderBorder;

    @Override
    public GeneratedObject generate() {
        TooltipSettings settings = new TooltipSettings(
            name,           // Name of the item
            emptyLine,      // Whether to add an empty line
            type,           // Type of the item
            alpha,          // Alpha value
            padding,        // Padding value
            normalItem,     // Whether to pad the first line
            maxLineLength,  // Maximum line length
            renderBorder    // Whether to render a border around the tooltip
        );

        return new GeneratedObject(buildItem(itemLore, settings));
    }

    /**
     * Builds an item tooltip image from a string of lore.
     *
     * @param itemLoreString The lore string to parse
     * @param settings       The {@link TooltipSettings settings} to use for the generated tooltip image
     *
     * @return The generated tooltip image
     */
    @Nullable
    public BufferedImage buildItem(String itemLoreString, TooltipSettings settings) {
        MinecraftTooltip parsedLore = parseLore(itemLoreString, settings);
        return parsedLore.render().getImage();
    }

    public MinecraftTooltip parseLore(String input, TooltipSettings settings) {
        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(settings.getPadding())
            .isPaddingFirstLine(settings.isPaddingFirstLine())
            .setRenderBorder(settings.isRenderBorder())
            .withAlpha(Range.between(0, 255).fit(settings.getAlpha()));

        if (settings.getName() != null && !settings.getName().isEmpty()) {
            builder.withLines(LineSegment.fromLegacy(rarity.getColorCode() + settings.getName(), '&'));
        }

        List<String> segments = new ArrayList<>();
        for (String line : input.split("\n")) {
            String parsed = Parser.parseString(line, List.of(
                new ColorCodeParser(),
                new IconParser(),
                new StatParser(),
                new GemstoneParser()
            ));
            segments.add(parsed);
        }

        List<List<LineSegment>> lines = TextWrapper.splitLines(segments, settings.getMaxLineLength());
        for (List<LineSegment> line : lines) {
            builder.withLines(line);
        }

        if (rarity != null && rarity != Rarity.NONE) {
            if (settings.isEmptyLine()) {
                builder.withEmptyLine();
            }
            builder.withLines(LineSegment.fromLegacy(rarity.getFormattedDisplay() + " " + settings.getType(), '&'));
        }

        return builder.build();
    }

    public enum TooltipSide {
        LEFT,
        RIGHT
    }

    public static class Builder implements ClassBuilder<MinecraftTooltipGenerator> {
        private String name;
        private Rarity rarity;
        private String itemLore;
        private String type;
        private Boolean emptyLine;
        private Integer alpha;
        private Integer padding;
        private boolean paddingFirstLine;
        private int maxLineLength;
        private boolean bypassMaxLineLength;
        private boolean centered;
        private boolean renderBorder;

        public MinecraftTooltipGenerator.Builder withName(String name) {
            this.name = name;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withRarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withItemLore(String itemLore) {
            this.itemLore = itemLore;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withType(String type) {
            this.type = type;
            return this;
        }

        public MinecraftTooltipGenerator.Builder withEmptyLine(boolean emptyLine) {
            this.emptyLine = emptyLine;
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

        public MinecraftTooltipGenerator.Builder isPaddingFirstLine(boolean paddingFirstLine) {
            this.paddingFirstLine = paddingFirstLine;
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

        // TODO support components
        public MinecraftTooltipGenerator.Builder parseNbtJson(JsonObject nbtJson) {
            this.emptyLine = false;
            this.paddingFirstLine = false;
            this.centered = false;
            this.rarity = Rarity.NONE;
            this.itemLore = "";

            JsonObject tagObject = nbtJson.get("tag").getAsJsonObject();
            JsonObject displayObject = tagObject.get("display").getAsJsonObject();
            this.name = displayObject.getAsJsonObject().get("Name").getAsString();

            displayObject.getAsJsonObject().get("Lore").getAsJsonArray().forEach(jsonElement -> {
                this.itemLore += jsonElement.getAsString() + "\\n";
            });

            return this;
        }

        @Override
        public MinecraftTooltipGenerator build() {
            return new MinecraftTooltipGenerator(name, rarity, itemLore, type, emptyLine, alpha, padding, paddingFirstLine, centered, maxLineLength, renderBorder);
        }
    }
}
