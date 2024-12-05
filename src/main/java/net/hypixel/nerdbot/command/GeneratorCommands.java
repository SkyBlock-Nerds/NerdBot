package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.generator.data.PowerStrength;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.data.Stat;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.image.GeneratorImageBuilder;
import net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftItemGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftPlayerHeadGenerator;
import net.hypixel.nerdbot.generator.impl.tooltip.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.Util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Log4j2
public class GeneratorCommands extends ApplicationCommand {

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
    private static final String EMPTY_LINE_DESCRIPTION = "Whether or not the tooltip should have an empty line";
    private static final String CENTERED_DESCRIPTION = "Whether or not the tooltip should be centered";
    private static final String MAX_LINE_LENGTH_DESCRIPTION = "The max line length of the tooltip";
    private static final String NORMAL_ITEM_DESCRIPTION = "TODO";
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

    private static final int DEFAULT_PADDING = 0;
    private static final int DEFAULT_ALPHA = 245;
    private static final boolean AUTO_HIDE_ON_ERROR = true;

    @JDASlashCommand(name = BASE_COMMAND, group = "item", subcommand = "display", description = "Display an item")
    public void generateItem(
        GuildSlashEvent event,
        @AppOption(autocomplete = "item-names", description = ITEM_DESCRIPTION) String itemId,
        @AppOption(description = EXTRA_DATA_DESCRIPTION) @Optional String data,
        @AppOption(description = ENCHANTED_DESCRIPTION) @Optional Boolean enchanted,
        @AppOption(description = "If the item should look as if it being hovered over") @Optional Boolean hoverEffect,
        @AppOption(description = SKIN_VALUE_DESCRIPTION) @Optional String skinValue,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        enchanted = enchanted != null && enchanted;
        hoverEffect = hoverEffect != null && hoverEffect;

        try {
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

            GeneratedObject generatedObject = item.build();
            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "item.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an item display", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            log.error("Encountered an error while generating an item display", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "powerstone", description = "Generate an image of a Power Stone")
    public void generatePowerstone(
        GuildSlashEvent event,
        @AppOption(description = "The name of your Power Stone") String powerName,
        @AppOption(autocomplete = "power-strengths", description = "The strength of the Power Stone") String powerStrength,
        @AppOption(description = "The Magical Power to use in the stat calculations") int magicalPower,
        @AppOption(description = "The stats that scale with the given Magical Power") @Optional String scalingStats, // Desired Format: stat1:1,stat2:23,stat3:456
        @AppOption(description = "The stats that do not scale with the given Magical Power") @Optional String uniqueBonus, // Desired Format: stat1:1,stat2:23,stat3:456
        @AppOption(autocomplete = "item-names", description = ITEM_DESCRIPTION) @Optional String itemId,
        @AppOption(description = SKIN_VALUE_DESCRIPTION) @Optional String skinValue,
        @AppOption(description = ALPHA_DESCRIPTION) @Optional Integer alpha,
        @AppOption(description = PADDING_DESCRIPTION) @Optional Integer padding,
        @AppOption(description = "Includes a slash command for you to edit") @Optional Boolean includeGenFullCommand,
        @AppOption(description = "Whether the Power Stone shows as selected") @Optional Boolean selected,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        alpha = alpha == null ? DEFAULT_ALPHA : alpha;
        padding = padding == null ? DEFAULT_PADDING : padding;

        Function<String, HashMap<String, Integer>> parseStatsToMap = stats -> {
            HashMap<String, Integer> map = new HashMap<>();
            String[] entries = stats.split(",");

            for (String entry : entries) {
                String[] stat = entry.split(":");

                if (stat.length != 2 || stat[0].trim().isEmpty() || stat[1].trim().isEmpty()) {
                    throw new GeneratorException("Stat `" + entry + "` is using an invalid format");
                }

                String statName = stat[0].trim();

                if (map.containsKey(statName)) {
                    map.put(statName, map.get(statName) + Integer.parseInt(stat[1].trim()));
                }

                int statValue;

                try {
                    statValue = Integer.parseInt(stat[1].trim());
                } catch (NumberFormatException e) {
                    throw new GeneratorException("Invalid number for stat `" + statName + "`: " + stat[1].trim());
                }

                map.put(statName, statValue);
            }

            return map;
        };

        try {
            StringBuilder scalingStatsFormatted = new StringBuilder();
            Map<String, Integer> scalingStatsMap = scalingStats != null ? parseStatsToMap.apply(scalingStats) : new HashMap<>();

            for (Map.Entry<String, Integer> entry : scalingStatsMap.entrySet()) {
                String statName = entry.getKey();
                Integer basePower = entry.getValue();
                Stat stat = Stat.byName(statName);

                if (stat == null) {
                    throw new GeneratorException("`" + statName + "` is not a valid stat");
                }

                scalingStatsFormatted.append(String.format("%%%%%s:%s%%%%\\n", statName, Util.COMMA_SEPARATED_FORMAT.format(calculatePowerStoneStat(stat, magicalPower, basePower))));
            }

            if (!scalingStatsFormatted.isEmpty()) {
                scalingStatsFormatted = new StringBuilder("&7Stats:\\n")
                    .append(scalingStatsFormatted)
                    .append("\\n");
            }

            StringBuilder bonusStatsFormatted = new StringBuilder();
            HashMap<String, Integer> bonusStats = parseStatsToMap.apply(uniqueBonus);

            for (Map.Entry<String, Integer> entry : bonusStats.entrySet()) {
                String statName = entry.getKey();
                Integer statAmount = entry.getValue();
                Stat stat = Stat.byName(statName);

                if (stat == null) {
                    throw new GeneratorException("'" + statName + "' is not a valid stat");
                }

                bonusStatsFormatted.append(String.format("%%%%%s:%s%%%%\\n", statName, Util.COMMA_SEPARATED_FORMAT.format(statAmount)));
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
                Util.COMMA_SEPARATED_FORMAT.format(magicalPower)
            );

            try {
                GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
                MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                    .withName("&a" + powerName)
                    .withRarity(Rarity.byName("none"))
                    .withItemLore(itemLore)
                    .withAlpha(alpha)
                    .withPadding(padding)
                    .withEmptyLine(true)
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
                            .isBigImage()
                            .build());
                    }
                }

                generatorImageBuilder.addGenerator(tooltipGenerator.build());
                GeneratedObject generatedObject = generatorImageBuilder.build();

                event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "item.png")).queue();
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

    @JDASlashCommand(name = BASE_COMMAND, group = "item", subcommand = "search", description = "Search for an item")
    public void searchItem(GuildSlashEvent event, @AppOption(description = "The ID of the item to search for") String itemId, @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        List<Map.Entry<String, BufferedImage>> results = Spritesheet.searchForTexture(itemId);

        if (results.isEmpty()) {
            event.getHook().editOriginal("No results found for that item!").queue();
            return;
        }

        List<Map.Entry<String, BufferedImage>> topResults = results.subList(0, Math.min(10, results.size()));
        StringBuilder message = new StringBuilder("Top results for `" + itemId + "` (" + Util.COMMA_SEPARATED_FORMAT.format(results.size()) + " total):\n");

        for (Map.Entry<String, BufferedImage> entry : topResults) {
            message.append(" - `").append(entry.getKey()).append("`\n");
        }

        event.getHook().editOriginal(message.toString()).queue();
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "head", description = "Generate a player head")
    public void generateHead(
        GuildSlashEvent event,
        @AppOption(description = TEXTURE_DESCRIPTION) String texture,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
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

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "recipe", description = "Generate a recipe")
    public void generateRecipe(
        GuildSlashEvent event,
        @AppOption(description = RECIPE_STRING_DESCRIPTION) String recipeString,
        @AppOption(description = RENDER_BACKGROUND_DESCRIPTION) @Optional Boolean renderBackground,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        renderBackground = renderBackground == null || renderBackground;

        try {
            GeneratedObject generatedObject = new GeneratorImageBuilder()
                .addGenerator(new MinecraftInventoryGenerator.Builder()
                    .withRows(3)
                    .withSlotsPerRow(3)
                    .drawBorder(false)
                    .drawBackground(renderBackground)
                    .withInventoryString(recipeString)
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

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "inventory", description = "Generate an inventory")
    public void generateInventory(
        GuildSlashEvent event,
        @AppOption(description = INVENTORY_ROWS_DESCRIPTION) int rows,
        @AppOption(description = INVENTORY_COLUMNS_DESCRIPTION) int slotsPerRow,
        @AppOption(description = INVENTORY_CONTENTS_DESCRIPTION) String inventoryString,
        @AppOption(description = "Optional item lore displayed beside the inventory") @Optional String hoveredItemString,
        @AppOption(description = INVENTORY_NAME_DESCRIPTION) @Optional String containerName,
        @AppOption(description = RENDER_BORDER_DESCRIPTION) @Optional Boolean drawBorder,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        drawBorder = drawBorder == null || drawBorder;

        try {
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
                    .withAlpha(DEFAULT_ALPHA)
                    .withPadding(DEFAULT_PADDING)
                    .isPaddingFirstLine(false)
                    .withEmptyLine(false)
                    .withRenderBorder(true)
                    .build();

                generatedObject.addGenerator(tooltipGenerator);
            }

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.build().getImage()), "inventory.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an inventory", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that inventory!").queue();
            log.error("Encountered an error while generating an inventory", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, group = "parse", subcommand = "nbt", description = "Parse an NBT string")
    public void parseNbtString(
        GuildSlashEvent event,
        @AppOption(description = NBT_DESCRIPTION) String nbt,
        @AppOption(description = ALPHA_DESCRIPTION) @Optional Integer alpha,
        @AppOption(description = PADDING_DESCRIPTION) @Optional Integer padding,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        alpha = alpha == null ? DEFAULT_ALPHA : alpha;
        padding = padding == null ? DEFAULT_PADDING : padding;

        try {
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
                    event.getHook().editOriginal("There seems to be more than 1 texture in the player head's NBT data. Please double-check it is correct!").queue();
                    return;
                }

                String base64 = textures.get(0).getAsJsonObject().get("Value").getAsString();

                generatorImageBuilder.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(base64)
                    .build()
                );
            } else {
                generatorImageBuilder.addGenerator(new MinecraftItemGenerator.Builder()
                    .withItem(jsonObject.get("id").getAsString())
                    .isBigImage()
                    .build());
            }

            MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .parseNbtJson(jsonObject)
                .withAlpha(alpha)
                .withPadding(padding)
                .withMaxLineLength(Util.getLongestLine(jsonObject.get("tag").getAsJsonObject()
                    .get("display").getAsJsonObject()
                    .get("Lore").getAsJsonArray()
                    .asList()
                    .stream()
                    .map(JsonElement::getAsString)
                    .toList()
                ).getRight());

            GeneratedObject generatedObject = generatorImageBuilder.addGenerator(tooltipGenerator.build()).build();
            MessageEditBuilder builder = new MessageEditBuilder()
                .setContent("Your NBT input has been parsed into a slash command:" + System.lineSeparator() + "```" + System.lineSeparator() + tooltipGenerator.buildSlashCommand() + "```")
                .setFiles(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "parsed_nbt.png"));

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

    @JDASlashCommand(name = BASE_COMMAND, group = "item", subcommand = "full", description = "Generate a full item image. Supports displaying items, recipes, and tooltips")
    public void generateTooltip(
        GuildSlashEvent event,
        @AppOption(description = NAME_DESCRIPTION) String name,
        @AppOption(description = LORE_DESCRIPTION) String itemLore,
        @AppOption(description = TYPE_DESCRIPTION) @Optional String type,
        @AppOption(autocomplete = "item-rarities", description = RARITY_DESCRIPTION) @Optional String rarity,
        @AppOption(autocomplete = "item-names", description = ITEM_DESCRIPTION) @Optional String itemId,
        @AppOption(description = SKIN_VALUE_DESCRIPTION) @Optional String skinValue,
        @AppOption(description = RECIPE_STRING_DESCRIPTION) @Optional String recipeString,
        @AppOption(description = ALPHA_DESCRIPTION) @Optional Integer alpha,
        @AppOption(description = PADDING_DESCRIPTION) @Optional Integer padding,
        @AppOption(description = EMPTY_LINE_DESCRIPTION) @Optional Boolean emptyLine,
        @AppOption(description = CENTERED_DESCRIPTION) @Optional Boolean centered,
        @AppOption(description = NORMAL_ITEM_DESCRIPTION) @Optional Boolean paddingFirstLine,
        @AppOption(description = MAX_LINE_LENGTH_DESCRIPTION) @Optional Integer maxLineLength,
        @AppOption(autocomplete = "tooltip-side", description = TOOLTIP_SIDE_DESCRIPTION) @Optional String tooltipSide,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        type = type == null ? "" : type;
        rarity = rarity == null ? "none" : rarity;
        alpha = alpha == null ? DEFAULT_ALPHA : alpha;
        padding = padding == null ? DEFAULT_PADDING : padding;
        emptyLine = emptyLine == null || emptyLine;
        centered = centered != null && centered;
        paddingFirstLine = paddingFirstLine == null || paddingFirstLine;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withName(name)
                .withRarity(Rarity.byName(rarity))
                .withItemLore(itemLore)
                .withType(type)
                .withAlpha(alpha)
                .withPadding(padding)
                .withEmptyLine(emptyLine)
                .withMaxLineLength(maxLineLength)
                .isTextCentered(centered)
                .isPaddingFirstLine(paddingFirstLine)
                .withRenderBorder(true)
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
                        .isBigImage()
                        .build());
                }
            }

            if (recipeString != null) {
                generatorImageBuilder.addGenerator(0, new MinecraftInventoryGenerator.Builder()
                    .withRows(3)
                    .withSlotsPerRow(3)
                    .drawBorder(false)
                    .withInventoryString(recipeString)
                    .build()
                ).build();
            }

            if (tooltipSide != null && MinecraftTooltipGenerator.TooltipSide.valueOf(tooltipSide.toUpperCase()) == MinecraftTooltipGenerator.TooltipSide.LEFT) {
                generatorImageBuilder.addGenerator(0, tooltipGenerator);
            } else {
                generatorImageBuilder.addGenerator(tooltipGenerator);
            }

            GeneratedObject generatedObject = generatorImageBuilder.build();
            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "item.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException | IllegalArgumentException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an item display", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            log.error("Encountered an error while generating an item display", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "text", description = "Generate some text")
    public void generateText(
        GuildSlashEvent event,
        @AppOption(description = TEXT_DESCRIPTION) String text,
        @AppOption(description = CENTERED_DESCRIPTION) @Optional Boolean centered,
        @AppOption(description = ALPHA_DESCRIPTION) @Optional Integer alpha,
        @AppOption(description = PADDING_DESCRIPTION) @Optional Integer padding,
        @AppOption(description = MAX_LINE_LENGTH_DESCRIPTION) @Optional Integer maxLineLength,
        @AppOption(description = "Whether the border should be rendered (default: true)") @Optional Boolean renderBorder,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        event.deferReply(hidden).complete();

        centered = centered != null && centered;
        alpha = alpha == null ? 0 : alpha;
        padding = padding == null ? DEFAULT_PADDING : padding;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;
        renderBorder = renderBorder == null || renderBorder;

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(text)
                .withAlpha(alpha)
                .withPadding(padding)
                .withMaxLineLength(maxLineLength)
                .isTextCentered(centered)
                .isPaddingFirstLine(false)
                .withEmptyLine(false)
                .withRenderBorder(renderBorder)
                .build();

            generatorImageBuilder.addGenerator(tooltipGenerator);
            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatorImageBuilder.build().getImage()), "text.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating text", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the text!").queue();
            log.error("Encountered an error while generating text", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, group = "dialogue", subcommand = "single", description = "Generate dialogue for a single NPC")
    public void generateSingleDialogue(
        GuildSlashEvent event,
        @AppOption(description = "Name of your NPC") String npcName,
        @AppOption(description = "NPC dialogue, lines separated by \\n") String dialogue,
        @AppOption(description = MAX_LINE_LENGTH_DESCRIPTION) @Optional Integer maxLineLength,
        @AppOption(description = "If the Abiphone symbol should be shown next to the dialogue") @Optional Boolean abiphone,
        @AppOption(description = "Player head texture (username, URL, etc.)") @Optional String skinValue,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        abiphone = abiphone != null && abiphone;
        maxLineLength = maxLineLength == null ? 91 : maxLineLength;
        event.deferReply(hidden).complete();

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
            .withPadding(DEFAULT_PADDING)
            .isPaddingFirstLine(false)
            .withEmptyLine(false)
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

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatorImageBuilder.build().getImage()), "dialogue.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating dialogue", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the dialogue!").queue();
            log.error("Encountered an error while generating dialogue", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, group = "dialogue", subcommand = "multi", description = "Generate dialogue for multiple NPCs")
    public void generateMultiDialogue(
        GuildSlashEvent event,
        @AppOption(description = "Names of your NPCs, separated by a comma") String npcNames,
        @AppOption(description = "NPC dialogue, lines separated by \\n") String dialogue,
        @AppOption(description = MAX_LINE_LENGTH_DESCRIPTION) @Optional Integer maxLineLength,
        @AppOption(description = "If the Abiphone symbol should be shown next to the dialogue") @Optional Boolean abiphone,
        @AppOption(description = "Player head texture (username, URL, etc.)") @Optional String skinValue,
        @AppOption(description = HIDDEN_OUTPUT_DESCRIPTION) @Optional Boolean hidden
    ) {
        if (hidden == null) {
            hidden = getUserAutoHideSetting(event);
        }
        abiphone = abiphone != null && abiphone;
        maxLineLength = maxLineLength == null ? 91 : maxLineLength;
        event.deferReply(hidden).complete();

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
                .withPadding(DEFAULT_PADDING)
                .isPaddingFirstLine(false)
                .withEmptyLine(false)
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

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatorImageBuilder.build().getImage()), "dialogue.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating dialogue", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the dialogue!").queue();
            log.error("Encountered an error while generating dialogue", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "history", description = "View your command history")
    public void viewHistory(GuildSlashEvent event) {
        event.deferReply(true).complete();

        List<EmbedBuilder> embedBuilders = new ArrayList<>();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

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
            File file = Util.createTempFile("generator_history.txt", String.join("\n\n", embedBuilders.stream().map(EmbedBuilder::getDescriptionBuilder).toList()));
            event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
        } catch (IOException e) {
            event.getHook().editOriginal("An error occurred while fetching your generator command history!").queue();
            log.error("Encountered an error while fetching generator command history for {}", event.getUser().getId(), e);
        }
    }

    @AutocompletionHandler(name = "power-strengths", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> powerStrengths(CommandAutoCompleteInteractionEvent event) {
        return PowerStrength.getPowerStrengthNames();
    }

    @AutocompletionHandler(name = "item-names", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> itemNames(CommandAutoCompleteInteractionEvent event) {
        return Spritesheet.getImageMap().keySet()
            .stream()
            .toList();
    }

    @AutocompletionHandler(name = "item-rarities", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> itemRarities(CommandAutoCompleteInteractionEvent event) {
        return Rarity.getRarityNames();
    }

    @AutocompletionHandler(name = "tooltip-side", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> tooltipSide(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(MinecraftTooltipGenerator.TooltipSide.values())
            .map(MinecraftTooltipGenerator.TooltipSide::name)
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
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        if (discordUserRepository.findById(user.getId()) != null) {
            discordUserRepository.findById(user.getId()).getGeneratorHistory().addCommand(command);
        }
    }

    /**
     * Gets the gen command auto hide preference from a {@link GuildSlashEvent}.
     *
     * @param event The {@link GuildSlashEvent} triggered by the user you want to get the auto hide preference from.
     *
     * @return The auto hide preference from the user.
     */
    private boolean getUserAutoHideSetting(GuildSlashEvent event) {
        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        if (user != null) {
            return user.isAutoHideGenCommands();
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