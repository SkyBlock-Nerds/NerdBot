package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.GeneratedItem;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.generator.parser.text.ColorCodeParser;
import net.hypixel.nerdbot.generator.parser.text.IconParser;
import net.hypixel.nerdbot.generator.parser.text.StatParser;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.util.GeneratorMessages;
import net.hypixel.nerdbot.generator.util.MinecraftTooltip;
import net.hypixel.nerdbot.util.Range;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftTooltipGenerator implements Generator {

    private final String name;
    private final Rarity rarity;
    private final String itemLore;
    private final String type;
    private final boolean emptyLine;
    private final int alpha;
    private final int padding;
    private final int maxLineLength;
    private final boolean normalItem;
    private final boolean centeredText;

    @Override
    public GeneratedItem generate() {
        return new GeneratedItem(buildItem(name, rarity, itemLore, type, emptyLine, alpha, padding, maxLineLength, normalItem, centeredText));
    }

    public static class Builder implements ClassBuilder<MinecraftTooltipGenerator> {
        private String name;
        private Rarity rarity;
        private String itemLore;
        private String type;
        private Boolean emptyLine;
        private Integer alpha;
        private Integer padding;
        private Integer maxLineLength;
        private boolean normalItem;
        private boolean centered;

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

        public MinecraftTooltipGenerator.Builder withMaxLineLength(int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }

        public MinecraftTooltipGenerator.Builder isNormalItem(boolean normalItem) {
            this.normalItem = normalItem;
            return this;
        }

        public MinecraftTooltipGenerator.Builder isTextCentered(boolean centered) {
            this.centered = centered;
            return this;
        }

        public MinecraftTooltipGenerator.Builder parseNbtJson(JsonObject nbtJson) {
            this.emptyLine = false;
            this.maxLineLength = Integer.MAX_VALUE;
            this.normalItem = false;
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
            return new MinecraftTooltipGenerator(name, rarity, itemLore, type, emptyLine, alpha, padding, maxLineLength, normalItem, centered);
        }
    }

    /**
     * Converts text into a rendered image
     *
     * @param name           the name of the item
     * @param rarity         the rarity of the item
     * @param itemLoreString the lore of the item
     * @param type           the type of the item
     * @param addEmptyLine   if there should be an extra line added between the lore and the final type line
     * @param alpha          the transparency of the generated image
     * @param padding        if there is any extra padding around the edges to prevent Discord from rounding the corners
     * @param maxLineLength  the maximum length before content overflows onto the next
     * @param isNormalItem   if the item should add an extra line between the title and first line
     *
     * @return a Minecraft item tooltip as a rendered image
     */
    @Nullable
    public BufferedImage buildItem(String name, Rarity rarity, String itemLoreString, String type, boolean addEmptyLine, int alpha, int padding, int maxLineLength, boolean isNormalItem, boolean isCentered) {
        if (rarity == null) {
            rarity = Rarity.NONE;
        }

        if (Util.findValue(Rarity.VALUES, rarity.name()) == null) {
            throw new GeneratorException(GeneratorMessages.INVALID_RARITY);
        }

        MinecraftTooltip parsedLore = parseLore(name, itemLoreString, addEmptyLine, type, maxLineLength, alpha, padding);
        return parsedLore.render().getImage();
    }

    private MinecraftTooltip parseLore(String name, String input, boolean emptyLine, String type, int maxLineLength, int alpha, int padding) {
        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(padding)
            .withAlpha(Range.between(0, 255).fit(alpha));

        if (name != null && !name.isEmpty()) {
            builder.withLines(LineSegment.fromLegacy(rarity.getColorCode() + name, '&'));
        }

        for (String line : input.split("\\\\n")) {
            String parsed = Parser.parseString(line, List.of(
                new ColorCodeParser(),
                new IconParser(),
                new StatParser()
            ));

            builder.withLines(LineSegment.fromLegacy(parsed, '&'));
        }

        if (rarity != null && rarity != Rarity.NONE) {
            if (emptyLine) {
                builder.withEmptyLine();
            }

            builder.withLines(LineSegment.fromLegacy(rarity.getFormattedDisplay() + " " + type, '&'));
        }

        return builder.build();
    }

    public enum TooltipSide {
        LEFT,
        RIGHT
    }
}
