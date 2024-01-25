package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonArray;
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
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
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
        return new Item(buildItem(name, rarity, itemLore, type, emptyLine, alpha, padding, maxLineLength, normalItem, centeredText));
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
    public BufferedImage buildItem(String name, Rarity rarity, String itemLoreString, String type, boolean addEmptyLine, int alpha, int padding, int maxLineLength, boolean isNormalItem, boolean isCentered) {
        // Checking that the fonts have been loaded correctly
        if (!MinecraftTooltip.isFontsRegistered()) {
            throw new GeneratorException(GeneratorMessages.FONTS_NOT_REGISTERED);
        }

        if (rarity == null) {
            rarity = Rarity.NONE;
        }

        // Check if the given rarity is a valid enum value
        if (Util.findValue(Rarity.VALUES, rarity.name()) == null) {
            throw new GeneratorException(GeneratorMessages.INVALID_RARITY);
        }

        StringColorParser parsedLore = parseLore(emptyLine, itemLore);

        // alpha value validation
        alpha = Objects.requireNonNullElse(alpha, 255); // checks if the image transparency was set
        alpha = Math.min(255, Math.max(0, alpha));

        // padding value validation
        padding = Math.max(0, padding);

        return new MinecraftTooltip(
            parsedLore.getParsedDescription(),
            MCColor.GRAY.getColor(),
            parsedLore.getEstimatedImageWidth() * 30,
            alpha,
            padding,
            isNormalItem,
            isCentered
        ).render().getImage();
    }

    private StringColorParser parseLore(boolean emptyLine, String input) {
        StringBuilder itemLore = new StringBuilder(input);
        String type = this.type;

        // adds the item's name to the array list
        if (name != null && !name.equalsIgnoreCase("NONE")) { // allow user to pass NONE for the title
            String createTitle = "%%" + rarity.getRarityColor().toString() + "%%" + name + "%%GRAY%%\\n";
            itemLore.insert(0, createTitle);
        }

        // writing the rarity if the rarity is not none
        if (rarity != null && rarity != Rarity.NONE) {
            // checks if there is a type for the item
            if (type == null || type.equalsIgnoreCase("none")) {
                type = "";
            }
            // checking if there is custom line break happening
            if (emptyLine) {
                itemLore.append("\\n");
            }

            // adds the items type in the item lore
            String createRarity = "\\n%%" + rarity.getRarityColor() + "%%%%BOLD%%" + rarity.getId().toUpperCase() + " " + type;
            itemLore.append(createRarity);
        } else {
            itemLore.append("\\n");
        }

        // Replace all section symbols to & symbols
        itemLore = new StringBuilder(itemLore.toString().replace("§", "&"));

        System.out.println("Parsing item lore: " + itemLore);

        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser(maxLineLength);
        colorParser.parseString(itemLore);

        System.out.println("Parsed item lore: " + colorParser.getParsedDescription());

        // checking that there were no errors while parsing the string
        if (!colorParser.parsedSuccessfully()) {
            throw new GeneratorException(colorParser.getErrorString());
        }

        return colorParser;
    }

    // Example:
    // {display:{Name:'[{"text":"Name of ","italic":false,"color":"yellow"},{"text":"Item","color":"gold"}]',Lore:['[{"text":"This is text lore.","italic":false,"color":"gray"},{"text":"","italic":false,"color":"dark_purple"}]','[{"text":"Line break!","italic":false,"color":"gray"},{"text":"","italic":false,"color":"dark_purple"}]','[{"text":"Line break OF COLOR RED and BOLD","italic":false,"color":"dark_red","bold":true}]']}} 1
    public JsonObject generateNbtJson() {
        StringColorParser itemLore = parseLore(this.emptyLine, this.itemLore);
        JsonObject nbtJson = new JsonObject();
        JsonObject displayJson = new JsonObject();
        JsonArray nameJson = new JsonArray();
        JsonArray loreJson = new JsonArray();

        //nameJson.add(itemLore.getParsedDescription().get(0).get(0).convertToJson());

        itemLore.getParsedDescription().stream()
            .skip(1)
            .forEach(coloredStrings -> {
                coloredStrings.forEach(coloredString -> {
                    JsonArray coloredStringJson = new JsonArray();
                    //coloredStringJson.add(coloredString.convertToJson());
                    loreJson.add(coloredStringJson);
                });
            });

        displayJson.add("Name", nameJson);
        displayJson.add("Lore", loreJson);
        nbtJson.add("display", displayJson);

        return nbtJson;
    }

    public enum TooltipSide {
        LEFT,
        RIGHT;
    }
}
