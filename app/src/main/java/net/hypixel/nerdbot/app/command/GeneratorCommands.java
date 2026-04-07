package net.hypixel.nerdbot.app.command;

import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import net.aerh.jigsaw.api.Engine;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.core.resource.PackMetadata;
import net.aerh.jigsaw.skyblock.engine.EngineManager;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.core.generator.CompositeRequest;
import net.aerh.jigsaw.core.generator.InventoryRequest;
import net.aerh.jigsaw.core.generator.ItemRequest;
import net.aerh.jigsaw.core.generator.PlayerHeadRequest;
import net.aerh.jigsaw.core.generator.TooltipRequest;
import net.aerh.jigsaw.core.text.TextWrapper;
import net.aerh.jigsaw.exception.JigsawException;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.exception.RenderException;
import net.aerh.jigsaw.skyblock.data.PowerStrength;
import net.aerh.jigsaw.skyblock.data.Rarity;
import net.aerh.jigsaw.skyblock.data.Stat;
import net.aerh.jigsaw.skyblock.tooltip.SkyBlockTooltipBuilder;
import net.aerh.jigsaw.skyblock.tooltip.TooltipSide;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.app.generation.DiscordGenerationContext;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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

    private static final String RESOURCE_PACK_DESCRIPTION = "Resource pack to use for textures";

    private static final boolean AUTO_HIDE_ON_ERROR = true;

    private static EngineManager engineManager;

    /**
     * Initializes the engine manager with resource pack configuration.
     * Must be called during bot startup before any commands are processed.
     */
    public static void initializeEngineManager(Path packDirectory, String defaultPack, boolean vanillaFallback) {
        if (engineManager != null) {
            engineManager.close();
        }
        engineManager = new EngineManager(packDirectory, defaultPack, vanillaFallback);
    }

    /**
     * Reloads all resource packs from disk.
     */
    public static void reloadResourcePacks() {
        if (engineManager != null) {
            engineManager.reload();
        }
    }

    /**
     * Returns the engine manager. Falls back to a default instance if not yet initialized.
     */
    private static EngineManager getEngineManager() {
        if (engineManager == null) {
            engineManager = new EngineManager(Path.of("./resource-packs"), null, true);
        }
        return engineManager;
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "display", description = "Display an item")
    public void generateItem(
        SlashCommandInteractionEvent event,
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION) String itemId,
        @SlashOption(autocompleteId = "overlay-colors", description = COLOR_DESCRIPTION, required = false) String color,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = "If the item should look as if it being hovered over", required = false) Boolean hoverEffect,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = DURABILITY_DESCRIPTION, required = false) Integer durability,
        @SlashOption(autocompleteId = "resource-packs", description = RESOURCE_PACK_DESCRIPTION, required = false) String resourcePack,
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
            CompositeRequest.Builder compositeBuilder = CompositeRequest.builder();

            if (itemId.equalsIgnoreCase("player_head") && skinValue != null) {
                compositeBuilder.add(PlayerHeadRequest.fromBase64(skinValue).scale(10).build());
            } else {
                ItemRequest.Builder itemBuilder = ItemRequest.builder()
                    .itemId(itemId)
                    .enchanted(enchanted)
                    .hovered(hoverEffect)
                    .scale(10);

                if (durability != null && durability < 100) {
                    itemBuilder.durabilityPercent(durability / 100.0);
                }

                if (color != null && !color.isBlank()) {
                    itemBuilder.color(color);
                }

                compositeBuilder.add(itemBuilder.build());
            }

            Engine engine = getEngineManager().getEngine(resourcePack);
            GeneratorResult result = engine.render(compositeBuilder.build(), context);
            sendResult(event, result, "item");
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (RenderException | ParseException exception) {
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

        alpha = alpha == null ? TooltipRequest.DEFAULT_ALPHA : alpha;
        padding = padding == null ? TooltipRequest.DEFAULT_PADDING : padding;
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
                    throw new IllegalArgumentException("Stat `" + entry + "` is using an invalid format. Use `stat:value` and separate multiple entries with commas (e.g., `health:-50,damage:10`)");
                }

                String statName = stat[0].trim();

                int statValue;

                try {
                    statValue = Integer.parseInt(stat[1].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number for stat `" + statName + "`: " + stat[1].trim() + ". Use `stat:value` (e.g., `health:-50`)");
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
                Optional<Stat> stat = Stat.byName(statName);

                if (stat.isEmpty()) {
                    throw new IllegalArgumentException("`" + statName + "` is not a valid stat");
                }

                scalingStatsFormatted.append(String.format("%%%%%s:%s%%%%\\n", statName, StringUtils.COMMA_SEPARATED_FORMAT.format(calculatePowerStoneStat(stat.get(), basePower, magicalPower))));
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
                Optional<Stat> stat = Stat.byName(statName);

                if (stat.isEmpty()) {
                    throw new IllegalArgumentException("'" + statName + "' is not a valid stat");
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

            Optional<PowerStrength> resolvedPowerStrength = PowerStrength.byName(powerStrength);
            String itemLore = String.format(itemLoreTemplate,
                resolvedPowerStrength.isEmpty() ? powerStrength : resolvedPowerStrength.get().formattedDisplay(),
                scalingStatsFormatted,
                bonusStatsFormatted,
                StringUtils.COMMA_SEPARATED_FORMAT.format(magicalPower)
            );

            try {
                CompositeRequest.Builder compositeBuilder = CompositeRequest.builder()
                    .scaleFactor(2);
                SkyBlockTooltipBuilder.Builder tooltipBuilder = SkyBlockTooltipBuilder.builder()
                    .name("&a" + powerName)
                    .rarity(Rarity.byName("none").orElse(null))
                    .lore(itemLore)
                    .alpha(alpha)
                    .padding(padding)
                    .centered(false)
                    .firstLinePadding(true)
                    .renderBorder(true);

                if (includeGenCommand != null && includeGenCommand) {
                    String slashCommand = tooltipBuilder.buildSlashCommand();

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
                        PlayerHeadRequest.Builder headBuilder = PlayerHeadRequest.fromBase64(
                            skinValue != null ? skinValue : ""
                        );

                        compositeBuilder.add(headBuilder.build());
                    } else {
                        ItemRequest.Builder itemBuilder = ItemRequest.builder()
                            .itemId(itemId)
                            .enchanted(enchanted);

                        if (color != null && !color.isBlank()) {
                            itemBuilder.color(color);
                        }

                        compositeBuilder.add(itemBuilder.build());
                    }
                }

                compositeBuilder.add(tooltipBuilder.build());
                GeneratorResult result = getEngineManager().getDefaultEngine().render(compositeBuilder.build(), context);
                sendResult(event, result, "powerstone");
                addCommandToUserHistory(event.getUser(), event.getCommandString());
            } catch (RenderException | ParseException | IllegalArgumentException exception) {
                event.getHook().editOriginal(exception.getMessage()).queue();
                log.error("Encountered an error while generating a Power Stone", exception);
            } catch (IOException exception) {
                event.getHook().editOriginal("An error occurred while generating that Power Stone!").queue();
                log.error("Encountered an error while generating a Power Stone", exception);
            }
        } catch (IllegalArgumentException exception) {
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

        List<Map.Entry<String, BufferedImage>> results = getEngineManager().getDefaultEngine().sprites().searchAll(itemId);

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
            InventoryRequest inventoryRequest = InventoryRequest.builder()
                .rows(3)
                .slotsPerRow(3)
                .drawBorder(false)
                .drawBackground(renderBackground)
                .withInventoryString(recipe)
                .build();

            GeneratorResult result = getEngineManager().getDefaultEngine().render(inventoryRequest, context);

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(result.firstFrame()), "recipe.png")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (RenderException | ParseException exception) {
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
        @SlashOption(autocompleteId = "resource-packs", description = RESOURCE_PACK_DESCRIPTION, required = false) String resourcePack,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        drawBorder = drawBorder == null || drawBorder;
        maxLineLength = maxLineLength == null ? TooltipRequest.DEFAULT_MAX_LINE_LENGTH : maxLineLength;

        try {
            CompositeRequest.Builder compositeBuilder = CompositeRequest.builder()
                .scaleFactor(2);

            InventoryRequest.Builder inventoryBuilder = InventoryRequest.builder()
                .rows(rows)
                .slotsPerRow(slotsPerRow)
                .drawBorder(drawBorder)
                .drawBackground(true)
                .withInventoryString(inventoryString);

            if (containerName != null && !containerName.isBlank()) {
                inventoryBuilder.title(containerName);
            }

            compositeBuilder.add(inventoryBuilder.build());

            if (hoveredItemString != null && !hoveredItemString.isBlank()) {
                TooltipRequest tooltipRequest = SkyBlockTooltipBuilder.builder()
                    .lore(TextWrapper.stripActualNewlines(hoveredItemString))
                    .alpha(TooltipRequest.DEFAULT_ALPHA)
                    .padding(TooltipRequest.DEFAULT_PADDING)
                    .firstLinePadding(false)
                    .maxLineLength(maxLineLength)
                    .renderBorder(true)
                    .build();

                compositeBuilder.add(tooltipRequest);
            }

            Engine engine = getEngineManager().getEngine(resourcePack);
            GeneratorResult result = engine.render(compositeBuilder.build(), context);
            sendResult(event, result, "inventory");
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (RenderException | ParseException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an inventory", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that inventory!").queue();
            log.error("Encountered an error while generating an inventory", exception);
        }
    }

    private static final int MAX_ATTACHMENT_SIZE_BYTES = 64 * 1024; // 64 KB

    @SlashCommand(name = BASE_COMMAND, subcommand = "parse", description = "Parse an NBT string (JSON or SNBT format)")
    public void parseNbtString(
        SlashCommandInteractionEvent event,
        @SlashOption(description = NBT_DESCRIPTION, required = false) String nbt,
        @SlashOption(description = "Upload a text file containing NBT data", required = false) Message.Attachment attachment,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        String nbtInput;
        try {
            nbtInput = resolveNbtInput(nbt, attachment);
        } catch (IllegalArgumentException e) {
            event.getHook().editOriginal(e.getMessage()).queue();
            return;
        }

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        try {
            ParsedItem parsedItem = getEngineManager().getDefaultEngine().parseNbt(nbtInput);

            // Build item request
            ItemRequest.Builder itemBuilder = ItemRequest.builder()
                .itemId(parsedItem.itemId())
                .enchanted(parsedItem.enchanted());
            parsedItem.dyeColor().ifPresent(itemBuilder::dyeColor);

            // Build tooltip from lore
            SkyBlockTooltipBuilder.Builder tooltipBuilder = SkyBlockTooltipBuilder.builder();
            parsedItem.displayName().ifPresent(tooltipBuilder::name);
            if (!parsedItem.lore().isEmpty()) {
                tooltipBuilder.lore(String.join("\\n", parsedItem.lore()));
            }

            // Handle player heads
            CompositeRequest.Builder compositeBuilder = CompositeRequest.builder()
                .scaleFactor(2);
            if (parsedItem.base64Texture().isPresent() && !parsedItem.base64Texture().get().isBlank()) {
                compositeBuilder.add(PlayerHeadRequest.fromBase64(parsedItem.base64Texture().get())
                    .build());
            } else {
                compositeBuilder.add(itemBuilder.build());
            }
            compositeBuilder.add(tooltipBuilder.build());

            GeneratorResult result = getEngineManager().getDefaultEngine().render(compositeBuilder.build(), context);

            String slashCommand = tooltipBuilder.buildSlashCommand();
            String commandItemId = parsedItem.itemId();

            if (commandItemId != null && !commandItemId.isBlank()) {
                if (commandItemId.startsWith("minecraft:")) {
                    commandItemId = commandItemId.substring("minecraft:".length());
                }
                slashCommand += " item_id: " + commandItemId;
            }

            if (parsedItem.base64Texture().isPresent() && !parsedItem.base64Texture().get().isBlank()) {
                slashCommand += " skin_value: " + parsedItem.base64Texture().get();
            }

            if (parsedItem.enchanted()) {
                slashCommand += " enchanted: True";
            }

            if (parsedItem.dyeColor().isPresent()) {
                slashCommand += " color: #" + String.format("%06X", parsedItem.dyeColor().get() & 0xFFFFFF);
            }

            // Escape newlines in lore so the slash command is a single line
            slashCommand = slashCommand.replace("\n", "\\n");

            String sourceLabel = attachment != null ? "attachment" : "text input";
            MessageEditBuilder builder = new MessageEditBuilder()
                .setContent("Your NBT " + sourceLabel + " has been parsed into a slash command:" + System.lineSeparator() + "```" + System.lineSeparator() + slashCommand + "```");

            if (result.isAnimated()) {
                builder.setFiles(FileUpload.fromData(((GeneratorResult.AnimatedImage) result).toGifBytes(), "parsed_nbt.gif"));
            } else {
                builder.setFiles(FileUpload.fromData(ImageUtil.toFile(result.firstFrame()), "parsed_nbt.png"));
            }

            event.getHook().editOriginal(builder.build()).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (JsonParseException exception) {
            event.getHook().editOriginal("You provided badly formatted NBT!").queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while parsing the NBT!").queue();
            log.error("Encountered an error while parsing NBT", exception);
        } catch (RenderException | ParseException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while parsing NBT", exception);
        }
    }

    private String resolveNbtInput(String nbtText, net.dv8tion.jda.api.entities.Message.Attachment attachment) {
        if (attachment != null) {
            if (attachment.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
                throw new IllegalArgumentException("Attachment is too large! Maximum size is " + StringUtils.formatSize(MAX_ATTACHMENT_SIZE_BYTES));
            }

            String fileName = attachment.getFileName().toLowerCase(Locale.ROOT);
            if (!fileName.endsWith(".txt") && !fileName.endsWith(".json") && !fileName.endsWith(".snbt") && !fileName.endsWith(".nbt")) {
                throw new IllegalArgumentException("Unsupported file type! Please upload a `.txt`, `.json`, `.snbt`, or `.nbt` file.");
            }

            try (InputStream inputStream = attachment.getProxy().download().join()) {
                String content = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    throw new IllegalArgumentException("The uploaded file is empty!");
                }

                log.debug("Read {} bytes from attachment '{}'", content.length(), attachment.getFileName());
                return content;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to read attachment '{}'", attachment.getFileName(), e);
                throw new IllegalArgumentException("Failed to read the uploaded file: " + e.getMessage());
            }
        }

        if (nbtText == null || nbtText.isBlank()) {
            throw new IllegalArgumentException("Please provide NBT data either as text or by uploading a file.");
        }

        return nbtText;
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
        @SlashOption(autocompleteId = "resource-packs", description = RESOURCE_PACK_DESCRIPTION, required = false) String resourcePack,
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
        alpha = alpha == null ? TooltipRequest.DEFAULT_ALPHA : alpha;
        padding = padding == null ? TooltipRequest.DEFAULT_PADDING : padding;
        centered = centered != null && centered;
        enchanted = enchanted != null && enchanted;
        firstLinePadding = firstLinePadding == null || firstLinePadding;
        maxLineLength = maxLineLength == null ? TooltipRequest.DEFAULT_MAX_LINE_LENGTH : maxLineLength;
        renderBorder = renderBorder == null || renderBorder;
        durability = durability == null ? 100 : durability;

        try {
            CompositeRequest.Builder compositeBuilder = CompositeRequest.builder()
                .scaleFactor(2);
            TooltipRequest tooltipRequest = SkyBlockTooltipBuilder.builder()
                .name(itemName)
                .rarity(Rarity.byName(rarity).orElse(null))
                .lore(TextWrapper.stripActualNewlines(itemLore))
                .type(type)
                .alpha(alpha)
                .padding(padding)
                .maxLineLength(maxLineLength)
                .centered(centered)
                .firstLinePadding(firstLinePadding)
                .renderBorder(renderBorder)
                .build();

            if (itemId != null) {
                if (itemId.equalsIgnoreCase("player_head")) {
                    PlayerHeadRequest.Builder headBuilder = PlayerHeadRequest.fromBase64(
                        skinValue != null ? skinValue : ""
                    );

                    compositeBuilder.add(headBuilder.build());
                } else {
                    ItemRequest.Builder itemBuilder = ItemRequest.builder()
                        .itemId(itemId)
                        .enchanted(enchanted)
                        .scale(10);

                    if (durability != null && durability < 100) {
                        itemBuilder.durabilityPercent(durability / 100.0);
                    }

                    if (color != null && !color.isBlank()) {
                        itemBuilder.color(color);
                    }

                    compositeBuilder.add(itemBuilder.build());
                }
            }

            if (recipe != null && !recipe.isBlank()) {
                compositeBuilder.add(0, InventoryRequest.builder()
                    .rows(3)
                    .slotsPerRow(3)
                    .drawBorder(renderBorder)
                    .withInventoryString(recipe)
                    .build()
                );
            }

            try {
                if (tooltipSide != null && TooltipSide.valueOf(tooltipSide.toUpperCase()) == TooltipSide.LEFT) {
                    compositeBuilder.add(0, tooltipRequest);
                } else {
                    compositeBuilder.add(tooltipRequest);
                }
            } catch (IllegalArgumentException ignored) {
                // Fallback to default side if an invalid value was provided
                compositeBuilder.add(tooltipRequest);
            }

            Engine engine = getEngineManager().getEngine(resourcePack);
            GeneratorResult result = engine.render(compositeBuilder.build(), context);
            sendResult(event, result, "item");
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (RenderException | ParseException | IllegalArgumentException exception) {
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
        padding = padding == null ? TooltipRequest.DEFAULT_PADDING : padding;
        maxLineLength = maxLineLength == null ? TooltipRequest.DEFAULT_MAX_LINE_LENGTH * 3 : maxLineLength;
        renderBorder = renderBorder != null && renderBorder;

        try {
            TooltipRequest tooltipRequest = SkyBlockTooltipBuilder.builder()
                .lore(TextWrapper.stripActualNewlines(text))
                .alpha(alpha)
                .padding(padding)
                .maxLineLength(maxLineLength)
                .centered(centered)
                .firstLinePadding(false)
                .renderBorder(renderBorder)
                .build();

            GeneratorResult result = getEngineManager().getDefaultEngine().render(tooltipRequest, context);
            sendResult(event, result, "text");
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (RenderException | ParseException exception) {
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

        TooltipRequest tooltipRequest = SkyBlockTooltipBuilder.builder()
            .lore(dialogue)
            .alpha(0)
            .padding(TooltipRequest.DEFAULT_PADDING)
            .firstLinePadding(false)
            .maxLineLength(maxLineLength)
            .bypassMaxLineLength(true)
            .renderBorder(renderBackground)
            .build();

        try {
            CompositeRequest.Builder compositeBuilder = CompositeRequest.builder()
                .scaleFactor(2)
                .add(tooltipRequest);

            if (skinValue != null) {
                PlayerHeadRequest playerHeadRequest = PlayerHeadRequest.fromBase64(skinValue)
                    .build();
                compositeBuilder.add(0, playerHeadRequest);
            }

            GeneratorResult result = getEngineManager().getDefaultEngine().render(compositeBuilder.build(), context);
            sendResult(event, result, "dialogue");
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (RenderException | ParseException exception) {
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
                    throw new IllegalArgumentException("Invalid NPC name index found in dialogue: " + split[0] + " (line " + (i + 1) + ")");
                }
            }

            dialogue = String.join("\n", lines);

            TooltipRequest tooltipRequest = SkyBlockTooltipBuilder.builder()
                .lore(dialogue)
                .alpha(0)
                .padding(TooltipRequest.DEFAULT_PADDING)
                .firstLinePadding(false)
                .maxLineLength(maxLineLength)
                .bypassMaxLineLength(true)
                .renderBorder(renderBackground)
                .build();

            CompositeRequest.Builder compositeBuilder = CompositeRequest.builder()
                .scaleFactor(2)
                .add(tooltipRequest);

            if (skinValue != null) {
                PlayerHeadRequest playerHeadRequest = PlayerHeadRequest.fromBase64(skinValue)
                    .build();
                compositeBuilder.add(0, playerHeadRequest);
            }

            GeneratorResult result = getEngineManager().getDefaultEngine().render(compositeBuilder.build(), context);
            sendResult(event, result, "dialogue");
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (IllegalArgumentException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating dialogue", exception);
        } catch (RenderException | ParseException exception) {
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

        if (discordUserRepository.findById(event.getUser().getId()).isSuccess()) {
            List<String> history = discordUserRepository.findById(event.getUser().getId()).orElse(null).getGeneratorHistory().getCommandHistory();
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

        return getEngineManager().getDefaultEngine().sprites().getAllSprites().keySet()
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

        return Arrays.stream(TooltipSide.values())
            .map(TooltipSide::name)
            .filter(side -> side.toLowerCase(Locale.ROOT).contains(userInput))
            .limit(25)
            .map(side -> new Command.Choice(side, side))
            .toList();
    }

    @SlashAutocompleteHandler(id = "overlay-colors")
    public List<Command.Choice> overlayColors(CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        return getEngineManager().getDefaultEngine().overlayColors().getAllColorOptionNames().stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(userInput))
            .sorted()
            .limit(25)
            .map(name -> new Command.Choice(name, name))
            .toList();
    }

    @SlashAutocompleteHandler(id = "resource-packs")
    public List<Command.Choice> resourcePacks(CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        return getEngineManager().availablePackNames().stream()
            .filter(name -> name.contains(userInput))
            .limit(25)
            .map(name -> new Command.Choice(name, name))
            .toList();
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "resource-packs", description = "List available resource packs")
    public void listResourcePacks(
        SlashCommandInteractionEvent event,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;
        event.deferReply(hidden).complete();

        Collection<String> packNames = getEngineManager().availablePackNames();

        if (packNames.isEmpty()) {
            event.getHook().editOriginal("No resource packs loaded. Using vanilla textures only.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder("**Loaded Resource Packs:**\n");
        for (String name : packNames) {
            getEngineManager().getPackMetadata(name).ifPresent(meta ->
                sb.append("- **").append(name).append("** (format ")
                  .append(meta.packFormat()).append(") - ")
                  .append(meta.description().isBlank() ? "No description" : meta.description())
                  .append("\n")
            );
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }

    /**
     * Splits a string containing newlines (both literal {@code \n} and the escape sequence {@code \\n})
     * into a list of individual lines suitable for {@link TooltipRequest.Builder#lines(List)}.
     */
    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        // Normalize escaped newlines to actual newlines, then split
        String normalized = text.replace("\\n", "\n");
        return Arrays.asList(normalized.split("\n", -1));
    }

    /**
     * Sends a GeneratorResult to the Discord channel, handling both static and animated images.
     */
    private void sendResult(SlashCommandInteractionEvent event, GeneratorResult result, String fileBaseName) throws IOException {
        if (result.isAnimated()) {
            event.getHook().editOriginalAttachments(FileUpload.fromData(((GeneratorResult.AnimatedImage) result).toGifBytes(), fileBaseName + ".gif")).queue();
        } else {
            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(result.firstFrame()), fileBaseName + ".png")).queue();
        }
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

        if (discordUserRepository.findById(user.getId()).isSuccess()) {
            DiscordUser discordUser = discordUserRepository.findById(user.getId()).orElse(null);

            if (discordUser.getGeneratorHistory() == null) {
                discordUser.setGeneratorHistory(new GeneratorHistory());
            }

            discordUser.getGeneratorHistory().addCommand(command);
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
            DiscordUser user = repository.findById(event.getMember().getId()).orElse(null);

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
        double statMultiplier = stat.powerScalingMultiplier() != null ? stat.powerScalingMultiplier() : 1;
        double logValue = Math.log(1 + (0.0019 * magicalPower));
        double magnitude = Math.pow(Math.abs(logValue), 1.2);
        double signedFactor = Math.signum(logValue) * magnitude;
        return ((double) basePower / 100) * statMultiplier * 719.28 * signedFactor;
    }
}
