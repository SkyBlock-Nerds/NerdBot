package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.ClassBuilder;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.parser.StringColorParser;
import net.hypixel.nerdbot.generator.util.GeneratorMessages;
import net.hypixel.nerdbot.generator.util.Item;
import net.hypixel.nerdbot.generator.util.MinecraftTooltip;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;

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
    public Item generate() {
        return new Item(buildItem(name, rarity.name(), itemLore, type, emptyLine, alpha, padding, maxLineLength, normalItem, centeredText));
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

        public MinecraftTooltipGenerator.Builder isCentered(boolean centered) {
            this.centered = centered;
            return this;
        }

        public MinecraftTooltipGenerator.Builder parseNbtJson(JsonObject nbtJson) {
            this.emptyLine = false;
            this.maxLineLength = Integer.MAX_VALUE;
            this.normalItem = false;
            this.centered = false;
            this.rarity = Rarity.NONE;

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
     * Converts text into a Minecraft Item tooltip into a rendered image
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
     * @return a Minecraft item description
     */
    @Nullable
    public BufferedImage buildItem(String name, String rarity, String itemLoreString, String type, boolean addEmptyLine, int alpha, int padding, int maxLineLength, boolean isNormalItem, boolean isCentered) {
        // Checking that the fonts have been loaded correctly
        if (!MinecraftTooltip.isFontsRegistered()) {
            throw new GeneratorException(GeneratorMessages.FONTS_NOT_REGISTERED);
        }

        // verify rarity argument
        if (Arrays.stream(Rarity.VALUES).noneMatch(rarity1 -> rarity.equalsIgnoreCase(rarity1.name()))) {
            throw new GeneratorException(String.format(GeneratorMessages.INVALID_RARITY, GeneratorMessages.stripString(rarity)));
        }

        StringBuilder itemLore = new StringBuilder(itemLoreString);

        // adds the item's name to the array list
        Rarity itemRarity = Rarity.valueOf(rarity.toUpperCase());
        if (!name.equalsIgnoreCase("NONE")) { // allow user to pass NONE for the title
            String createTitle = "%%" + itemRarity.getRarityColor().toString() + "%%" + name + "%%GRAY%%\\n";
            itemLore.insert(0, createTitle);
        }

        // writing the rarity if the rarity is not none
        if (itemRarity != Rarity.NONE) {
            // checks if there is a type for the item
            if (type == null || type.equalsIgnoreCase("none")) {
                type = "";
            }
            // checking if there is custom line break happening
            if (addEmptyLine) {
                itemLore.append("\\n");
            }

            // adds the items type in the item lore
            String createRarity = "\\n%%" + itemRarity.getRarityColor() + "%%%%BOLD%%" + itemRarity.getId().toUpperCase() + " " + type;
            itemLore.append(createRarity);
        } else {
            itemLore.append("\\n");
        }

        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser(maxLineLength);
        colorParser.parseString(itemLore);

        // checking that there were no errors while parsing the string
        if (!colorParser.isSuccessfullyParsed()) {
            throw new GeneratorException(colorParser.getErrorString());
        }

        // alpha value validation
        alpha = Objects.requireNonNullElse(alpha, 255); // checks if the image transparency was set
        alpha = Math.min(255, Math.max(0, alpha));

        // padding value validation
        padding = Math.max(0, padding);

        return new MinecraftTooltip(
            colorParser.getParsedDescription(),
            MCColor.GRAY,
            colorParser.getEstimatedImageWidth() * 30,
            alpha,
            padding,
            isNormalItem,
            isCentered
        )
            .render()
            .getImage();
    }

    public enum TooltipSide {
        LEFT,
        RIGHT;
    }
}