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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

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

    @Override
    public GeneratedObject generate() {
        return new GeneratedObject(buildItem(name, itemLore, type, emptyLine, alpha, padding, normalItem, maxLineLength));
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
    public BufferedImage buildItem(String name, String itemLoreString, String type, boolean addEmptyLine, int alpha, int padding, boolean paddingFirstLine, int maxLineLength) {
        MinecraftTooltip parsedLore = parseLore(name, itemLoreString, addEmptyLine, type, alpha, padding, paddingFirstLine, maxLineLength);
        return parsedLore.render().getImage();
    }

    public MinecraftTooltip parseLore(String name, String input, boolean emptyLine, String type, int alpha, int padding, boolean paddingFirstLine, int maxLineLength) {
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

        List<List<LineSegment>> lines = splitLines(segments);
        for (List<LineSegment> line : lines) {
            builder.withLines(line);
        }

        if (rarity != null && rarity != Rarity.NONE) {
            if (emptyLine) {
                builder.withEmptyLine();
            }

            builder.withLines(LineSegment.fromLegacy(rarity.getFormattedDisplay() + " " + type, '&'));
        }

        return builder.build();
    }

    private List<List<LineSegment>> splitLines(List<String> lines) {
        List<List<LineSegment>> output = new CopyOnWriteArrayList<>();

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                return output;
            }

            // split text into segments based on newline characters
            String[] segments = line.split("\\n");
            for (String segment : segments) {
                output.addAll(wrapSegment(segment, maxLineLength));
            }
        }

        return output;
    }

    private List<List<LineSegment>> wrapSegment(String text, int maxLineLength) {
        List<List<LineSegment>> lines = new CopyOnWriteArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        String lastColorCode = "";

        String[] words = text.split("\\s+"); // split text by whitespace characters

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLineLength) {
                // If adding the next word exceeds max length, add currentLine to the list
                String lineToAdd = currentLine.toString().trim();
                if (!lineToAdd.startsWith("&")) {
                    lineToAdd = lastColorCode + lineToAdd;
                }
                lines.add(LineSegment.fromLegacy(lineToAdd, '&'));
                lastColorCode = getLastColorCode(lineToAdd);
                currentLine.setLength(0); // reset the current line
            }

            // Add next word to the current line
            if (!currentLine.isEmpty()) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        // Add the last line if it's not empty
        if (!currentLine.isEmpty()) {
            String lastLine = currentLine.toString().trim();
            if (!lastLine.startsWith("&")) {
                lastLine = lastColorCode + lastLine;
            }
            lines.add(LineSegment.fromLegacy(lastLine, '&'));
        } else if (!text.contains(" ")) { // Handle case with no spaces
            for (int i = 0; i < text.length(); i += maxLineLength) {
                String part = text.substring(i, Math.min(i + maxLineLength, text.length()));
                if (!part.startsWith("&")) {
                    part = lastColorCode + part;
                }
                lines.add(LineSegment.fromLegacy(part, '&'));
                lastColorCode = getLastColorCode(part);
            }
        }

        return lines;
    }

    private static String getLastColorCode(String text) {
        String colorCode = "";

        for (int i = text.length() - 2; i >= 0; i--) {
            if (text.charAt(i) == '&') {
                colorCode = text.substring(i, i + 2);
                break;
            }
        }

        return colorCode;
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
            return new MinecraftTooltipGenerator(name, rarity, itemLore, type, emptyLine, alpha, padding, paddingFirstLine, centered, maxLineLength);
        }
    }
}
