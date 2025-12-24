package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.TooManyTexturesException;
import net.hypixel.nerdbot.generator.impl.tooltip.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.parser.text.PlaceholderReverseMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class MinecraftNbtParser {

    public static ParsedNbt Parse(String nbt) {
        JsonObject jsonObject = JsonParser.parseString(nbt).getAsJsonObject();
        ArrayList<ClassBuilder<? extends Generator>> generators = new ArrayList<>();

        if (jsonObject.get("id").getAsString().contains("skull")) {
            String value = jsonObject.get("id").getAsString();
            value = value.replace("minecraft:", "")
                .replace("skull", "player_head");
            jsonObject.addProperty("id", value);
        }

        // Handle player head for both legacy and component formats
        boolean isPlayerHead = jsonObject.get("id").getAsString().equalsIgnoreCase("player_head");
        String parsedItemId = jsonObject.get("id").getAsString();

        String base64Texture = null;
        if (isPlayerHead) {
            base64Texture = getSkinValue(jsonObject);

            if (base64Texture != null) {
                generators.add(new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(base64Texture));
            } else {
                generators.add(new MinecraftItemGenerator.Builder()
                    .withItem(parsedItemId)
                    .isBigImage());
            }
        } else {
            generators.add(new MinecraftItemGenerator.Builder()
                .withItem(parsedItemId)
                //.isEnchanted(enchanted) TODO: determine if the item is enchanted
                .isBigImage());
        }

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .parseNbtJson(jsonObject)
            .withRenderBorder(true)
            .isPaddingFirstLine(true)
            .withMaxLineLength(getMaxLineLength(jsonObject));

        // Extract dye color and apply to item generator if it exists
        String dyeColor = tooltipGenerator.getDyeColor(jsonObject);
        if (dyeColor != null && !isPlayerHead) {
            // Update the item generator with dye color
            generators = new ArrayList<>();
            generators.add(new MinecraftItemGenerator.Builder()
                .withItem(jsonObject.get("id").getAsString())
                .withData(dyeColor)
                .isBigImage());
        }

        generators.add(tooltipGenerator);

        PlaceholderReverseMapper reverseMapper = new PlaceholderReverseMapper();
        String mappedLore = reverseMapper.mapPlaceholders(tooltipGenerator.getItemLore());
        String mappedName = reverseMapper.mapPlaceholders(tooltipGenerator.getItemName());

        tooltipGenerator
            .withItemLore(mappedLore)
            .withName(mappedName);

        return new ParsedNbt(generators, base64Texture, parsedItemId);
    }

    private static String parseTextComponentForLength(JsonObject textComponent) {
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

                // Only add the text content for length calculation
                if (extraComponent.has("text")) {
                    result.append(extraComponent.get("text").getAsString());
                }
            }
        }

        return result.toString();
    }

    private static int getMaxLineLength(JsonObject jsonObject) {
        Integer maxLineLength = null;

        if (jsonObject.has("components")) {
            maxLineLength = getMaxLineLengthComponentsFormat(jsonObject);
        } else if (jsonObject.has("tag")) {
            maxLineLength = getMaxLineLengthLegacyFormat(jsonObject);
        }

        return maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;
    }

    private static Integer getMaxLineLengthLegacyFormat(JsonObject jsonObject) {
        JsonObject tag = jsonObject.getAsJsonObject("tag");
        if (tag.has("display")) {
            JsonObject display = tag.getAsJsonObject("display");
            if (display.has("Lore")) {
                var stats = display.get("Lore")
                    .getAsJsonArray()
                    .asList()
                    .stream()
                    .map(JsonElement::getAsString)
                    .mapToInt(String::length)
                    .summaryStatistics();

                if (stats.getCount() > 0) {
                    return stats.getMax();
                }
            }
        }

        return null;
    }

    private static Integer getMaxLineLengthComponentsFormat(JsonObject jsonObject) {
        JsonObject components = jsonObject.getAsJsonObject("components");
        if (components.has("minecraft:lore")) {
            JsonArray loreArray = components.getAsJsonArray("minecraft:lore");
            List<String> loreLines = new ArrayList<>();

            for (JsonElement loreElement : loreArray) {
                JsonObject loreEntry = loreElement.getAsJsonObject();
                String parsedLine = parseTextComponentForLength(loreEntry);
                loreLines.add(parsedLine);
            }

            if (!loreLines.isEmpty()) {
                return loreLines.stream().max(Comparator.comparingInt(String::length)).get().length();
            }
        }

        return null;
    }

    private static String getSkinValue(JsonObject jsonObject) {
        if (jsonObject.has("tag")) {
            return getSKinValueLegacyFormat(jsonObject);
        } else if (jsonObject.has("components")) {
            return getSKinValueComponentsFormat(jsonObject);
        }

        return null;
    }

    private static String getSKinValueLegacyFormat(JsonObject jsonObject) {
        JsonObject tagObject = jsonObject.getAsJsonObject("tag");
        if (tagObject != null && tagObject.get("SkullOwner") != null) {
            JsonArray textures = tagObject.get("SkullOwner").getAsJsonObject()
                .get("Properties").getAsJsonObject()
                .get("textures").getAsJsonArray();

            if (textures.size() > 1) {
                throw new TooManyTexturesException();
            }

            return textures.get(0).getAsJsonObject().get("Value").getAsString();
        }
        return null;
    }

    private static String getSKinValueComponentsFormat(JsonObject jsonObject) {
        JsonObject components = jsonObject.getAsJsonObject("components");
        if (components.has("minecraft:profile")) {
            JsonObject profile = components.getAsJsonObject("minecraft:profile");
            if (profile.has("properties")) {
                JsonArray properties = profile.getAsJsonArray("properties");
                for (JsonElement propertyElement : properties) {
                    JsonObject property = propertyElement.getAsJsonObject();
                    if (property.has("name") && "textures".equalsIgnoreCase(property.get("name").getAsString()) && property.has("value")) {
                        return property.get("value").getAsString();
                    }
                }
            }
        }

        return null;
    }

    @Getter(AccessLevel.PUBLIC)
    @AllArgsConstructor
    public static class ParsedNbt {

        private ArrayList<ClassBuilder<? extends Generator>> generators;
        private String base64Texture;
        private String parsedItemId;
    }
}
