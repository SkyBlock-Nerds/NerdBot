package net.hypixel.nerdbot.generator.impl.tooltip.skyblock;

import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.impl.tooltip.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.text.wrapper.TextWrapper;
import net.hypixel.nerdbot.util.Range;
import net.hypixel.nerdbot.util.Util;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SkyblockItemGenerator extends MinecraftTooltipGenerator<SkyblockItemSettings> {

    protected final Rarity rarity;
    protected final String type;
    protected final boolean emptyLine;
    protected final boolean normalItem;

    protected SkyblockItemGenerator(String name, Rarity rarity, String itemLore, String type, boolean emptyLine, int alpha, int padding, boolean normalItem, boolean centeredText, int maxLineLength, boolean renderBorder) {
        super(
            name,
            renderBorder,
            maxLineLength,
            padding,
            alpha,
            itemLore,
            centeredText
        );
        this.rarity = rarity;
        this.type = type;
        this.emptyLine = emptyLine;
        this.normalItem = normalItem;
    }

    @Override
    public GeneratedObject generate() {
        SkyblockItemSettings settings = new SkyblockItemSettings(
            itemLore,
            name,
            alpha,
            padding,
            maxLineLength,
            renderBorder,
            rarity,
            emptyLine,
            type,
            normalItem
        );

        return new GeneratedObject(buildItem(settings));
    }

    @Override
    public MinecraftTooltip parseLore(SkyblockItemSettings settings) {
        log.debug("Parsing lore for item: {} with SkyblockItemSettings: {}", settings.getName(), settings);

        MinecraftTooltip.Builder builder = MinecraftTooltip.builder()
            .withPadding(settings.getPadding())
            .isPaddingFirstLine(settings.isNormalItem())
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

        for (String line : TextWrapper.wrapString(settings.getLore(), settings.getMaxLineLength())) {
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

    public static class Builder implements ClassBuilder<SkyblockItemGenerator> {
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

        public SkyblockItemGenerator.Builder withName(String name) {
            this.name = name;
            return this;
        }

        public SkyblockItemGenerator.Builder withRarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public SkyblockItemGenerator.Builder withItemLore(String itemLore) {
            this.itemLore = itemLore;
            return this;
        }

        public SkyblockItemGenerator.Builder withType(String type) {
            this.type = type;
            return this;
        }

        public SkyblockItemGenerator.Builder withEmptyLine(boolean emptyLine) {
            this.emptyLine = emptyLine;
            return this;
        }

        public SkyblockItemGenerator.Builder withAlpha(int alpha) {
            this.alpha = alpha;
            return this;
        }

        public SkyblockItemGenerator.Builder withPadding(int padding) {
            this.padding = padding;
            return this;
        }

        public SkyblockItemGenerator.Builder isPaddingFirstLine(boolean paddingFirstLine) {
            this.paddingFirstLine = paddingFirstLine;
            return this;
        }

        public SkyblockItemGenerator.Builder isTextCentered(boolean centered) {
            this.centered = centered;
            return this;
        }

        public SkyblockItemGenerator.Builder withMaxLineLength(int maxLineLength) {
            if (bypassMaxLineLength) {
                this.maxLineLength = maxLineLength;
            } else {
                this.maxLineLength = MinecraftTooltip.LINE_LENGTH.fit(maxLineLength);
            }
            return this;
        }

        public SkyblockItemGenerator.Builder bypassMaxLineLength(boolean bypassMaxLineLength) {
            this.bypassMaxLineLength = bypassMaxLineLength;
            return this;
        }

        public SkyblockItemGenerator.Builder withRenderBorder(boolean renderBorder) {
            this.renderBorder = renderBorder;
            return this;
        }

        // TODO support components
        public SkyblockItemGenerator.Builder parseNbtJson(JsonObject nbtJson) {
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
            StringBuilder commandBuilder = new StringBuilder("/" + GeneratorCommands.BASE_COMMAND + " item full ");
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
        public SkyblockItemGenerator build() {
            return new SkyblockItemGenerator(name, rarity, itemLore, type, emptyLine, alpha, padding, paddingFirstLine, centered, maxLineLength, renderBorder);
        }
    }
}