package net.hypixel.nerdbot.generator.impl.tooltip;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;
import net.hypixel.nerdbot.util.Range;
import net.hypixel.nerdbot.util.Util;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftTooltipGenerator implements Generator {

    public static final int DEFAULT_MAX_LINE_LENGTH = 36;

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
        return parseLore(itemLoreString, settings).render().getImage();
    }

    public MinecraftTooltip parseLore(String input, TooltipSettings settings) {
        log.debug("Parsing lore for item: {} with TooltipSettings: {}", name, settings);

        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(settings.getPadding())
            .isPaddingFirstLine(settings.isPaddingFirstLine())
            .setRenderBorder(settings.isRenderBorder())
            .withAlpha(Range.between(0, 255).fit(settings.getAlpha()));

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
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private transient boolean bypassMaxLineLength;
        private boolean centered;
        private transient boolean renderBorder;

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
            this.rarity = Rarity.byName("NONE");
            this.itemLore = "";

            JsonObject tagObject = nbtJson.get("tag").getAsJsonObject();
            JsonObject displayObject = tagObject.get("display").getAsJsonObject();
            this.name = displayObject.getAsJsonObject().get("Name").getAsString();

            displayObject.getAsJsonObject().get("Lore").getAsJsonArray().forEach(jsonElement -> {
                this.itemLore += jsonElement.getAsString() + "\\n";
            });

            this.itemLore = this.itemLore.replaceAll(String.valueOf(ChatFormat.SECTION_SYMBOL), String.valueOf(ChatFormat.AMPERSAND_SYMBOL));

            return this;
        }

        /**
         * Builds a slash command from the current state of the builder.
         *
         * @return A properly formatted slash command string.
         */
        public String buildSlashCommand() {
            StringBuilder commandBuilder = new StringBuilder("/" + GeneratorCommands.BASE_COMMAND + " item ");
            Field[] fields = this.getClass().getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    int modifiers = field.getModifiers();
                    if (Modifier.isTransient(modifiers)) {
                        continue;
                    }

                    Object value = field.get(this);
                    if (value != null && !(value instanceof String string && string.isEmpty())) {
                        String paramName = Util.convertCamelCaseToSnakeCase(field.getName());

                        commandBuilder.append(paramName).append(": ");

                        if (value instanceof Boolean bool) {
                            commandBuilder.append(StringUtils.capitalize(bool.toString())); // Discord slash commands use "True" and "False" for booleans
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

        @Override
        public MinecraftTooltipGenerator build() {
            return new MinecraftTooltipGenerator(name, rarity, itemLore, type, emptyLine, alpha, padding, paddingFirstLine, centered, maxLineLength, renderBorder);
        }
    }
}
