package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
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
        return new GeneratedObject(buildItem(name, itemLore, type, emptyLine, alpha, padding, normalItem, maxLineLength, renderBorder));
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
    public BufferedImage buildItem(String name, String itemLoreString, String type, boolean addEmptyLine, int alpha, int padding, boolean paddingFirstLine, int maxLineLength, boolean renderBorder) {
        MinecraftTooltip parsedLore = parseLore(name, itemLoreString, addEmptyLine, type, alpha, padding, paddingFirstLine, maxLineLength, renderBorder);
        return parsedLore.render().getImage();
    }

    public MinecraftTooltip parseLore(String name, String input, boolean emptyLine, String type, int alpha, int padding, boolean paddingFirstLine, int maxLineLength, boolean renderBorder) {
        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(padding)
            .isPaddingFirstLine(paddingFirstLine)
            .setRenderBorder(renderBorder)
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

        List<List<LineSegment>> lines = splitLines(segments, maxLineLength);
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

    private List<List<LineSegment>> splitLines(List<String> lines, int maxLineLength) {
        List<List<LineSegment>> output = new CopyOnWriteArrayList<>();

        for (String line : lines) {
            // adds blank line if the line is empty, since this seems to only trigger when using two newline characters in a row
            if (line == null || line.isBlank()) {
                output.add(LineSegment.fromLegacy(" ", '&'));
                continue;
            }

            // split text into segments based on newline characters
            String[] segments = line.split("\\n");
            for (String segment : segments) {
                output.addAll(wrapSegment(segment, maxLineLength));
            }
        }

        // throw an exception if every line is empty
        if (output.stream().allMatch(List::isEmpty)) {
            throw new GeneratorException("You cannot generate an empty tooltip!");
        }

        return output;
    }

    private List<List<LineSegment>> wrapSegment(String text, int maxLineLength) {
        List<List<LineSegment>> lines = new CopyOnWriteArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        String lastColorCode = "";
        String lastFormattingCodes = "";

        String[] words = text.split("\\s+"); // split text by whitespace characters

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLineLength) {
                // If adding the next word exceeds max length, add currentLine to the list
                String lineToAdd = currentLine.toString().trim();
                if (!lineToAdd.startsWith("&")) {
                    lineToAdd = lastColorCode + lastFormattingCodes + lineToAdd;
                }
                lines.add(LineSegment.fromLegacy(lineToAdd, '&'));
                lastColorCode = getLastColorCode(lineToAdd);
                lastFormattingCodes = getLastFormattingCodes(lineToAdd);
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
                lastLine = lastColorCode + lastFormattingCodes + lastLine;
            }
            lines.add(LineSegment.fromLegacy(lastLine, '&'));
        } else if (!text.contains(" ")) { // Handle case with no spaces
            for (int i = 0; i < text.length(); i += maxLineLength) {
                String part = text.substring(i, Math.min(i + maxLineLength, text.length()));
                if (!part.startsWith("&")) {
                    part = lastColorCode + lastFormattingCodes + part;
                }
                lines.add(LineSegment.fromLegacy(part, '&'));
                lastColorCode = getLastColorCode(part);
                lastFormattingCodes = getLastFormattingCodes(part);
            }
        }

        return lines;
    }

    private String getLastColorCode(String text) {
        String colorCode = "";
        for (int i = text.length() - 2; i >= 0; i--) {
            if (text.charAt(i) == '&' && (i + 1 < text.length())) {
                char code = text.charAt(i + 1);
                if (Character.isLetterOrDigit(code)) {
                    colorCode = text.substring(i, i + 2);
                    if ("0123456789abcdef".indexOf(code) != -1) {
                        break;
                    }
                }
            }
        }
        return colorCode;
    }

    private String getLastFormattingCodes(String text) {
        StringBuilder formattingCodes = new StringBuilder();
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '&' && (i + 1 < text.length())) {
                char code = text.charAt(i + 1);
                if ("klmnor".indexOf(code) != -1) {
                    formattingCodes.append(text, i, i + 2);
                }
            }
        }
        return formattingCodes.toString();
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
