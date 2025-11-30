package net.hypixel.nerdbot.app.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.model.user.generator.GeneratorHistory;
import net.hypixel.nerdbot.generator.data.PowerStrength;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.data.Stat;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.image.GeneratorImageBuilder;
import net.hypixel.nerdbot.generator.image.MinecraftTooltip;
import net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftItemGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftPlayerHeadGenerator;
import net.hypixel.nerdbot.generator.impl.tooltip.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.core.FileUtils;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.generator.parser.text.PlaceholderReverseMapper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public class GeneratorCommands {

    public static final String BASE_COMMAND = "gen2"; // TODO change this back to "gen" when released

    private static final String ITEM_DESCRIPTION = "The ID of the item to display";
    private static final String EXTRA_DATA_DESCRIPTION = "The extra modifiers to change the item";
    private static final String ENCHANTED_DESCRIPTION = "Whether or not the item should be enchanted";
    private static final String NAME_DESCRIPTION = "The name of the item";
    private static final String RARITY_DESCRIPTION = "The rarity of the item";
    private static final String TYPE_DESCRIPTION = "The type of the item";
    private static final String LORE_DESCRIPTION = "The lore of the item";
    private static final String SKIN_VALUE_DESCRIPTION = "The skin value of the player head";
    private static final String ALPHA_DESCRIPTION = "The alpha of the tooltip";
    private static final String PADDING_DESCRIPTION = "The padding of the tooltip";
    private static final String RARITY_LINE_BREAK_DESCRIPTION = "Whether or not the tooltip should have an empty line between the lore and the rarity/type";
    private static final String CENTERED_DESCRIPTION = "Whether or not the tooltip should be centered";
    private static final String MAX_LINE_LENGTH_DESCRIPTION = "The max line length of the tooltip";
    private static final String LINE_PADDING_DESCRIPTION = "Add a small amount of padding between the item name and the first line of lore";
    private static final String TOOLTIP_SIDE_DESCRIPTION = "Which side the tooltip should be displayed on";
    private static final String TEXT_DESCRIPTION = "The text to display";
    private static final String TEXTURE_DESCRIPTION = "The texture of the player head";
    private static final String RECIPE_STRING_DESCRIPTION = "The recipe string to display";
    private static final String INVENTORY_ROWS_DESCRIPTION = "The number of rows in the inventory";
    private static final String INVENTORY_COLUMNS_DESCRIPTION = "The number of slots per row in the inventory";
    private static final String INVENTORY_CONTENTS_DESCRIPTION = "The inventory contents to display";
    private static final String INVENTORY_NAME_DESCRIPTION = "The name of the inventory";
    private static final String RENDER_BACKGROUND_DESCRIPTION = "Whether or not the background should be rendered";
    private static final String RENDER_BORDER_DESCRIPTION = "Whether the inventory's border should be rendered";
    private static final String NBT_DESCRIPTION = "The NBT string to parse";
    private static final String HIDDEN_OUTPUT_DESCRIPTION = "Whether the output should be hidden (sent ephemerally)";
    private static final String DURABILITY_DESCRIPTION = "Item durability percentage (0-100, only shown if less than 100)";

    private static final boolean AUTO_HIDE_ON_ERROR = true;

    @SlashCommand(name = BASE_COMMAND, subcommand = "display", description = "Display an item")
    public void generateItem(
        SlashCommandInteractionEvent event,
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION) String itemId,
        @SlashOption(description = EXTRA_DATA_DESCRIPTION, required = false) String data,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = "If the item should look as if it being hovered over", required = false) Boolean hoverEffect,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = DURABILITY_DESCRIPTION, required = false) Integer durability,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        enchanted = enchanted != null && enchanted;
        hoverEffect = hoverEffect != null && hoverEffect;
        durability = durability == null ? 100 : durability;

        try {
            GeneratorImageBuilder item = new GeneratorImageBuilder();

            if (itemId.equalsIgnoreCase("player_head") && skinValue != null) {
                item.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(skinValue)
                    .build());
            } else {
                MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
                    .withItem(itemId)
                    .withData(data)
                    .isEnchanted(enchanted)
                    .withHoverEffect(hoverEffect)
                    .isBigImage();

                if (durability != null) {
                    itemBuilder.withDurability(durability);
                }

                item.addGenerator(itemBuilder.build());
            }

            GeneratedObject generatedObject = item.build();

            if (generatedObject.isAnimated()) {
                event.getHook().editOriginalAttachments(FileUpload.fromData(generatedObject.getGifData(), "item.gif")).queue();
            } else {
                event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "item.png")).queue();
            }

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an item display", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            log.error("Encountered an error while generating an item display", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "powerstone", description = "Generate an image of a Power Stone")
    public void generatePowerstone(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "The name of your Power Stone") String powerName,
        @SlashOption(autocompleteId = "power-strengths", description = "The strength of the Power Stone") String powerStrength,
        @SlashOption(description = "The Magical Power to use in the stat calculations") int magicalPower,
        @SlashOption(description = "The stats that scale with the given Magical Power", required = false) String scalingStats, // Desired Format: stat1:1,stat2:23,stat3:456
        @SlashOption(description = "The stats that do not scale with the given Magical Power", required = false) String uniqueBonus, // Desired Format: stat1:1,stat2:23,stat3:456
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION, required = false) String itemId,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = ALPHA_DESCRIPTION, required = false) Integer alpha,
        @SlashOption(description = PADDING_DESCRIPTION, required = false) Integer padding,
        @SlashOption(description = "Includes a slash command for you to edit", required = false) Boolean includeGenFullCommand,
        @SlashOption(description = "Whether the Power Stone shows as selected", required = false) Boolean selected,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        alpha = alpha == null ? MinecraftTooltip.DEFAULT_ALPHA : alpha;
        padding = padding == null ? MinecraftTooltip.DEFAULT_PADDING : padding;
        enchanted = enchanted != null && enchanted;

        Function<String, Map<String, Integer>> parseStatsToMap = stats -> {
            Map<String, Integer> map = new HashMap<>();

            if (stats == null || stats.trim().isEmpty()) {
                return map;
            }

            String[] entries = stats.split(",");

            for (String entry : entries) {
                if (entry == null || entry.trim().isEmpty()) {
                    continue;
                }

                String[] stat = entry.split(":");

                if (stat.length != 2 || stat[0].trim().isEmpty() || stat[1].trim().isEmpty()) {
                    throw new GeneratorException("Stat `" + entry + "` is using an invalid format");
                }

                String statName = stat[0].trim();

                int statValue;

                try {
                    statValue = Integer.parseInt(stat[1].trim());
                } catch (NumberFormatException e) {
                    throw new GeneratorException("Invalid number for stat `" + statName + "`: " + stat[1].trim());
                }

                map.merge(statName, statValue, Integer::sum);
            }

            return map;
        };

        try {
            StringBuilder scalingStatsFormatted = new StringBuilder();
            Map<String, Integer> scalingStatsMap = parseStatsToMap.apply(scalingStats);

            for (Map.Entry<String, Integer> entry : scalingStatsMap.entrySet()) {
                String statName = entry.getKey();
                Integer basePower = entry.getValue();
                Stat stat = Stat.byName(statName);

                if (stat == null) {
                    throw new GeneratorException("`" + statName + "` is not a valid stat");
                }

                scalingStatsFormatted.append(String.format("%%%%%s:%s%%%%\\n", statName, StringUtils.COMMA_SEPARATED_FORMAT.format(calculatePowerStoneStat(stat, basePower, magicalPower))));
            }

            if (!scalingStatsFormatted.isEmpty()) {
                scalingStatsFormatted = new StringBuilder("&7Stats:\\n")
                    .append(scalingStatsFormatted)
                    .append("\\n");
            }

            StringBuilder bonusStatsFormatted = new StringBuilder();
            Map<String, Integer> bonusStats = parseStatsToMap.apply(uniqueBonus);

            for (Map.Entry<String, Integer> entry : bonusStats.entrySet()) {
                String statName = entry.getKey();
                Integer statAmount = entry.getValue();
                Stat stat = Stat.byName(statName);

                if (stat == null) {
                    throw new GeneratorException("'" + statName + "' is not a valid stat");
                }

                bonusStatsFormatted.append(String.format("%%%%%s:%s%%%%\\n", statName, StringUtils.COMMA_SEPARATED_FORMAT.format(statAmount)));
            }

            if (!bonusStatsFormatted.isEmpty()) {
                bonusStatsFormatted = new StringBuilder("&7Unique Power Bonus:\\n")
                    .append(bonusStatsFormatted)
                    .append("\\n");
            }

            String itemLoreTemplate =
                "&8%s\\n" + // %s = PowerStrength.byName(powerStrength) OR powerStrength
                    "\\n" +
                    "%s" + // %s = scalingStatsFormatted
                    "%s" + // %s = bonusStatsFormatted
                    "&7You have: &6%s Magical Power\\n" + // %d = magicalPower
                    "\\n" +
                    (selected == null || selected ? "&aPower is selected!" : "&eClick to select power!");

            String itemLore = String.format(itemLoreTemplate,
                PowerStrength.byName(powerStrength) == null ? powerStrength : PowerStrength.byName(powerStrength).getFormattedDisplay(),
                scalingStatsFormatted,
                bonusStatsFormatted,
                StringUtils.COMMA_SEPARATED_FORMAT.format(magicalPower)
            );

            try {
                GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
                MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                    .withName("&a" + powerName)
                    .withRarity(Rarity.byName("none"))
                    .withItemLore(itemLore)
                    .withAlpha(alpha)
                    .withPadding(padding)
                    .disableRarityLineBreak(true)
                    .isTextCentered(false)
                    .isPaddingFirstLine(true)
                    .withRenderBorder(true);

                if (includeGenFullCommand != null && includeGenFullCommand) {
                    event.getHook().sendMessage("Your Power Stone has been parsed into a slash command:\n```" + tooltipGenerator.buildSlashCommand() + "```").queue();
                }

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

                generatorImageBuilder.addGenerator(tooltipGenerator.build());
                GeneratedObject generatedObject = generatorImageBuilder.build();

                if (generatedObject.isAnimated()) {
                    event.getHook().editOriginalAttachments(FileUpload.fromData(generatedObject.getGifData(), "powerstone.gif")).queue();
                } else {
                    event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "powerstone.png")).queue();
                }

                addCommandToUserHistory(event.getUser(), event.getCommandString());
            } catch (GeneratorException | IllegalArgumentException exception) {
                event.getHook().editOriginal(exception.getMessage()).queue();
                log.error("Encountered an error while generating a Power Stone", exception);
            } catch (IOException exception) {
                event.getHook().editOriginal("An error occurred while generating that Power Stone!").queue();
                log.error("Encountered an error while generating a Power Stone", exception);
            }
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating a Power Stone", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "search", description = "Search for an item")
    public void searchItem(SlashCommandInteractionEvent event, @SlashOption(description = "The ID of the item to search for") String itemId, @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        List<Map.Entry<String, BufferedImage>> results = Spritesheet.searchForTexture(itemId);

        if (results.isEmpty()) {
            event.getHook().editOriginal("No results found for that item!").queue();
            return;
        }

        List<Map.Entry<String, BufferedImage>> topResults = results.subList(0, Math.min(10, results.size()));
        StringBuilder message = new StringBuilder("Top results for `" + itemId + "` (" + StringUtils.COMMA_SEPARATED_FORMAT.format(results.size()) + " total):\n");

        for (Map.Entry<String, BufferedImage> entry : topResults) {
            message.append(" - `").append(entry.getKey()).append("`\n");
        }

        event.getHook().editOriginal(message.toString()).queue();
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "head", description = "Generate a player head")
    public void generateHead(
        SlashCommandInteractionEvent event,
        @SlashOption(description = TEXTURE_DESCRIPTION) String texture,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        try {
            GeneratedObject generatedObject = new GeneratorImageBuilder()
                .addGenerator(new MinecraftPlayerHeadGenerator.Builder().withSkin(texture).build())
                .build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "head.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating a player head", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that player head!").queue();
            log.error("Encountered an error while generating a player head", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "recipe", description = "Generate a recipe")
    public void generateRecipe(
        SlashCommandInteractionEvent event,
        @SlashOption(description = RECIPE_STRING_DESCRIPTION) String recipe,
        @SlashOption(description = RENDER_BACKGROUND_DESCRIPTION, required = false) Boolean renderBackground,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        renderBackground = renderBackground == null || renderBackground;

        try {
            GeneratedObject generatedObject = new GeneratorImageBuilder()
                .addGenerator(new MinecraftInventoryGenerator.Builder()
                    .withRows(3)
                    .withSlotsPerRow(3)
                    .drawBorder(false)
                    .drawBackground(renderBackground)
                    .withInventoryString(recipe)
                    .build())
                .build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "recipe.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating a recipe", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that recipe!").queue();
            log.error("Encountered an error while generating a recipe", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "inventory", description = "Generate an inventory")
    public void generateInventory(
        SlashCommandInteractionEvent event,
        @SlashOption(description = INVENTORY_ROWS_DESCRIPTION) int rows,
        @SlashOption(description = INVENTORY_COLUMNS_DESCRIPTION) int slotsPerRow,
        @SlashOption(description = INVENTORY_CONTENTS_DESCRIPTION) String inventoryString,
        @SlashOption(description = "Optional item lore displayed beside the inventory", required = false) String hoveredItemString,
        @SlashOption(description = INVENTORY_NAME_DESCRIPTION, required = false) String containerName,
        @SlashOption(description = RENDER_BORDER_DESCRIPTION, required = false) Boolean drawBorder,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        drawBorder = drawBorder == null || drawBorder;
        boolean animateGlint = DiscordBotEnvironment.getBot().getConfig().getGeneratorConfig().getInventory().isAnimateGlint();
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;

        try {
            GeneratorImageBuilder generatedObject = new GeneratorImageBuilder()
                .addGenerator(new MinecraftInventoryGenerator.Builder()
                    .withRows(rows)
                    .withSlotsPerRow(slotsPerRow)
                    .drawBorder(drawBorder)
                    .drawBackground(true)
                    .withAnimateGlint(animateGlint)
                    .withContainerTitle(containerName)
                    .withInventoryString(inventoryString)
                    .build());

            if (hoveredItemString != null && !hoveredItemString.isBlank()) {
                MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                    .withItemLore(hoveredItemString)
                    .withAlpha(MinecraftTooltip.DEFAULT_ALPHA)
                    .withPadding(MinecraftTooltip.DEFAULT_PADDING)
                    .isPaddingFirstLine(false)
                    .disableRarityLineBreak(false)
                    .withMaxLineLength(maxLineLength)
                    .withScaleFactor(Math.min(2, MinecraftInventoryGenerator.getScaleFactor()))
                    .withRenderBorder(true)
                    .build();

                generatedObject.addGenerator(tooltipGenerator);
            }

            GeneratedObject finalObject = generatedObject.build();

            if (finalObject.isAnimated()) {
                event.getHook().editOriginalAttachments(FileUpload.fromData(finalObject.getGifData(), "inventory.gif")).queue();
            } else {
                event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(finalObject.getImage()), "inventory.png")).queue();
            }

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an inventory", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that inventory!").queue();
            log.error("Encountered an error while generating an inventory", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "parse", description = "Parse an NBT string")
    public void parseNbtString(
        SlashCommandInteractionEvent event,
        @SlashOption(description = NBT_DESCRIPTION) String nbt,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        try {
            JsonObject jsonObject = JsonParser.parseString(nbt).getAsJsonObject();
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
            String skinValueForCommand = null;

            if (jsonObject.get("id").getAsString().contains("skull")) {
                String value = jsonObject.get("id").getAsString();
                value = value.replace("minecraft:", "")
                    .replace("skull", "player_head");
                jsonObject.addProperty("id", value);
            }

            // Handle player head for both legacy and component formats
            boolean isPlayerHead = jsonObject.get("id").getAsString().equalsIgnoreCase("player_head");
            JsonObject tagObject = jsonObject.has("tag") ? jsonObject.get("tag").getAsJsonObject() : null;
            String parsedItemId = jsonObject.get("id").getAsString();

            if (isPlayerHead) {
                String base64Texture = null;

                if (tagObject != null && tagObject.get("SkullOwner") != null) {
                    JsonArray textures = tagObject.get("SkullOwner").getAsJsonObject()
                        .get("Properties").getAsJsonObject()
                        .get("textures").getAsJsonArray();

                    if (textures.size() > 1) {
                        event.getHook().editOriginal("There seems to be more than 1 texture in the player head's NBT data. Please double-check it is correct!").queue();
                        return;
                    }

                    base64Texture = textures.get(0).getAsJsonObject().get("Value").getAsString();
                }

                if (base64Texture == null && jsonObject.has("components")) {
                    JsonObject components = jsonObject.getAsJsonObject("components");
                    if (components.has("minecraft:profile")) {
                        JsonObject profile = components.getAsJsonObject("minecraft:profile");
                        if (profile.has("properties")) {
                            JsonArray properties = profile.getAsJsonArray("properties");
                            for (JsonElement propertyElement : properties) {
                                JsonObject property = propertyElement.getAsJsonObject();
                                if (property.has("name") && "textures".equalsIgnoreCase(property.get("name").getAsString()) && property.has("value")) {
                                    base64Texture = property.get("value").getAsString();
                                    break;
                                }
                            }
                        }
                    }
                }

                if (base64Texture != null) {
                    skinValueForCommand = base64Texture;

                    generatorImageBuilder.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                        .withSkin(base64Texture)
                        .build()
                    );
                } else {
                    generatorImageBuilder.addGenerator(new MinecraftItemGenerator.Builder()
                        .withItem(parsedItemId)
                        .isBigImage()
                        .build());
                }
            } else {
                generatorImageBuilder.addGenerator(new MinecraftItemGenerator.Builder()
                    .withItem(parsedItemId)
                    //.isEnchanted(enchanted) TODO: determine if the item is enchanted
                    .isBigImage()
                    .build());
            }

            int maxLineLength;

            // Calculate max line length based on format
            if (jsonObject.has("components")) {
                JsonObject components = jsonObject.getAsJsonObject("components");
                if (components.has("minecraft:lore")) {
                    JsonArray loreArray = components.getAsJsonArray("minecraft:lore");
                    List<String> loreLines = new ArrayList<>();

                    for (JsonElement loreElement : loreArray) {
                        JsonObject loreEntry = loreElement.getAsJsonObject();
                        String parsedLine = parseTextComponentForLength(loreEntry);
                        loreLines.add(parsedLine);
                    }

                    maxLineLength = StringUtils.getLongestLine(loreLines).getRight();
                } else {
                    maxLineLength = MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
                }
            } else if (jsonObject.has("tag")) {
                // Legacy format
                JsonObject tag = jsonObject.getAsJsonObject("tag");
                if (tag.has("display")) {
                    JsonObject display = tag.getAsJsonObject("display");
                    if (display.has("Lore")) {
                        maxLineLength = StringUtils.getLongestLine(display.get("Lore").getAsJsonArray()
                            .asList()
                            .stream()
                            .map(JsonElement::getAsString)
                            .toList()).getRight();
                    } else {
                        maxLineLength = MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
                    }
                } else {
                    maxLineLength = MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
                }
            } else {
                maxLineLength = MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
            }

            MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .parseNbtJson(jsonObject)
                .withRenderBorder(true)
                .isPaddingFirstLine(true)
                .withMaxLineLength(maxLineLength);

            // Extract dye color and apply to item generator if it exists
            String dyeColor = tooltipGenerator.getDyeColor(jsonObject);
            if (dyeColor != null && !isPlayerHead) {
                // Update the item generator with dye color
                generatorImageBuilder = new GeneratorImageBuilder();
                generatorImageBuilder.addGenerator(new MinecraftItemGenerator.Builder()
                    .withItem(jsonObject.get("id").getAsString())
                    .withData(dyeColor)
                    .isBigImage()
                    .build());
            }

            GeneratedObject generatedObject = generatorImageBuilder.addGenerator(tooltipGenerator.build()).build();

            PlaceholderReverseMapper reverseMapper = new PlaceholderReverseMapper();
            String mappedLore = reverseMapper.mapPlaceholders(tooltipGenerator.getItemLore());
            String mappedName = reverseMapper.mapPlaceholders(tooltipGenerator.getItemName());

            tooltipGenerator
                .withItemLore(mappedLore)
                .withName(mappedName);

            String slashCommand = tooltipGenerator.buildSlashCommand();
            String commandItemId = parsedItemId;

            if (commandItemId != null && !commandItemId.isEmpty()) {
                if (commandItemId.startsWith("minecraft:")) {
                    commandItemId = commandItemId.substring("minecraft:".length());
                }
                slashCommand += " item_id: " + commandItemId;
            }

            if (skinValueForCommand != null && !skinValueForCommand.isEmpty()) {
                slashCommand += " skin_value: " + skinValueForCommand;
            }

            // Escape newlines in lore so the slash command is a single line
            slashCommand = slashCommand.replace("\n", "\\n");

            MessageEditBuilder builder = new MessageEditBuilder()
                .setContent("Your NBT input has been parsed into a slash command:" + System.lineSeparator() + "```" + System.lineSeparator() + slashCommand + "```");

            if (generatedObject.isAnimated()) {
                builder.setFiles(FileUpload.fromData(generatedObject.getGifData(), "parsed_nbt.gif"));
            } else {
                builder.setFiles(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "parsed_nbt.png"));
            }

            event.getHook().editOriginal(builder.build()).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (JsonParseException exception) {
            event.getHook().editOriginal("You provided badly formatted NBT!").queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while parsing NBT", exception);
        } catch (IOException e) {
            event.getHook().editOriginal("An error occurred while parsing the NBT!").queue();
            log.error("Encountered an error while parsing NBT", e);
        }
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

    @SlashCommand(name = BASE_COMMAND, subcommand = "full", description = "Generate a full item image. Supports displaying items, recipes, and tooltips")
    public void generateTooltip(
        SlashCommandInteractionEvent event,
        @SlashOption(description = NAME_DESCRIPTION) String itemName,
        @SlashOption(description = LORE_DESCRIPTION) String itemLore,
        @SlashOption(description = TYPE_DESCRIPTION, required = false) String type,
        @SlashOption(autocompleteId = "item-rarities", description = RARITY_DESCRIPTION, required = false) String rarity,
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION, required = false) String itemId,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = RECIPE_STRING_DESCRIPTION, required = false) String recipe,
        @SlashOption(description = ALPHA_DESCRIPTION, required = false) Integer alpha,
        @SlashOption(description = PADDING_DESCRIPTION, required = false) Integer padding,
        @SlashOption(description = RARITY_LINE_BREAK_DESCRIPTION, required = false) Boolean disableRarityLineBreak,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = CENTERED_DESCRIPTION, required = false) Boolean centered,
        @SlashOption(description = LINE_PADDING_DESCRIPTION, required = false) Boolean paddingFirstLine,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(autocompleteId = "tooltip-side", description = TOOLTIP_SIDE_DESCRIPTION, required = false) String tooltipSide,
        @SlashOption(description = RENDER_BORDER_DESCRIPTION, required = false) Boolean renderBorder,
        @SlashOption(description = DURABILITY_DESCRIPTION, required = false) Integer durability,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

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
        durability = durability == null ? 100 : durability;

        try {
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
                    MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
                        .withItem(itemId)
                        .isEnchanted(enchanted)
                        .isBigImage();

                    if (durability != null) {
                        itemBuilder.withDurability(durability);
                    }

                    generatorImageBuilder.addGenerator(itemBuilder.build());
                }
            }

            if (recipe != null && !recipe.isBlank()) {
                generatorImageBuilder.addGenerator(0, new MinecraftInventoryGenerator.Builder()
                    .withRows(3)
                    .withSlotsPerRow(3)
                    .drawBorder(renderBorder)
                    .withInventoryString(recipe)
                    .build()
                ).build();
            }

            try {
                if (tooltipSide != null && MinecraftTooltipGenerator.TooltipSide.valueOf(tooltipSide.toUpperCase()) == MinecraftTooltipGenerator.TooltipSide.LEFT) {
                    generatorImageBuilder.addGenerator(0, tooltipGenerator);
                } else {
                    generatorImageBuilder.addGenerator(tooltipGenerator);
                }
            } catch (IllegalArgumentException ignored) {
                // Fallback to default side if an invalid value was provided
                generatorImageBuilder.addGenerator(tooltipGenerator);
            }

            GeneratedObject generatedObject = generatorImageBuilder.build();

            if (generatedObject.isAnimated()) {
                event.getHook().editOriginalAttachments(FileUpload.fromData(generatedObject.getGifData(), "item.gif")).queue();
            } else {
                event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "item.png")).queue();
            }

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException | IllegalArgumentException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an item display", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            log.error("Encountered an error while generating an item display", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "text", description = "Generate some text")
    public void generateText(
        SlashCommandInteractionEvent event,
        @SlashOption(description = TEXT_DESCRIPTION) String text,
        @SlashOption(description = CENTERED_DESCRIPTION, required = false) Boolean centered,
        @SlashOption(description = ALPHA_DESCRIPTION, required = false) Integer alpha,
        @SlashOption(description = PADDING_DESCRIPTION, required = false) Integer padding,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(description = RENDER_BORDER_DESCRIPTION, required = false) Boolean renderBorder,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        centered = centered != null && centered;
        alpha = alpha == null ? 0 : alpha;
        padding = padding == null ? MinecraftTooltip.DEFAULT_PADDING : padding;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH * 3 : maxLineLength;
        renderBorder = renderBorder != null && renderBorder;

        try {
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
            GeneratedObject generatedObject = generatorImageBuilder.build();

            if (generatedObject.isAnimated()) {
                event.getHook().editOriginalAttachments(FileUpload.fromData(generatedObject.getGifData(), "text.gif")).queue();
            } else {
                event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "text.png")).queue();
            }

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating text", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the text!").queue();
            log.error("Encountered an error while generating text", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, group = "dialogue", subcommand = "single", description = "Generate dialogue for a single NPC")
    public void generateSingleDialogue(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Name of your NPC") String npcName,
        @SlashOption(description = "NPC dialogue, lines separated by \\n") String dialogue,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(description = "If the Abiphone symbol should be shown next to the dialogue", required = false) Boolean abiphone,
        @SlashOption(description = "Player head texture (username, URL, etc.)", required = false) String skinValue,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        abiphone = abiphone != null && abiphone;
        maxLineLength = maxLineLength == null ? 91 : maxLineLength;

        String[] lines = dialogue.split("\\\\n");
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "&e[NPC] " + npcName + "&f: " + (abiphone ? "&b%%ABIPHONE%%&f " : "") + lines[i];
            String line = lines[i];

            if (line.contains("{options:")) {
                String[] split = line.split("\\{options: ?");
                lines[i] = split[0];
                String[] options = split[1].replace("}", "").split(", ");
                lines[i] += "\n&eSelect an option: &f";
                for (String option : options) {
                    lines[i] += "&a" + option + "&f ";
                }
            }
        }

        dialogue = String.join("\n", lines);

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .withItemLore(dialogue)
            .withAlpha(0)
            .withPadding(MinecraftTooltip.DEFAULT_PADDING)
            .isPaddingFirstLine(false)
            .disableRarityLineBreak(false)
            .withMaxLineLength(maxLineLength)
            .bypassMaxLineLength(true);

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder()
                .addGenerator(tooltipGenerator.build());

            if (skinValue != null) {
                MinecraftPlayerHeadGenerator playerHeadGenerator = new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(skinValue)
                    .withScale(-2)
                    .build();
                generatorImageBuilder.addGenerator(0, playerHeadGenerator);
            }

            GeneratedObject generatedObject = generatorImageBuilder.build();

            if (generatedObject.isAnimated()) {
                event.getHook().editOriginalAttachments(FileUpload.fromData(generatedObject.getGifData(), "dialogue.gif")).queue();
            } else {
                event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "dialogue.png")).queue();
            }

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating dialogue", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the dialogue!").queue();
            log.error("Encountered an error while generating dialogue", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, group = "dialogue", subcommand = "multi", description = "Generate dialogue for multiple NPCs")
    public void generateMultiDialogue(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Names of your NPCs, separated by a comma") String npcNames,
        @SlashOption(description = "NPC dialogue, lines separated by \\n") String dialogue,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(description = "If the Abiphone symbol should be shown next to the dialogue", required = false) Boolean abiphone,
        @SlashOption(description = "Player head texture (username, URL, etc.)", required = false) String skinValue,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        abiphone = abiphone != null && abiphone;
        maxLineLength = maxLineLength == null ? 91 : maxLineLength;

        try {
            String[] lines = dialogue.split("\\\\n");
            String[] names = npcNames.split(", ?");

            for (int i = 0; i < lines.length; i++) {
                String[] split = lines[i].split(", ?");
                try {
                    int index = Integer.parseInt(split[0]);

                    if (index >= names.length) {
                        index = names.length - 1;
                    }

                    lines[i] = "&e[NPC] " + names[index] + "&f: " + (abiphone ? "&b%%ABIPHONE%%&f " : "") + split[1];
                    String line = lines[i];

                    if (line.contains("{options:")) {
                        String[] split2 = line.split("\\{options: ?");
                        lines[i] = split2[0];
                        String[] options = split2[1].replace("}", "").split(", ?");
                        lines[i] += "\n&eSelect an option: &f";
                        for (String option : options) {
                            lines[i] += "&a" + option + "&f ";
                        }
                    }
                } catch (NumberFormatException exception) {
                    throw new GeneratorException("Invalid NPC name index found in dialogue: " + split[0] + " (line " + (i + 1) + ")");
                }
            }

            dialogue = String.join("\n", lines);

            MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(dialogue)
                .withAlpha(0)
                .withPadding(MinecraftTooltip.DEFAULT_PADDING)
                .isPaddingFirstLine(false)
                .disableRarityLineBreak(false)
                .withMaxLineLength(maxLineLength)
                .bypassMaxLineLength(true);

            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder()
                .addGenerator(tooltipGenerator.build());

            if (skinValue != null) {
                MinecraftPlayerHeadGenerator playerHeadGenerator = new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(skinValue)
                    .withScale(-2)
                    .build();
                generatorImageBuilder.addGenerator(0, playerHeadGenerator);
            }

            GeneratedObject generatedObject = generatorImageBuilder.build();

            if (generatedObject.isAnimated()) {
                event.getHook().editOriginalAttachments(FileUpload.fromData(generatedObject.getGifData(), "dialogue.gif")).queue();
            } else {
                event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "dialogue.png")).queue();
            }

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating dialogue", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the dialogue!").queue();
            log.error("Encountered an error while generating dialogue", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "history", description = "View your command history")
    public void viewHistory(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        List<EmbedBuilder> embedBuilders = new ArrayList<>();
        DiscordUserRepository discordUserRepository = DiscordBotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        if (discordUserRepository.findById(event.getUser().getId()) != null) {
            List<String> history = discordUserRepository.findById(event.getUser().getId()).getGeneratorHistory().getCommandHistory();
            embedBuilders.addAll(history.stream()
                .map(s -> new EmbedBuilder().setDescription(s))
                .toList()
            );
        } else {
            embedBuilders.add(new EmbedBuilder().setTitle("No history found"));
        }

        try {
            File file = FileUtils.createTempFile("generator_history.txt", String.join("\n\n", embedBuilders.stream().map(EmbedBuilder::getDescriptionBuilder).toList()));
            event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
        } catch (IOException e) {
            event.getHook().editOriginal("An error occurred while fetching your generator command history!").queue();
            log.error("Encountered an error while fetching generator command history for {}", event.getUser().getId(), e);
        }
    }

    @SlashAutocompleteHandler(id = "power-strengths")
    public List<Command.Choice> powerStrengths(CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        return PowerStrength.getPowerStrengthNames().stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(userInput))
            .limit(25)
            .map(name -> new Command.Choice(name, name))
            .toList();
    }

    @SlashAutocompleteHandler(id = "item-names")
    public List<Command.Choice> itemNames(CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        return Spritesheet.getImageMap().keySet()
            .stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(userInput))
            .limit(25)
            .map(name -> new Command.Choice(name, name))
            .toList();
    }

    @SlashAutocompleteHandler(id = "item-rarities")
    public List<Command.Choice> itemRarities(CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        return Rarity.getRarityNames().stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(userInput))
            .limit(25)
            .map(name -> new Command.Choice(name, name))
            .toList();
    }

    @SlashAutocompleteHandler(id = "tooltip-side")
    public List<Command.Choice> tooltipSide(CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        return Arrays.stream(MinecraftTooltipGenerator.TooltipSide.values())
            .map(MinecraftTooltipGenerator.TooltipSide::name)
            .filter(side -> side.toLowerCase(Locale.ROOT).contains(userInput))
            .limit(25)
            .map(side -> new Command.Choice(side, side))
            .toList();
    }

    /**
     * Adds a slash command to the given {@link User}'s history.
     * This will silently fail if the user is not found in the database.
     *
     * @param user    The {@link User} to add the command to
     * @param command The command to add
     */
    private void addCommandToUserHistory(User user, String command) {
        DiscordUserRepository discordUserRepository = DiscordBotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        if (discordUserRepository == null) {
            return;
        }

        if (discordUserRepository.findById(user.getId()) != null) {
            DiscordUser discordUser = discordUserRepository.findById(user.getId());

            if (discordUser.getGeneratorHistory() == null) {
                discordUser.setGeneratorHistory(new GeneratorHistory());
            }

            discordUserRepository.findById(user.getId()).getGeneratorHistory().addCommand(command);
        }
    }

    /**
     * Gets the gen command auto hide preference from a {@link SlashCommandInteractionEvent}.
     *
     * @param event The {@link SlashCommandInteractionEvent} triggered by the user you want to get the auto hide preference from.
     *
     * @return The auto hide preference from the user.
     */
    private boolean getUserAutoHideSetting(SlashCommandInteractionEvent event) {
        try {
            DiscordUserRepository repository = DiscordBotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
            DiscordUser user = repository.findById(event.getMember().getId());

            if (user != null) {
                return user.isAutoHideGenCommands();
            }
        } catch (Exception exception) {
            return AUTO_HIDE_ON_ERROR;
        }

        return AUTO_HIDE_ON_ERROR;
    }

    /**
     * Calculates the stat value for a Power Stone stat based on the base power and magical power.
     *
     * @param stat         The {@link Stat} to calculate the value for
     * @param basePower    The base power of the stat
     * @param magicalPower The magical power of the Power Stone
     *
     * @return The calculated stat value
     */
    private double calculatePowerStoneStat(Stat stat, int basePower, int magicalPower) {
        double statMultiplier = stat.getPowerScalingMultiplier() != null ? stat.getPowerScalingMultiplier() : 1;
        return ((double) basePower / 100) * statMultiplier * 719.28 * Math.pow(Math.log(1 + (0.0019 * magicalPower)), 1.2);
    }
}
