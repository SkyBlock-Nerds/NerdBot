package net.hypixel.nerdbot.app.command;

import com.google.gson.JsonParseException;
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
import net.hypixel.nerdbot.app.generation.DiscordGenerationContext;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.data.PowerStrength;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.data.Stat;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.NbtParseException;
import net.aerh.imagegenerator.exception.TooManyTexturesException;
import net.aerh.imagegenerator.image.GeneratorImageBuilder;
import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.MinecraftInventoryGenerator;
import net.aerh.imagegenerator.impl.MinecraftItemGenerator;
import net.aerh.imagegenerator.impl.MinecraftNbtParser;
import net.aerh.imagegenerator.impl.MinecraftPlayerHeadGenerator;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.spritesheet.OverlayLoader;
import net.aerh.imagegenerator.spritesheet.Spritesheet;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class GeneratorCommands {

    public static final String BASE_COMMAND = "gen";

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
    private static final String CENTERED_DESCRIPTION = "Whether or not the tooltip should be centered";
    private static final String MAX_LINE_LENGTH_DESCRIPTION = "The max line length of the tooltip";
    private static final String FIRST_LINE_PADDING_DESCRIPTION = "Add a small amount of padding between the item name and the first line of lore";
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
    private static final String COLOR_DESCRIPTION = "The overlay color (e.g., red, blue, #FF0000)";

    private static final boolean AUTO_HIDE_ON_ERROR = true;

    @SlashCommand(name = BASE_COMMAND, subcommand = "display", description = "Display an item")
    public void generateItem(
        SlashCommandInteractionEvent event,
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION) String itemId,
        @SlashOption(description = EXTRA_DATA_DESCRIPTION, required = false) String data,
        @SlashOption(autocompleteId = "overlay-colors", description = COLOR_DESCRIPTION, required = false) String color,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = "If the item should look as if it being hovered over", required = false) Boolean hoverEffect,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = DURABILITY_DESCRIPTION, required = false) Integer durability,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        enchanted = enchanted != null && enchanted;
        hoverEffect = hoverEffect != null && hoverEffect;
        durability = durability == null ? 100 : durability;

        try {
            GeneratorImageBuilder item = new GeneratorImageBuilder().withContext(context);

            if (itemId.equalsIgnoreCase("player_head") && skinValue != null) {
                item.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(skinValue)
                    .build());
            } else {
                MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
                    .withItem(itemId)
                    .withData(data)
                    .withColor(color)
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
        @SlashOption(autocompleteId = "overlay-colors", description = COLOR_DESCRIPTION, required = false) String color,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = ALPHA_DESCRIPTION, required = false) Integer alpha,
        @SlashOption(description = PADDING_DESCRIPTION, required = false) Integer padding,
        @SlashOption(description = "Includes a slash command for you to edit", required = false) Boolean includeGenCommand,
        @SlashOption(description = "Whether the Power Stone shows as selected", required = false) Boolean selected,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

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
                    throw new GeneratorException("Stat `" + entry + "` is using an invalid format. Use `stat:value` and separate multiple entries with commas (e.g., `health:-50,damage:10`)");
                }

                String statName = stat[0].trim();

                int statValue;

                try {
                    statValue = Integer.parseInt(stat[1].trim());
                } catch (NumberFormatException e) {
                    throw new GeneratorException("Invalid number for stat `" + statName + "`: " + stat[1].trim() + ". Use `stat:value` (e.g., `health:-50`)");
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
                GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);
                MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                    .withName("&a" + powerName)
                    .withRarity(Rarity.byName("none"))
                    .withItemLore(itemLore)
                    .withAlpha(alpha)
                    .withPadding(padding)
                    .isTextCentered(false)
                    .hasFirstLinePadding(true)
                    .withRenderBorder(true);

                if (includeGenCommand != null && includeGenCommand) {
                    String slashCommand = tooltipGenerator.buildSlashCommand();

                    // I hate this, but it works *for now*. Should probably replace it later
                    if (itemId != null && !itemId.isBlank()) {
                        slashCommand += " item_id: " + itemId;
                    }

                    if (enchanted) {
                        slashCommand += " enchanted: True";
                    }

                    event.getHook().sendMessage("Your Power Stone has been parsed into a slash command:\n```" + slashCommand.trim() + "```").queue();
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
                            .withColor(color)
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
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

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

    @SlashCommand(name = BASE_COMMAND, subcommand = "recipe", description = "Generate a recipe")
    public void generateRecipe(
        SlashCommandInteractionEvent event,
        @SlashOption(description = RECIPE_STRING_DESCRIPTION) String recipe,
        @SlashOption(description = RENDER_BACKGROUND_DESCRIPTION, required = false) Boolean renderBackground,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        renderBackground = renderBackground == null || renderBackground;

        try {
            GeneratedObject generatedObject = new GeneratorImageBuilder().withContext(context)
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
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        drawBorder = drawBorder == null || drawBorder;
        boolean animateGlint = DiscordBotEnvironment.getBot().getConfig().getGeneratorConfig().getInventory().isAnimateGlint();
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;

        try {
            GeneratorImageBuilder generatedObject = new GeneratorImageBuilder().withContext(context)
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
                    .hasFirstLinePadding(false)
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
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        try {
            MinecraftNbtParser.ParsedNbt parsedNbt = MinecraftNbtParser.parse(nbt);
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);

            parsedNbt.getGenerators().forEach(generator -> {
                generatorImageBuilder.addGenerator(generator.build());
            });

            GeneratedObject generatedObject = generatorImageBuilder.build();

            Optional<ClassBuilder<? extends Generator>> tooltipGenerator = parsedNbt.getGenerators()
                .stream()
                .filter(gen -> gen instanceof MinecraftTooltipGenerator.Builder)
                .findFirst();
            if (tooltipGenerator.isEmpty()) {
                event.getHook().editOriginal("An error occurred.").queue();
                log.error("An error occurred while parsing the NBT string, there doesnt seem to be a tooltip but no nbt parser exception occurred.");
                return;
            }

            String slashCommand = ((MinecraftTooltipGenerator.Builder) tooltipGenerator.get()).buildSlashCommand();
            String commandItemId = parsedNbt.getParsedItemId();

            if (commandItemId != null && !commandItemId.isBlank()) {
                if (commandItemId.startsWith("minecraft:")) {
                    commandItemId = commandItemId.substring("minecraft:".length());
                }
                slashCommand += " item_id: " + commandItemId;
            }

            if (parsedNbt.getBase64Texture() != null && !parsedNbt.getBase64Texture().isBlank()) {
                slashCommand += " skin_value: " + parsedNbt.getBase64Texture();
            }

            if (parsedNbt.isEnchanted()) {
                slashCommand += " enchanted: True";
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
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while parsing the NBT!").queue();
            log.error("Encountered an error while parsing NBT", exception);
        } catch (TooManyTexturesException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (GeneratorException | NbtParseException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while parsing NBT", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "item", description = "Generate a full item image. Supports displaying items, recipes, tooltips & more")
    public void generateTooltip(
        SlashCommandInteractionEvent event,
        @SlashOption(description = NAME_DESCRIPTION) String itemName,
        @SlashOption(description = LORE_DESCRIPTION) String itemLore,
        @SlashOption(description = TYPE_DESCRIPTION, required = false) String type,
        @SlashOption(autocompleteId = "item-rarities", description = RARITY_DESCRIPTION, required = false) String rarity,
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION, required = false) String itemId,
        @SlashOption(autocompleteId = "overlay-colors", description = COLOR_DESCRIPTION, required = false) String color,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = RECIPE_STRING_DESCRIPTION, required = false) String recipe,
        @SlashOption(description = ALPHA_DESCRIPTION, required = false) Integer alpha,
        @SlashOption(description = PADDING_DESCRIPTION, required = false) Integer padding,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = CENTERED_DESCRIPTION, required = false) Boolean centered,
        @SlashOption(description = FIRST_LINE_PADDING_DESCRIPTION, required = false) Boolean firstLinePadding,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(autocompleteId = "tooltip-side", description = TOOLTIP_SIDE_DESCRIPTION, required = false) String tooltipSide,
        @SlashOption(description = RENDER_BORDER_DESCRIPTION, required = false) Boolean renderBorder,
        @SlashOption(description = DURABILITY_DESCRIPTION, required = false) Integer durability,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        type = type == null ? "" : type;
        rarity = rarity == null ? "none" : rarity;
        alpha = alpha == null ? MinecraftTooltip.DEFAULT_ALPHA : alpha;
        padding = padding == null ? MinecraftTooltip.DEFAULT_PADDING : padding;
        centered = centered != null && centered;
        enchanted = enchanted != null && enchanted;
        firstLinePadding = firstLinePadding == null || firstLinePadding;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;
        renderBorder = renderBorder == null || renderBorder;
        durability = durability == null ? 100 : durability;

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withName(itemName)
                .withRarity(Rarity.byName(rarity))
                .withItemLore(itemLore)
                .withType(type)
                .withAlpha(alpha)
                .withPadding(padding)
                .withMaxLineLength(maxLineLength)
                .isTextCentered(centered)
                .hasFirstLinePadding(firstLinePadding)
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
                        .withColor(color)
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
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        centered = centered != null && centered;
        alpha = alpha == null ? 0 : alpha;
        padding = padding == null ? MinecraftTooltip.DEFAULT_PADDING : padding;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH * 3 : maxLineLength;
        renderBorder = renderBorder != null && renderBorder;

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(text)
                .withAlpha(alpha)
                .withPadding(padding)
                .withMaxLineLength(maxLineLength)
                .isTextCentered(centered)
                .hasFirstLinePadding(false)
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
        @SlashOption(description = RENDER_BACKGROUND_DESCRIPTION, required = false) Boolean renderBackground,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        abiphone = abiphone != null && abiphone;
        maxLineLength = maxLineLength == null ? 91 : maxLineLength;
        renderBackground = renderBackground != null && renderBackground;

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
            .hasFirstLinePadding(false)
            .withMaxLineLength(maxLineLength)
            .withRenderBorder(renderBackground)
            .bypassMaxLineLength(true);

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context)
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
        @SlashOption(description = RENDER_BACKGROUND_DESCRIPTION, required = false) Boolean renderBackground,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        abiphone = abiphone != null && abiphone;
        maxLineLength = maxLineLength == null ? 91 : maxLineLength;
        renderBackground = renderBackground != null && renderBackground;

        try {
            String[] lines = dialogue.split("\\\\n");
            String[] names = npcNames.split(", ?");

            for (int i = 0; i < lines.length; i++) {
                String[] split = lines[i].split(", ?", 2);
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
                .hasFirstLinePadding(false)
                .withMaxLineLength(maxLineLength)
                .withRenderBorder(renderBackground)
                .bypassMaxLineLength(true);

            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context)
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

    @SlashAutocompleteHandler(id = "overlay-colors")
    public List<Command.Choice> overlayColors(CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        return OverlayLoader.getInstance().getAllColorOptionNames()
            .stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(userInput))
            .sorted()
            .limit(25)
            .map(name -> new Command.Choice(name, name))
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
     * Determine whether the slash command should be allowed to be executed
     *
     * @param event The SlashCommandInteractionEvent event instance
     *
     * @return True if it can execute, false otherwise
     */
    private boolean shouldBlockGeneratorCommand(SlashCommandInteractionEvent event) {
        ChannelConfig channelConfig = DiscordBotEnvironment.getBot().getConfig().getChannelConfig();
        String[] allowedChannelIds = channelConfig.getGenChannelIds();

        if (allowedChannelIds == null || allowedChannelIds.length == 0) {
            return false;
        }

        String channelId = event.getChannel().getId();
        boolean allowed = Arrays.asList(allowedChannelIds).contains(channelId);

        if (!allowed) {
            String response = "Generator commands can only be used in image generator channels.";

            if (event.isAcknowledged()) {
                event.getHook().sendMessage(response).setEphemeral(true).queue();
            } else {
                event.reply(response).setEphemeral(true).queue();
            }

            log.warn("Blocked generator command '{}' from user {} in channel {}", event.getCommandString(), event.getUser().getId(), event.getChannel().getId());
            return true;
        }

        return false;
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
        double logValue = Math.log(1 + (0.0019 * magicalPower));
        double magnitude = Math.pow(Math.abs(logValue), 1.2);
        double signedFactor = Math.signum(logValue) * magnitude;
        return ((double) basePower / 100) * statMultiplier * 719.28 * signedFactor;
    }
}
