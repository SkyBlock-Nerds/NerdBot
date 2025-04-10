package net.hypixel.nerdbot.internalapi.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kotlin.Pair;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.image.GeneratorImageBuilder;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftItemGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftPlayerHeadGenerator;
import net.hypixel.nerdbot.generator.impl.tooltip.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.exception.NbtParseException;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Log4j2
public class GeneratorApi {

    public static GeneratedObject generateItem(
        String itemId,
        @Nullable String data,
        @Nullable Boolean enchanted,
        @Nullable Boolean hoverEffect,
        @Nullable String skinValue
    ) {
        enchanted = enchanted != null && enchanted;
        hoverEffect = hoverEffect != null && hoverEffect;

        GeneratorImageBuilder item = new GeneratorImageBuilder();

        if (itemId.equalsIgnoreCase("player_head") && skinValue != null) {
            item.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                .withSkin(skinValue)
                .build());
        } else {
            item.addGenerator(new MinecraftItemGenerator.Builder()
                .withItem(itemId)
                .withData(data)
                .isEnchanted(enchanted)
                .withHoverEffect(hoverEffect)
                .isBigImage()
                .build()
            );
        }

        return item.build();
    }

    public static List<String> searchItem(String itemId) {
        return Spritesheet.searchForTexture(itemId)
            .stream()
            .map(Map.Entry::getKey)
            .toList();
    }

    public static GeneratedObject generateHead(String texture) {
        return new GeneratorImageBuilder()
            .addGenerator(new MinecraftPlayerHeadGenerator.Builder().withSkin(texture).build())
            .build();
    }

    public static GeneratedObject generateRecipe(
        String recipe,
        @Nullable Boolean renderBackground
    ) {
        renderBackground = renderBackground == null || renderBackground;

        return new GeneratorImageBuilder()
            .addGenerator(new MinecraftInventoryGenerator.Builder()
                .withRows(3)
                .withSlotsPerRow(3)
                .drawBorder(false)
                .drawBackground(renderBackground)
                .withInventoryString(recipe)
                .build())
            .build();
    }

    public static GeneratedObject generateInventory(
        String inventoryString,
        int rows,
        int slotsPerRow,
        @Nullable String hoveredItemString,
        @Nullable String containerName,
        @Nullable Boolean drawBorder
    ) {
        drawBorder = drawBorder == null || drawBorder;

        GeneratorImageBuilder generatedObject = new GeneratorImageBuilder()
            .addGenerator(new MinecraftInventoryGenerator.Builder()
                .withRows(rows)
                .withSlotsPerRow(slotsPerRow)
                .drawBorder(drawBorder)
                .drawBackground(true)
                .withContainerTitle(containerName)
                .withInventoryString(inventoryString)
                .build());

        if (hoveredItemString != null) {
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(hoveredItemString)
                .withAlpha(MinecraftTooltip.DEFAULT_ALPHA)
                .withPadding(MinecraftTooltip.DEFAULT_PADDING)
                .isPaddingFirstLine(false)
                .disableRarityLineBreak(false)
                .withRenderBorder(true)
                .build();

            generatedObject.addGenerator(tooltipGenerator);
        }

        return generatedObject.build();
    }

    // TODO find a better way to return the gen command because this is a bit messy (There shouldn't be a discord command in the general API)(aslo double return pair is wacky)
    public static Pair<GeneratedObject, String> parseNbtString(
        String nbt,
        @Nullable Integer alpha,
        @Nullable Integer padding
    ) {
        alpha = alpha == null ? MinecraftTooltip.DEFAULT_ALPHA : alpha;
        padding = padding == null ? MinecraftTooltip.DEFAULT_PADDING : padding;

        JsonObject jsonObject = JsonParser.parseString(nbt).getAsJsonObject();
        JsonObject tagObject = jsonObject.get("tag").getAsJsonObject();
        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();

        if (jsonObject.get("id").getAsString().contains("skull")) {
            String value = jsonObject.get("id").getAsString();
            value = value.replace("minecraft:", "")
                .replace("skull", "player_head");
            jsonObject.addProperty("id", value);
        }

        if (jsonObject.get("id").getAsString().equalsIgnoreCase("player_head")
            && tagObject.get("SkullOwner") != null) {
            JsonArray textures = tagObject.get("SkullOwner").getAsJsonObject()
                .get("Properties").getAsJsonObject()
                .get("textures").getAsJsonArray();

            if (textures.size() > 1) {
                throw new NbtParseException("Multiple textures found for player head! Please provide a single texture.");
            }

            String base64 = textures.get(0).getAsJsonObject().get("Value").getAsString();

            generatorImageBuilder.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                .withSkin(base64)
                .build()
            );
        } else {
            generatorImageBuilder.addGenerator(new MinecraftItemGenerator.Builder()
                .withItem(jsonObject.get("id").getAsString())
                //.isEnchanted(enchanted) TODO: determine if the item is enchanted
                .isBigImage()
                .build());
        }

        int maxLineLength = Util.getLongestLine(jsonObject.get("tag").getAsJsonObject()
            .get("display").getAsJsonObject()
            .get("Lore").getAsJsonArray()
            .asList()
            .stream()
            .map(JsonElement::getAsString)
            .toList()).getRight();

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .parseNbtJson(jsonObject)
            .withAlpha(alpha)
            .withPadding(padding)
            .withRenderBorder(true)
            .isPaddingFirstLine(true)
            .withMaxLineLength(maxLineLength);

        GeneratedObject generatedObject = generatorImageBuilder.addGenerator(tooltipGenerator.build()).build();
        String parsedCommand =
            "Your NBT input has been parsed into a slash command:" + System.lineSeparator() +
                "```" + System.lineSeparator() + tooltipGenerator.buildSlashCommand() + "```";

        return new Pair<>(generatedObject, parsedCommand);
    }

    public static GeneratedObject generateTooltip(
        String itemName,
        String itemLore,
        @Nullable String type,
        @Nullable String rarity,
        @Nullable String itemId,
        @Nullable String skinValue,
        @Nullable String recipe,
        @Nullable Integer alpha,
        @Nullable Integer padding,
        @Nullable Boolean disableRarityLineBreak,
        @Nullable Boolean enchanted,
        @Nullable Boolean centered,
        @Nullable Boolean paddingFirstLine,
        @Nullable Integer maxLineLength,
        @Nullable String tooltipSide,
        @Nullable Boolean renderBorder
    ) {
        type = type == null ? "" : type;
        rarity = rarity == null ? "none" : rarity;
        alpha = alpha == null ? MinecraftTooltip.DEFAULT_ALPHA : alpha;
        padding = padding == null ? MinecraftTooltip.DEFAULT_PADDING : padding;
        disableRarityLineBreak = disableRarityLineBreak == null || disableRarityLineBreak;
        centered = centered != null && centered;
        enchanted = enchanted != null && enchanted;
        paddingFirstLine = paddingFirstLine == null || paddingFirstLine;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;
        renderBorder = renderBorder == null || renderBorder;

        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
        MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .withName(itemName)
            .withRarity(Rarity.byName(rarity))
            .withItemLore(itemLore)
            .withType(type)
            .withAlpha(alpha)
            .withPadding(padding)
            .disableRarityLineBreak(disableRarityLineBreak)
            .withMaxLineLength(maxLineLength)
            .isTextCentered(centered)
            .isPaddingFirstLine(paddingFirstLine)
            .withRenderBorder(renderBorder)
            .build();

        if (itemId != null) {
            if (itemId.equalsIgnoreCase("player_head")) {
                MinecraftPlayerHeadGenerator.Builder generator = new MinecraftPlayerHeadGenerator.Builder()
                    .withScale(-2);

                if (skinValue != null) {
                    generator.withSkin(skinValue);
                }

                generatorImageBuilder.addGenerator(generator.build());
            } else {
                generatorImageBuilder.addGenerator(new MinecraftItemGenerator.Builder()
                    .withItem(itemId)
                    .isEnchanted(enchanted)
                    .isBigImage()
                    .build());
            }
        }

        if (recipe != null) {
            generatorImageBuilder.addGenerator(0, new MinecraftInventoryGenerator.Builder()
                .withRows(3)
                .withSlotsPerRow(3)
                .drawBorder(renderBorder)
                .withInventoryString(recipe)
                .build()
            ).build();
        }

        if (tooltipSide != null && MinecraftTooltipGenerator.TooltipSide.valueOf(tooltipSide.toUpperCase()) == MinecraftTooltipGenerator.TooltipSide.LEFT) {
            generatorImageBuilder.addGenerator(0, tooltipGenerator);
        } else {
            generatorImageBuilder.addGenerator(tooltipGenerator);
        }

        return generatorImageBuilder.build();
    }

    public static GeneratedObject generateText(
        String text,
        @Nullable Boolean centered,
        @Nullable Integer alpha,
        @Nullable Integer padding,
        @Nullable Integer maxLineLength,
        @Nullable Boolean renderBorder
    ) {
        centered = centered != null && centered;
        alpha = alpha == null ? MinecraftTooltip.DEFAULT_ALPHA : alpha;
        padding = padding == null ? MinecraftTooltip.DEFAULT_PADDING : padding;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;
        renderBorder = renderBorder != null && renderBorder;

        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
        MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .withItemLore(text)
            .withAlpha(alpha)
            .withPadding(padding)
            .withMaxLineLength(maxLineLength)
            .isTextCentered(centered)
            .isPaddingFirstLine(false)
            .disableRarityLineBreak(false)
            .withRenderBorder(renderBorder)
            .build();

        generatorImageBuilder.addGenerator(tooltipGenerator);
        return generatorImageBuilder.build();
    }

    public static List<String> itemNamesAutoCompletes() {
        return Spritesheet.getImageMap().keySet()
            .stream()
            .toList();
    }

    public static List<String> itemRaritiesAutoCompletes() {
        return Rarity.getRarityNames();
    }

    public static List<String> tooltipSideAutoCompletes() {
        return Arrays.stream(MinecraftTooltipGenerator.TooltipSide.values())
            .map(MinecraftTooltipGenerator.TooltipSide::name)
            .toList();
    }
}