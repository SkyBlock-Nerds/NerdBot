package net.hypixel.nerdbot.generator.impl.tooltip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.core.Range;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftTooltipGenerator implements Generator {

    public static final int DEFAULT_MAX_LINE_LENGTH = 36;

    private final String name;
    private final Rarity rarity;
    private final String itemLore;
    private final String type;
    private final int alpha;
    private final int padding;
    private final boolean normalItem;
    private final boolean centeredText;
    private final int maxLineLength;
    private final boolean renderBorder;
    private final int scaleFactor;

    @Override
    public @NotNull GeneratedObject render() throws GeneratorException {
        log.debug("Rendering tooltip for '{}'", name);

        // Configure tooltip rendering before generating
        TooltipSettings settings = new TooltipSettings(
            name,
            type,
            alpha,
            padding,
            !normalItem,
            maxLineLength,
            renderBorder,
            centeredText,
            scaleFactor
        );

        MinecraftTooltip tooltip = parseLore(itemLore, settings).render();

        if (tooltip.isAnimated()) {
            try {
                byte[] gifData = ImageUtil.toGifBytes(tooltip.getAnimationFrames(), tooltip.getFrameDelayMs(), true);
                log.debug("Rendered animated tooltip for '{}' ({} frames, delay {}ms)", name, tooltip.getAnimationFrames().size(), tooltip.getFrameDelayMs());
                return new GeneratedObject(gifData, tooltip.getAnimationFrames(), tooltip.getFrameDelayMs());
            } catch (IOException e) {
                throw new GeneratorException("Failed to generate animated tooltip GIF", e);
            }
        } else {
            BufferedImage tooltipImage = tooltip.getImage();
            log.debug("Rendered static tooltip for '{}' (dimensions {}x{})", name, tooltipImage.getWidth(), tooltipImage.getHeight());
            return new GeneratedObject(tooltipImage);
        }
    }

    /**
     * Parses the lore of the item and applies the {@link TooltipSettings} settings.
     *
     * @param input    The lore to parse
     * @param settings The {@link TooltipSettings} to apply
     *
     * @return The parsed {@link MinecraftTooltip}
     */
    public MinecraftTooltip parseLore(String input, TooltipSettings settings) {
        log.debug("Parsing lore for item: {} with TooltipSettings: {}", name, settings);

        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(settings.getPadding())
            .isPaddingFirstLine(settings.isPaddingFirstLine())
            .setRenderBorder(settings.isRenderBorder())
            .isTextCentered(settings.isCenteredText())
            .withAlpha(Range.between(0, 255).fit(settings.getAlpha()))
            .withScaleFactor(settings.getScaleFactor());

        if (settings.getName() != null && !settings.getName().isEmpty()) {
            String name = settings.getName();

            if (rarity != null && rarity != Rarity.byName("NONE")) {
                name = rarity.getColorCode() + name;
            }

            builder.withLines(LineSegment.fromLegacy(TextWrapper.parseLine(name), '&'));
        }

        List<List<LineSegment>> segments = new ArrayList<>();

        for (String line : TextWrapper.wrapString(input, settings.getMaxLineLength())) {
            segments.add(LineSegment.fromLegacy(line, '&'));
        }

        for (List<LineSegment> line : segments) {
            builder.withLines(line);
        }

        if (rarity != null && rarity != Rarity.byName("NONE")) {
            String formattedType = settings.getType() == null || settings.getType().isEmpty() ? "" : " " + settings.getType();
            builder.withLines(LineSegment.fromLegacy(rarity.getFormattedDisplay() + formattedType, '&'));
        }

        return builder.build();
    }

    public enum TooltipSide {
        LEFT,
        RIGHT
    }

    public static class Builder implements ClassBuilder<MinecraftTooltipGenerator> {
        @Getter
        private String itemName;
        private Rarity rarity;
        @Getter
        private String itemLore;
        private String type;
        private Integer alpha = MinecraftTooltip.DEFAULT_ALPHA;
        private Integer padding = 0;
        private boolean paddingFirstLine = true;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private transient boolean bypassMaxLineLength;
        private boolean centered;
        private boolean renderBorder = true;
        private transient int scaleFactor = 1;

        public MinecraftTooltipGenerator.Builder withName(String itemName) {
            this.itemName = itemName;
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

        public MinecraftTooltipGenerator.Builder withScaleFactor(int scaleFactor) {
            this.scaleFactor = Math.max(1, scaleFactor);
            return this;
        }

        public MinecraftTooltipGenerator.Builder parseNbtJson(JsonObject nbtJson) {
            this.paddingFirstLine = false;
            this.centered = false;
            this.rarity = Rarity.byName("NONE");
            this.itemLore = "";
            this.renderBorder = true;
            this.alpha = MinecraftTooltip.DEFAULT_ALPHA;
            this.padding = 0;

            // Check if using new component format (1.20.5+)
            if (nbtJson.has("components")) {
                parseComponents(nbtJson.getAsJsonObject("components"));
            } else if (nbtJson.has("tag")) {
                // Legacy format support
                JsonObject tagObject = nbtJson.get("tag").getAsJsonObject();
                if (tagObject.has("display")) {
                    JsonObject displayObject = tagObject.get("display").getAsJsonObject();

                    // Parse Name if present
                    if (displayObject.has("Name")) {
                        this.itemName = displayObject.get("Name").getAsString();
                        this.itemName = this.itemName.replace(ChatFormat.SECTION_SYMBOL, ChatFormat.AMPERSAND_SYMBOL);
                    }

                    // Parse Lore if present
                    if (displayObject.has("Lore")) {
                        JsonArray loreArray = displayObject.get("Lore").getAsJsonArray();
                        StringBuilder loreBuilder = new StringBuilder();

                        for (int i = 0; i < loreArray.size(); i++) {
                            if (i > 0) {
                                loreBuilder.append("\\n");
                            }

                            loreBuilder.append(loreArray.get(i).getAsString());
                        }

                        this.itemLore = loreBuilder.toString()
                            .replaceAll(String.valueOf(ChatFormat.SECTION_SYMBOL), String.valueOf(ChatFormat.AMPERSAND_SYMBOL));
                    }
                }
            }

            return this;
        }

        private void parseComponents(JsonObject components) {
            // Parse minecraft:custom_name component
            if (components.has("minecraft:custom_name")) {
                JsonObject customName = components.getAsJsonObject("minecraft:custom_name");
                this.itemName = parseTextComponent(customName);
            }

            // Parse minecraft:lore component
            if (components.has("minecraft:lore")) {
                JsonArray loreArray = components.getAsJsonArray("minecraft:lore");
                StringBuilder loreBuilder = new StringBuilder();

                for (int i = 0; i < loreArray.size(); i++) {
                    JsonObject loreEntry = loreArray.get(i).getAsJsonObject();
                    String parsedLine = parseTextComponent(loreEntry);
                    loreBuilder.append(parsedLine);
                    if (i < loreArray.size() - 1) {
                        loreBuilder.append("\\n");
                    }
                }

                this.itemLore = loreBuilder.toString();
            }
        }

        public String getDyeColor(JsonObject nbtJson) {
            return extractDyeColor(nbtJson);
        }

        private String extractDyeColor(JsonObject nbtJson) {
            // Try components format first (1.20.5+)
            if (nbtJson.has("components")) {
                JsonObject components = nbtJson.getAsJsonObject("components");
                if (components.has("minecraft:dyed_color")) {
                    return convertToHexColor(components.get("minecraft:dyed_color").getAsInt(), "component");
                }
            }

            // Try legacy format
            if (nbtJson.has("tag")) {
                JsonObject tag = nbtJson.getAsJsonObject("tag");
                if (tag.has("display") && tag.getAsJsonObject("display").has("color")) {
                    return convertToHexColor(tag.getAsJsonObject("display").get("color").getAsInt(), "legacy");
                }
            }

            return null;
        }

        private String convertToHexColor(int dyeColor, String format) {
            String hexColor = "#" + String.format("%06X", dyeColor & 0xFFFFFF);
            log.debug("Extracted {} dye color: {} -> {}", format, dyeColor, hexColor);
            return hexColor;
        }

        private void addFormattingCode(StringBuilder result, JsonObject component, String key, ChatFormat format) {
            if (component.has(key)) {
                boolean isFormatted = parseBooleanValue(component.get(key));
                if (isFormatted) {
                    result.append("&").append(format.getCode());
                }
            }
        }

        private boolean parseBooleanValue(JsonElement element) {
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isBoolean()) {
                    return element.getAsBoolean();
                }
                if (element.getAsJsonPrimitive().isString()) {
                    String value = element.getAsString();
                    return "1b".equals(value) || "true".equalsIgnoreCase(value);
                }
            }
            return false;
        }

        private String parseTextComponent(JsonObject textComponent) {
            StringBuilder result = new StringBuilder();

            // Handle base text
            if (textComponent.has("text")) {
                String text = textComponent.get("text").getAsString();
                if (!text.isEmpty()) {
                    result.append(text);
                }
            }

            // Handle extra components array
            if (textComponent.has("extra")) {
                JsonArray extraArray = textComponent.getAsJsonArray("extra");
                for (JsonElement extraElement : extraArray) {
                    JsonObject extraComponent = extraElement.getAsJsonObject();

                    // Add color formatting if present
                    if (extraComponent.has("color")) {
                        String colorName = extraComponent.get("color").getAsString();
                        ChatFormat colorFormat = getColorFromComponentName(colorName);
                        if (colorFormat != null && colorFormat.isColor()) {
                            result.append("&").append(colorFormat.getCode());
                        }
                    }

                    // Add formatting codes - handle both boolean and "1b"/"0b" format
                    addFormattingCode(result, extraComponent, "bold", ChatFormat.BOLD);
                    addFormattingCode(result, extraComponent, "italic", ChatFormat.ITALIC);
                    addFormattingCode(result, extraComponent, "underlined", ChatFormat.UNDERLINE);
                    addFormattingCode(result, extraComponent, "strikethrough", ChatFormat.STRIKETHROUGH);
                    addFormattingCode(result, extraComponent, "obfuscated", ChatFormat.OBFUSCATED);

                    // Add the text content
                    if (extraComponent.has("text")) {
                        result.append(extraComponent.get("text").getAsString());
                    }
                }
            }

            return result.toString();
        }

        private ChatFormat getColorFromComponentName(String componentColorName) {
            return switch (componentColorName.toLowerCase()) {
                case "black" -> ChatFormat.BLACK;
                case "dark_blue" -> ChatFormat.DARK_BLUE;
                case "dark_green" -> ChatFormat.DARK_GREEN;
                case "dark_aqua" -> ChatFormat.DARK_AQUA;
                case "dark_red" -> ChatFormat.DARK_RED;
                case "dark_purple" -> ChatFormat.DARK_PURPLE;
                case "gold" -> ChatFormat.GOLD;
                case "gray" -> ChatFormat.GRAY;
                case "dark_gray" -> ChatFormat.DARK_GRAY;
                case "blue" -> ChatFormat.BLUE;
                case "green" -> ChatFormat.GREEN;
                case "aqua" -> ChatFormat.AQUA;
                case "red" -> ChatFormat.RED;
                case "light_purple" -> ChatFormat.LIGHT_PURPLE;
                case "yellow" -> ChatFormat.YELLOW;
                case "white" -> ChatFormat.WHITE;
                default -> null;
            };
        }

        /**
         * Builds a slash command from the current state of the builder.
         *
         * @return A properly formatted slash command string.
         */
        // TODO support player head textures
        public String buildSlashCommand() {
            String baseCommand = System.getProperty("generator.base.command", "gen");
            StringBuilder commandBuilder = new StringBuilder("/" + baseCommand + " item ");
            Field[] fields = this.getClass().getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    if (Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }

                    Object value = field.get(this);
                    if (value != null && !(value instanceof String string && string.isEmpty())) {
                        String paramName = convertCamelCaseToSnakeCase(field.getName());

                        commandBuilder.append(paramName).append(": ");

                        if (value instanceof Boolean bool) {
                            commandBuilder.append(capitalize(bool.toString())); // Discord slash commands use "True" and "False" for booleans
                        } else if (value instanceof String stringValue) {
                            commandBuilder.append(formatCommandString(stringValue));
                        } else {
                            commandBuilder.append(value);
                        }

                        commandBuilder.append(" ");
                    }
                } catch (IllegalAccessException e) {
                    throw new GeneratorException("Failed to build slash command", e);
                }
            }

            return commandBuilder.toString().trim();
        }

        private static String capitalize(String text) {
            if (text == null || text.isEmpty()) return text;
            return text.substring(0, 1).toUpperCase() + text.substring(1);
        }

        private static String formatCommandString(String text) {
            if (text == null) {
                return "";
            }

            String normalized = TextWrapper.normalizeNewlines(text);
            return normalized.replace("\n", "\\n");
        }

        private static String convertCamelCaseToSnakeCase(String camelCase) {
            if (camelCase == null) return null;
            return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
        }

        @Override
        public MinecraftTooltipGenerator build() {
            return new MinecraftTooltipGenerator(
                itemName,
                rarity,
                itemLore,
                type,
                alpha,
                padding,
                !paddingFirstLine, // normalItem is inverse of paddingFirstLine
                centered,
                maxLineLength,
                renderBorder,
                scaleFactor
            );
        }
    }
}
