package net.hypixel.nerdbot.generator.impl;

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
import net.hypixel.nerdbot.util.Range;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftTooltipGenerator implements Generator {

    private static final int MAX_LINE_LENGTH = 32;
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&[0-9a-fk-or]");

    private final String name;
    private final Rarity rarity;
    private final String itemLore;
    private final String type;
    private final boolean emptyLine;
    private final int alpha;
    private final int padding;
    private final boolean normalItem;
    private final boolean centeredText;

    @Override
    public GeneratedObject generate() {
        return new GeneratedObject(buildItem(name, itemLore, type, emptyLine, alpha, padding, normalItem, centeredText));
    }

    /**
     * Converts text into a rendered image
     *
     * @param name             the name of the item
     * @param itemLoreString   the lore of the item
     * @param type             the type of the item
     * @param addEmptyLine     if there should be an extra line added between the lore and the final type line
     * @param alpha            the transparency of the generated image
     * @param padding          if there is any extra padding around the edges to prevent Discord from rounding the corners
     * @param paddingFirstLine if the item should add an extra line between the title and first line
     *
     * @return a Minecraft item tooltip as a rendered image
     */
    @Nullable
    public BufferedImage buildItem(String name, String itemLoreString, String type, boolean addEmptyLine, int alpha, int padding, boolean paddingFirstLine, boolean isCentered) {
        MinecraftTooltip parsedLore = parseLore(name, itemLoreString, addEmptyLine, type, alpha, padding, paddingFirstLine);
        return parsedLore.render().getImage();
    }

    public MinecraftTooltip parseLore(String name, String input, boolean emptyLine, String type, int alpha, int padding, boolean paddingFirstLine) {
        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(padding)
            .isPaddingFirstLine(paddingFirstLine)
            .withAlpha(Range.between(0, 255).fit(alpha));

        if (name != null && !name.isEmpty()) {
            builder.withLines(LineSegment.fromLegacy(rarity.getColorCode() + name, '&'));
        }

        List<String> segments = new ArrayList<>();
        for (String line : input.split("\\\\n")) {
            String parsed = Parser.parseString(line, List.of(
                new ColorCodeParser(),
                new IconParser(),
                new StatParser(),
                new GemstoneParser()
            ));

            segments.add(parsed);
        }

        for (String segment : segments) {
            String[] words = segment.split(" ");
            StringBuilder currentLine = new StringBuilder();
            String lastColorCode = "";

            for (String word : words) {
                lastColorCode = findLastColorCode(word, lastColorCode);

                while (word.length() > MAX_LINE_LENGTH) {
                    String part = word.substring(0, MAX_LINE_LENGTH);
                    builder.withLines(LineSegment.fromLegacy(part, '&'));
                    word = lastColorCode + word.substring(MAX_LINE_LENGTH);
                }

                if (currentLine.length() + word.length() > MAX_LINE_LENGTH) {
                    builder.withLines(LineSegment.fromLegacy(currentLine.toString().trim(), '&'));
                    currentLine = new StringBuilder(lastColorCode);
                }

                currentLine.append(word).append(" ");
            }

            if (!currentLine.isEmpty()) {
                builder.withLines(LineSegment.fromLegacy(currentLine.toString().trim(), '&'));
            }
        }

        if (rarity != null && rarity != Rarity.NONE) {
            if (emptyLine) {
                builder.withEmptyLine();
            }

            builder.withLines(LineSegment.fromLegacy(rarity.getFormattedDisplay() + " " + type, '&'));
        }

        return builder.build();
    }

    private static String findLastColorCode(String word, String lastColorCode) {
        Matcher matcher = COLOR_CODE_PATTERN.matcher(word);
        while (matcher.find()) {
            lastColorCode = matcher.group();
        }
        return lastColorCode;
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

        public MinecraftTooltipGenerator.Builder isPaddingFirstLine(boolean paddingFirstLine) {
            this.paddingFirstLine = paddingFirstLine;
            return this;
        }

        public MinecraftTooltipGenerator.Builder isTextCentered(boolean centered) {
            this.centered = centered;
            return this;
        }

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
            return new MinecraftTooltipGenerator(name, rarity, itemLore, type, emptyLine, alpha, padding, paddingFirstLine, centered);
        }
    }
}
