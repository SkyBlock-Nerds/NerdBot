package net.hypixel.nerdbot.app.command;

import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.context.GenerationContext;
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
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.spritesheet.OverlayLoader;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import net.aerh.imagegenerator.text.TextColorRemap;
import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.generation.DiscordGenerationContext;
import net.hypixel.nerdbot.app.generation.pack.ResourcePackService;
import net.hypixel.nerdbot.app.util.GlyphListing;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
import net.hypixel.nerdbot.marmalade.functional.Pair;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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
    private static final String ITEM_MODEL_DESCRIPTION = "The minecraft:item_model ref to render (e.g. hypixel_skyblock:item/jacob/cactus_knife)";
    private static final String PACK_DESCRIPTION = "The resource pack used to resolve item textures";
    private static final String TOOLTIP_STYLE_DESCRIPTION = "The pack tooltip style to render with (defaults to the rarity's configured style)";
    private static final String ANIMATED_DESCRIPTION = "Whether animated pack textures render as a GIF (False forces a static render)";

    private static final String MINECRAFT_NAMESPACE = "minecraft:";

    private static final boolean AUTO_HIDE_ON_ERROR = true;

    @SlashCommand(name = BASE_COMMAND, subcommand = "display", description = "Display an item", guildOnly = true)
    public void generateItem(
        SlashCommandInteractionEvent event,
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION, required = false) String itemId,
        @SlashOption(autocompleteId = "item-names", description = ITEM_MODEL_DESCRIPTION, required = false) String itemModel,
        @SlashOption(description = EXTRA_DATA_DESCRIPTION, required = false) String data,
        @SlashOption(autocompleteId = "overlay-colors", description = COLOR_DESCRIPTION, required = false) String color,
        @SlashOption(description = ENCHANTED_DESCRIPTION, required = false) Boolean enchanted,
        @SlashOption(description = "If the item should look as if it being hovered over", required = false) Boolean hoverEffect,
        @SlashOption(description = SKIN_VALUE_DESCRIPTION, required = false) String skinValue,
        @SlashOption(description = DURABILITY_DESCRIPTION, required = false) Integer durability,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
        @SlashOption(description = ANIMATED_DESCRIPTION, required = false) Boolean animated,
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
        animated = animated == null || animated;
        durability = durability == null ? 100 : durability;

        try {
            requireSingleItemAddress(itemId, itemModel);
            requireAnItemAddress(itemId, itemModel);
            PackId packId = resolvePackOption(pack);
            GeneratorImageBuilder item = new GeneratorImageBuilder().withContext(context);

            if (itemModel == null && itemId.equalsIgnoreCase("player_head") && skinValue != null) {
                item.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(skinValue)
                    .build());
            } else {
                MinecraftItemGenerator.Builder itemBuilder = addressItem(new MinecraftItemGenerator.Builder(), itemId, itemModel)
                    .withData(data)
                    .withColor(color)
                    .isEnchanted(enchanted)
                    .withHoverEffect(hoverEffect);
                applyItemPack(itemBuilder, packId, animated);

                if (durability != null) {
                    itemBuilder.withDurability(durability);
                }

                item.addGenerator(itemBuilder.build());
            }

            GeneratedObject generatedObject = item.build();

            event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "item")).queue();

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an item display", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            log.error("Encountered an error while generating an item display", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "powerstone", description = "Generate an image of a Power Stone", guildOnly = true)
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
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
        @SlashOption(description = ANIMATED_DESCRIPTION, required = false) Boolean animated,
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
        animated = animated == null || animated;

        try {
            StringBuilder scalingStatsFormatted = new StringBuilder();
            Map<String, Integer> scalingStatsMap = parseStatsToMap(scalingStats);

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
            Map<String, Integer> bonusStats = parseStatsToMap(uniqueBonus);

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
                PackId packId = resolvePackOption(pack);
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
                applyPackTheme(tooltipGenerator, packId, null, Rarity.byName("none"));

                if (includeGenCommand != null && includeGenCommand) {
                    // The builder round-trips its own fields (including pack and tooltip_style);
                    // only options that belong to the item generator still need appending by hand.
                    String slashCommand = appendPowerStoneItemOptions(tooltipGenerator.buildSlashCommand(), itemId, enchanted, animated);

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
                        MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
                            .withItem(itemId)
                            .withColor(color)
                            .isEnchanted(enchanted);
                        applyItemPack(itemBuilder, packId, animated);

                        generatorImageBuilder.addGenerator(itemBuilder.build());
                    }
                }

                generatorImageBuilder.addGenerator(tooltipGenerator.build());
                GeneratedObject generatedObject = generatorImageBuilder.build();

                event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "powerstone")).queue();

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

    @SlashCommand(name = BASE_COMMAND, subcommand = "search", description = "Search for an item", guildOnly = true)
    public void searchItem(SlashCommandInteractionEvent event, @SlashOption(description = "The ID of the item to search for") String itemId, @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        List<String> spritesheetResults = Spritesheet.searchForTexture(itemId).stream().map(Pair::first).toList();
        List<String> packResults = SkyBlockNerdsBot.resourcePackService().searchItemRefs(itemId);

        if (spritesheetResults.isEmpty() && packResults.isEmpty()) {
            event.getHook().editOriginal("No results found for that item!").queue();
            return;
        }

        StringBuilder message = new StringBuilder();
        appendSearchResults(message, "Top results for `" + itemId + "`", spritesheetResults);
        appendSearchResults(message, "Resource pack results for `" + itemId + "`", packResults);

        event.getHook().editOriginal(message.toString()).queue();
    }

    private static final int SEARCH_RESULT_LIMIT = 10;
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private static final String SEARCH_TRUNCATION_MARKER = " - ...\n";

    /**
     * Appends a titled block of up to {@link #SEARCH_RESULT_LIMIT} results to the search reply.
     * This is called once per result source onto a shared builder, so every append (the block
     * header, each line, and the truncation marker) is bounded by Discord's
     * {@link #MAX_MESSAGE_LENGTH} limit: a block whose header would not fit is skipped entirely,
     * and lines stop as soon as the next one plus the marker would overflow.
     */
    /**
     * Parse a comma-separated {@code stat:value} string (e.g. {@code "health:-50,damage:10"}) into a
     * map of stat name to summed value.
     *
     * <p>Blank entries are ignored and duplicate stats are summed. Extracted from the powerstone
     * {@code /gen} handler (where it was an inline lambda used for both scaling and bonus stats) so
     * the parsing and its error messages can be unit-tested.
     *
     * @param stats the raw stats string, may be {@code null} or blank (yielding an empty map)
     * @return a map of stat name to summed value
     * @throws GeneratorException if an entry is not {@code stat:value} or the value is not an integer
     */
    static Map<String, Integer> parseStatsToMap(String stats) {
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
    }

    static void appendSearchResults(StringBuilder message, String header, List<String> results) {
        if (results.isEmpty()) {
            return;
        }

        String headerLine = header + " (" + StringUtils.COMMA_SEPARATED_FORMAT.format(results.size()) + " total):\n";

        if (message.length() + headerLine.length() + SEARCH_TRUNCATION_MARKER.length() > MAX_MESSAGE_LENGTH) {
            return;
        }

        message.append(headerLine);

        for (String result : results.subList(0, Math.min(SEARCH_RESULT_LIMIT, results.size()))) {
            String line = " - `" + result + "`\n";

            if (message.length() + line.length() + SEARCH_TRUNCATION_MARKER.length() > MAX_MESSAGE_LENGTH) {
                message.append(SEARCH_TRUNCATION_MARKER);
                break;
            }

            message.append(line);
        }
    }

    private static final int SYMBOLS_PER_PAGE = 20;

    @SlashCommand(name = BASE_COMMAND, subcommand = "symbols", description = "List the placeholder names usable in generator text", guildOnly = true)
    public void listSymbols(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Only show names containing this text", required = false) String filter,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;
        event.deferReply(hidden).complete();

        List<String> rows = GlyphListing.buildRows(filter);

        if (rows.isEmpty()) {
            event.getHook().editOriginal("No placeholders match `" + filter + "`!").queue();
            return;
        }

        PaginatedResponse<String> pagination = PaginatedResponse.forText(rows, SYMBOLS_PER_PAGE, GlyphListing::buildPage, "gen-symbols-page");
        pagination.sendMessage(event);

        event.getHook().retrieveOriginal().queue(message ->
            PaginationManager.registerPagination(message.getId(), pagination)
        );
    }

    @SlashComponentHandler(id = "gen-symbols-pagination", patterns = {"gen-symbols-page:*"})
    public void handleSymbolsPagination(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        if (!PaginationManager.handleButtonInteraction(event)) {
            log.warn("Could not find symbols pagination for message ID: {}", event.getMessageId());
            event.getHook().editOriginal("This pagination has expired. Please run the command again.").queue();
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "recipe", description = "Generate a recipe", guildOnly = true)
    public void generateRecipe(
        SlashCommandInteractionEvent event,
        @SlashOption(description = RECIPE_STRING_DESCRIPTION) String recipe,
        @SlashOption(description = RENDER_BACKGROUND_DESCRIPTION, required = false) Boolean renderBackground,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
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
                    .withPack(resolvePackOption(pack))
                    .withInventoryString(recipe)
                    .build())
                .build();

            event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "recipe")).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating a recipe", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that recipe!").queue();
            log.error("Encountered an error while generating a recipe", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "inventory", description = "Generate an inventory", guildOnly = true)
    public void generateInventory(
        SlashCommandInteractionEvent event,
        @SlashOption(description = INVENTORY_ROWS_DESCRIPTION) int rows,
        @SlashOption(description = INVENTORY_COLUMNS_DESCRIPTION) int slotsPerRow,
        @SlashOption(description = INVENTORY_CONTENTS_DESCRIPTION) String inventoryString,
        @SlashOption(description = "Optional item lore displayed beside the inventory", required = false) String hoveredItemString,
        @SlashOption(description = INVENTORY_NAME_DESCRIPTION, required = false) String containerName,
        @SlashOption(description = RENDER_BORDER_DESCRIPTION, required = false) Boolean drawBorder,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        drawBorder = drawBorder == null || drawBorder;
        boolean animateGlint = SkyBlockNerdsBot.config().getGeneratorConfig().getInventory().isAnimateGlint();
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;

        try {
            PackId packId = resolvePackOption(pack);
            GeneratorImageBuilder generatedObject = new GeneratorImageBuilder().withContext(context)
                .addGenerator(new MinecraftInventoryGenerator.Builder()
                    .withRows(rows)
                    .withSlotsPerRow(slotsPerRow)
                    .drawBorder(drawBorder)
                    .drawBackground(true)
                    .withAnimateGlint(animateGlint)
                    .withContainerTitle(containerName)
                    .withPack(packId)
                    .withInventoryString(inventoryString)
                    .build());

            if (hoveredItemString != null && !hoveredItemString.isBlank()) {
                MinecraftTooltipGenerator.Builder tooltipBuilder = new MinecraftTooltipGenerator.Builder()
                    .withItemLore(TextWrapper.stripActualNewlines(hoveredItemString))
                    .withAlpha(MinecraftTooltip.DEFAULT_ALPHA)
                    .withPadding(MinecraftTooltip.DEFAULT_PADDING)
                    .hasFirstLinePadding(false)
                    .withMaxLineLength(maxLineLength)
                    .withScaleFactor(Math.min(2, MinecraftInventoryGenerator.getScaleFactor()))
                    .withRenderBorder(true);
                applyPackTheme(tooltipBuilder, packId);

                generatedObject.addGenerator(tooltipBuilder.build());
            }

            GeneratedObject finalObject = generatedObject.build();

            event.getHook().editOriginalAttachments(renderAttachment(finalObject, "inventory")).queue();

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an inventory", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that inventory!").queue();
            log.error("Encountered an error while generating an inventory", exception);
        }
    }

    private static final int MAX_ATTACHMENT_SIZE_BYTES = 64 * 1024; // 64 KB

    @SlashCommand(name = BASE_COMMAND, subcommand = "parse", description = "Parse an NBT string (JSON or SNBT format)", guildOnly = true)
    public void parseNbtString(
        SlashCommandInteractionEvent event,
        @SlashOption(description = NBT_DESCRIPTION, required = false) String nbt,
        @SlashOption(description = "Upload a text file containing NBT data", required = false) Message.Attachment attachment,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
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
            PackId packId = resolvePackOption(pack);
            ParsedRender render = renderParsedNbtWithFallback(SkyBlockNerdsBot.resourcePackService(), nbtInput, packId, context);

            MinecraftNbtParser.ParsedNbt parsedNbt = render.parsedNbt();
            GeneratedObject generatedObject = render.image();
            ItemModelFallback fallback = render.fallback();
            String itemModel = parsedNbt.getParsedItemModel();

            Optional<ClassBuilder<? extends Generator>> tooltipGenerator = parsedNbt.getGenerators()
                .stream()
                .filter(gen -> gen instanceof MinecraftTooltipGenerator.Builder)
                .findFirst();
            if (tooltipGenerator.isEmpty()) {
                event.getHook().editOriginal("An error occurred.").queue();
                log.error("An error occurred while parsing the NBT string, there doesnt seem to be a tooltip but no nbt parser exception occurred.");
                return;
            }

            String slashCommand = appendParsedItemOptions(
                ((MinecraftTooltipGenerator.Builder) tooltipGenerator.get()).buildSlashCommand(),
                parsedNbt.getParsedItemId(),
                itemModel,
                parsedNbt.getBase64Texture(),
                parsedNbt.isEnchanted()
            );

            // Escape newlines in lore so the slash command is a single line
            slashCommand = slashCommand.replace("\n", "\\n");

            String sourceLabel = attachment != null ? "attachment" : "text input";
            String content = "Your NBT " + sourceLabel + " has been parsed into a slash command:"
                + System.lineSeparator() + "```" + System.lineSeparator() + slashCommand + "```";

            String fallbackNotice = itemModelFallbackNotice(fallback,
                packId == null ? null : packId.toString(), itemModel, parsedNbt.getParsedItemId());
            if (fallbackNotice != null) {
                content += System.lineSeparator() + "-# " + fallbackNotice;
            }

            MessageEditBuilder builder = new MessageEditBuilder().setContent(content);

            builder.setFiles(renderAttachment(generatedObject, "parsed_nbt"));

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

    @SlashCommand(name = BASE_COMMAND, subcommand = "item", description = "Generate a full item image. Supports displaying items, recipes, tooltips & more", guildOnly = true)
    public void generateTooltip(
        SlashCommandInteractionEvent event,
        @SlashOption(description = NAME_DESCRIPTION) String itemName,
        @SlashOption(description = LORE_DESCRIPTION) String itemLore,
        @SlashOption(description = TYPE_DESCRIPTION, required = false) String type,
        @SlashOption(autocompleteId = "item-rarities", description = RARITY_DESCRIPTION, required = false) String rarity,
        @SlashOption(autocompleteId = "item-names", description = ITEM_DESCRIPTION, required = false) String itemId,
        @SlashOption(autocompleteId = "item-names", description = ITEM_MODEL_DESCRIPTION, required = false) String itemModel,
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
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
        @SlashOption(autocompleteId = "tooltip-styles", description = TOOLTIP_STYLE_DESCRIPTION, required = false) String tooltipStyle,
        @SlashOption(description = ANIMATED_DESCRIPTION, required = false) Boolean animated,
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
        animated = animated == null || animated;
        firstLinePadding = firstLinePadding == null || firstLinePadding;
        maxLineLength = maxLineLength == null ? MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH : maxLineLength;
        renderBorder = renderBorder == null || renderBorder;
        durability = durability == null ? 100 : durability;

        try {
            requireSingleItemAddress(itemId, itemModel);
            PackId packId = resolvePackOption(pack);
            Rarity itemRarity = Rarity.byName(rarity);
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);
            MinecraftTooltipGenerator.Builder tooltipBuilder = new MinecraftTooltipGenerator.Builder()
                .withName(itemName)
                .withRarity(itemRarity)
                .withItemLore(TextWrapper.stripActualNewlines(itemLore))
                .withType(type)
                .withAlpha(alpha)
                .withPadding(padding)
                .withMaxLineLength(maxLineLength)
                .isTextCentered(centered)
                .hasFirstLinePadding(firstLinePadding)
                .withRenderBorder(renderBorder);
            requireBorderForTooltipStyle(tooltipStyle, renderBorder);
            applyPackTheme(tooltipBuilder, packId, tooltipStyle, itemRarity);
            MinecraftTooltipGenerator tooltipGenerator = tooltipBuilder.build();

            if (itemId != null || itemModel != null) {
                if (itemModel == null && itemId.equalsIgnoreCase("player_head")) {
                    MinecraftPlayerHeadGenerator.Builder generator = new MinecraftPlayerHeadGenerator.Builder()
                        .withScale(-2);

                    if (skinValue != null) {
                        generator.withSkin(skinValue);
                    }

                    generatorImageBuilder.addGenerator(generator.build());
                } else {
                    MinecraftItemGenerator.Builder itemBuilder = addressItem(new MinecraftItemGenerator.Builder(), itemId, itemModel)
                        .withColor(color)
                        .isEnchanted(enchanted);
                    applyItemPack(itemBuilder, packId, animated);

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
                    .withPack(packId)
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

            event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "item")).queue();

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException | IllegalArgumentException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating an item display", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            log.error("Encountered an error while generating an item display", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "text", description = "Generate some text", guildOnly = true)
    public void generateText(
        SlashCommandInteractionEvent event,
        @SlashOption(description = TEXT_DESCRIPTION) String text,
        @SlashOption(description = CENTERED_DESCRIPTION, required = false) Boolean centered,
        @SlashOption(description = ALPHA_DESCRIPTION, required = false) Integer alpha,
        @SlashOption(description = PADDING_DESCRIPTION, required = false) Integer padding,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(description = RENDER_BORDER_DESCRIPTION, required = false) Boolean renderBorder,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
        @SlashOption(autocompleteId = "tooltip-styles", description = TOOLTIP_STYLE_DESCRIPTION, required = false) String tooltipStyle,
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
            PackId packId = resolvePackOption(pack);
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);
            MinecraftTooltipGenerator.Builder tooltipBuilder = new MinecraftTooltipGenerator.Builder()
                .withItemLore(TextWrapper.stripActualNewlines(text))
                .withAlpha(alpha)
                .withPadding(padding)
                .withMaxLineLength(maxLineLength)
                .isTextCentered(centered)
                .hasFirstLinePadding(false)
                .withRenderBorder(renderBorder);
            requireBorderForTooltipStyle(tooltipStyle, renderBorder);
            applyPackTheme(tooltipBuilder, packId, tooltipStyle, null);

            generatorImageBuilder.addGenerator(tooltipBuilder.build());
            GeneratedObject generatedObject = generatorImageBuilder.build();

            event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "text")).queue();

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating text", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the text!").queue();
            log.error("Encountered an error while generating text", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, group = "dialogue", subcommand = "single", description = "Generate dialogue for a single NPC", guildOnly = true)
    public void generateSingleDialogue(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Name of your NPC") String npcName,
        @SlashOption(description = "NPC dialogue, lines separated by \\n") String dialogue,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(description = "If the Abiphone symbol should be shown next to the dialogue", required = false) Boolean abiphone,
        @SlashOption(description = "Player head texture (username, URL, etc.)", required = false) String skinValue,
        @SlashOption(description = RENDER_BACKGROUND_DESCRIPTION, required = false) Boolean renderBackground,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
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
            MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(buildSingleDialogue(npcName, dialogue, abiphone))
                .withAlpha(0)
                .withPadding(MinecraftTooltip.DEFAULT_PADDING)
                .hasFirstLinePadding(false)
                .withMaxLineLength(maxLineLength)
                .withRenderBorder(renderBackground)
                .bypassMaxLineLength(true);
            applyPackTheme(tooltipGenerator, resolvePackOption(pack));

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

            event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "dialogue")).queue();

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating dialogue", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the dialogue!").queue();
            log.error("Encountered an error while generating dialogue", exception);
        }
    }

    @SlashCommand(name = BASE_COMMAND, group = "dialogue", subcommand = "multi", description = "Generate dialogue for multiple NPCs", guildOnly = true)
    public void generateMultiDialogue(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Names of your NPCs, separated by a comma") String npcNames,
        @SlashOption(description = "NPC dialogue, lines separated by \\n") String dialogue,
        @SlashOption(description = MAX_LINE_LENGTH_DESCRIPTION, required = false) Integer maxLineLength,
        @SlashOption(description = "If the Abiphone symbol should be shown next to the dialogue", required = false) Boolean abiphone,
        @SlashOption(description = "Player head texture (username, URL, etc.)", required = false) String skinValue,
        @SlashOption(description = RENDER_BACKGROUND_DESCRIPTION, required = false) Boolean renderBackground,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
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
            MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(buildMultiDialogue(npcNames, dialogue, abiphone))
                .withAlpha(0)
                .withPadding(MinecraftTooltip.DEFAULT_PADDING)
                .hasFirstLinePadding(false)
                .withMaxLineLength(maxLineLength)
                .withRenderBorder(renderBackground)
                .bypassMaxLineLength(true);
            applyPackTheme(tooltipGenerator, resolvePackOption(pack));

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

            event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "dialogue")).queue();

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating dialogue", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the dialogue!").queue();
            log.error("Encountered an error while generating dialogue", exception);
        }
    }

    /**
     * Appends the options driving the Power Stone's item render onto its reconstructed slash
     * command. The tooltip builder already round-trips its own fields (name, lore, pack,
     * tooltip_style) via {@link MinecraftTooltipGenerator.Builder#buildSlashCommand()}; item_id,
     * enchanted and animated belong to the sibling item generator, so they are appended here so a
     * re-run reproduces the same render (an animated Power Stone stays animated on round-trip).
     *
     * @param slashCommand The tooltip builder's reconstructed command to append onto
     * @param itemId       The rendered item id, or null/blank when no item was rendered
     * @param enchanted    Whether the item render was enchanted
     * @param animated     Whether the item render used the pack's animated textures; false emits
     *                     an explicit opt-out because animation defaults on
     *
     * @return The slash command with the item options appended
     */
    static String appendPowerStoneItemOptions(String slashCommand, String itemId, boolean enchanted, boolean animated) {
        if (itemId != null && !itemId.isBlank()) {
            slashCommand += " item_id: " + itemId;
        }

        if (enchanted) {
            slashCommand += " enchanted: True";
        }

        if (!animated) {
            slashCommand += " animated: False";
        }

        return slashCommand;
    }

    /**
     * Appends the options addressing the item a parsed NBT payload rendered. An item carrying a
     * {@code minecraft:item_model} component is addressed by that model rather than by its item
     * id, so the emitted command reproduces the preview instead of falling back to whatever base
     * item the pack happens to sit on (Hypixel ships most custom items on {@code paper}).
     *
     * @param slashCommand The tooltip builder's reconstructed command to append onto
     * @param itemId       The parsed item id, used only when no item model is present
     * @param itemModel    The {@code minecraft:item_model} component value, or null when absent
     * @param skinValue    The resolved player head texture, or null when the item is not a head
     * @param enchanted    Whether the item render was enchanted
     *
     * @return The slash command with the item options appended
     */
    static String appendParsedItemOptions(String slashCommand, String itemId, String itemModel, String skinValue, boolean enchanted) {
        if (itemModel != null && !itemModel.isBlank()) {
            slashCommand += " item_model: " + itemModel;
        } else if (itemId != null && !itemId.isBlank()) {
            slashCommand += " item_id: " + stripMinecraftNamespace(itemId);
        }

        if (skinValue != null && !skinValue.isBlank()) {
            slashCommand += " skin_value: " + skinValue;
        }

        if (enchanted) {
            slashCommand += " enchanted: True";
        }

        return slashCommand;
    }

    /**
     * The item id without its default namespace, so the emitted command matches what the
     * {@code item_id} option expects. Item model refs keep their namespace: it selects the
     * asset directory the model is looked up in.
     */
    private static String stripMinecraftNamespace(String itemId) {
        return itemId.startsWith(MINECRAFT_NAMESPACE) ? itemId.substring(MINECRAFT_NAMESPACE.length()) : itemId;
    }

    /**
     * Rejects addressing one item both ways at once, mirroring the generator's own
     * {@code withItem}/{@code withItemModel} validation but failing with a message the user sees
     * in Discord rather than an IllegalArgumentException stack trace.
     *
     * @throws GeneratorException If both options were supplied
     */
    static void requireSingleItemAddress(String itemId, String itemModel) {
        if (itemId != null && !itemId.isBlank() && itemModel != null && !itemModel.isBlank()) {
            throw new GeneratorException("The item_id and item_model options are mutually exclusive; use one or the other!");
        }
    }

    /**
     * The outcome of rendering a parsed NBT payload: the image, the parse result it came from,
     * and which stand-in (if any) had to be drawn in place of the addressed item model.
     */
    record ParsedRender(GeneratedObject image, MinecraftNbtParser.ParsedNbt parsedNbt, ItemModelFallback fallback) {
    }

    /**
     * Parses and renders an NBT payload, degrading gracefully when an addressed item model cannot
     * be produced.
     *
     * <p>Whether an item model can render is only knowable by rendering it: a ref can index
     * cleanly and still fail (a definition pointing at a model the pack never shipped, for one),
     * and the bot's pack is routinely older than the items people paste in. So the model is
     * attempted as addressed, and only a failure triggers the stand-in - the head texture where
     * the item has one, otherwise the base item the model sits on. A failure with no item model
     * involved is rethrown untouched: that is a genuine error about the user's input, not a pack
     * being behind.
     *
     * @param packService The pack service backing the render
     * @param nbtInput    The raw NBT, re-parsed for the retry so builders start clean
     * @param packId      The resolved pack, or null for vanilla
     * @param context     The generation context, may be null outside an interaction
     *
     * @throws GeneratorException If the render fails for any reason other than an item model the
     *                            pack cannot produce
     */
    static ParsedRender renderParsedNbtWithFallback(ResourcePackService packService, String nbtInput,
                                                    @Nullable PackId packId, @Nullable GenerationContext context) throws IOException {
        MinecraftNbtParser.ParsedNbt parsedNbt = MinecraftNbtParser.parse(nbtInput);

        try {
            return new ParsedRender(renderParsedNbt(packService, parsedNbt, packId, context, ItemModelFallback.NONE),
                parsedNbt, ItemModelFallback.NONE);
        } catch (GeneratorException exception) {
            // PackResolveException extends GeneratorException, so this covers both a pack that
            // cannot produce the model and a model that resolves to nothing renderable.
            // Without an item model the stand-in would be the same item-id render that just
            // failed, so this rethrows rather than burning a second attempt on the same outcome.
            ItemModelFallback fallback = itemModelFallback(parsedNbt.getParsedItemModel(), parsedNbt.getBase64Texture());
            if (fallback == ItemModelFallback.NONE) {
                throw exception;
            }

            log.info("Could not render item model '{}' from pack '{}', falling back to {}",
                parsedNbt.getParsedItemModel(), packId, fallback, exception);
            // Re-parsed rather than re-used: building a generator fills defaults into its builder,
            // so the retry starts from clean builders instead of half-configured ones.
            MinecraftNbtParser.ParsedNbt retry = MinecraftNbtParser.parse(nbtInput);
            return new ParsedRender(renderParsedNbt(packService, retry, packId, context, fallback), retry, fallback);
        }
    }

    /**
     * Renders a parsed NBT payload, substituting the item generator per the given fallback. The
     * tooltip is themed here rather than at the call site so the emitted slash command round-trips
     * the pack and rarity-derived style, reproducing the preview exactly.
     */
    private static GeneratedObject renderParsedNbt(ResourcePackService packService, MinecraftNbtParser.ParsedNbt parsedNbt,
                                                   @Nullable PackId packId, @Nullable GenerationContext context,
                                                   ItemModelFallback fallback) throws IOException {
        GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder().withContext(context);

        for (ClassBuilder<? extends Generator> generator : parsedNbt.getGenerators()) {
            if (generator instanceof MinecraftTooltipGenerator.Builder tooltipBuilder) {
                applyPackTheme(packService, tooltipBuilder, packId, null, tooltipBuilder.getRarity());
            } else if (generator instanceof MinecraftItemGenerator.Builder itemBuilder) {
                if (fallback == ItemModelFallback.PLAYER_HEAD) {
                    generatorImageBuilder.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                        .withSkin(parsedNbt.getBase64Texture())
                        .withScale(-2)
                        .build());
                    continue;
                }

                if (fallback == ItemModelFallback.ITEM_ID) {
                    // Rebuilt rather than re-pointed: the parsed builder already committed to
                    // withItemModel, which the generator treats as exclusive with withItem. A dye
                    // color on the original is lost here, which is acceptable on a render that is
                    // already a stand-in for the model the pack could not produce.
                    MinecraftItemGenerator.Builder baseItem = new MinecraftItemGenerator.Builder()
                        .withItem(parsedNbt.getParsedItemId())
                        .isEnchanted(parsedNbt.isEnchanted());
                    applyItemPack(packService, baseItem, packId, true);
                    generatorImageBuilder.addGenerator(baseItem.build());
                    continue;
                }

                // The texture's own animation data decides the output, matching the item commands'
                // default: an animated pack texture parses into a GIF preview and everything else
                // stays static. The emitted command round-trips because the animated option
                // defaults on everywhere.
                applyItemPack(packService, itemBuilder, packId, true);
            }

            generatorImageBuilder.addGenerator(generator.build());
        }

        return generatorImageBuilder.build();
    }

    /** What a {@code minecraft:item_model} render degrades to when the pack cannot produce it. */
    enum ItemModelFallback {
        /** The model resolved (or none was asked for): render it as addressed. */
        NONE,
        /** Render the item's player head texture instead. */
        PLAYER_HEAD,
        /** Render the base item the model sits on instead. */
        ITEM_ID
    }

    /**
     * Picks what to render after a render failed with an item model addressed. Hypixel commonly
     * ships an item with both a {@code minecraft:profile} texture (so vanilla clients see a
     * textured head) and a {@code minecraft:item_model} (so pack clients see the real model), and
     * the bot's pack is routinely older than the items people paste in. Degrading - to the head
     * where one exists, otherwise to the base item the model sits on - keeps the tooltip preview
     * working; the caller pairs it with {@link #itemModelFallbackNotice} so the substitution is
     * never silent.
     *
     * <p>{@link ItemModelFallback#NONE} for an item that was never addressed by model: that
     * failure has nothing to do with item models and must surface to the user unchanged.
     *
     * @param itemModel     The item model ref that was being rendered, or null when addressing by id
     * @param base64Texture The resolved head texture, or null/blank when the item has none
     */
    static ItemModelFallback itemModelFallback(String itemModel, String base64Texture) {
        if (itemModel == null || itemModel.isBlank()) {
            return ItemModelFallback.NONE;
        }

        return base64Texture != null && !base64Texture.isBlank()
            ? ItemModelFallback.PLAYER_HEAD
            : ItemModelFallback.ITEM_ID;
    }

    /**
     * The line telling the user their preview is not what the NBT actually asked for, so a wrong
     * looking icon reads as a stale pack rather than a broken generator.
     *
     * @return The notice, or null when nothing was substituted
     */
    @Nullable
    static String itemModelFallbackNotice(ItemModelFallback fallback, @Nullable String packId, String itemModel, String itemId) {
        if (fallback == ItemModelFallback.NONE) {
            return null;
        }

        String source = packId == null ? "Vanilla" : "The `" + packId + "` pack";
        String rendered = fallback == ItemModelFallback.PLAYER_HEAD
            ? "the item's player head texture"
            : "the base item `" + stripMinecraftNamespace(itemId) + "`";

        return source + " could not resolve item model `" + itemModel + "`, so " + rendered
            + " was rendered instead. The pack is likely older than this item.";
    }

    /**
     * Rejects a render that names neither an item id nor an item model, for the subcommands whose
     * whole output is the item. {@code /gen item} does not use this: there, both options are
     * genuinely optional because the tooltip renders on its own.
     *
     * @throws GeneratorException If neither option was supplied
     */
    static void requireAnItemAddress(String itemId, String itemModel) {
        if ((itemId == null || itemId.isBlank()) && (itemModel == null || itemModel.isBlank())) {
            throw new GeneratorException("Set either the item_id or the item_model option to pick what to render!");
        }
    }

    /**
     * Points an item builder at whichever address the user gave, keeping the two mutually
     * exclusive builder calls in one place. Callers validate the pair first with
     * {@link #requireSingleItemAddress(String, String)}.
     */
    private static MinecraftItemGenerator.Builder addressItem(MinecraftItemGenerator.Builder builder, String itemId, String itemModel) {
        return itemModel != null && !itemModel.isBlank()
            ? builder.withItemModel(itemModel)
            : builder.withItem(itemId);
    }

    static String buildSingleDialogue(String npcName, String dialogue, boolean abiphone) {
        String[] lines = dialogue.split("\\\\n");

        for (int i = 0; i < lines.length; i++) {
            lines[i] = expandDialogueOptions(npcDialogueLine(npcName, abiphone, lines[i]), i + 1);
        }

        return String.join("\n", lines);
    }

    static String buildMultiDialogue(String npcNames, String dialogue, boolean abiphone) {
        String[] lines = dialogue.split("\\\\n");
        String[] names = npcNames.split(", ?");

        for (int i = 0; i < lines.length; i++) {
            String[] split = lines[i].split(", ?", 2);

            if (split.length < 2) {
                throw new GeneratorException("Each line must start with an NPC index followed by a comma (line " + (i + 1) + ")! Example: 0, Hello!");
            }

            int index;
            try {
                index = Integer.parseInt(split[0]);
            } catch (NumberFormatException exception) {
                throw new GeneratorException("Invalid NPC name index found in dialogue: " + split[0] + " (line " + (i + 1) + ")");
            }

            if (index < 0) {
                throw new GeneratorException("Invalid NPC name index found in dialogue: " + split[0] + " (line " + (i + 1) + ")");
            }

            if (index >= names.length) {
                index = names.length - 1;
            }

            lines[i] = expandDialogueOptions(npcDialogueLine(names[index], abiphone, split[1]), i + 1);
        }

        return String.join("\n", lines);
    }

    private static String npcDialogueLine(String npcName, boolean abiphone, String text) {
        return "&e[NPC] " + npcName + "&f: " + (abiphone ? "&b%%ABIPHONE%%&f " : "") + text;
    }

    private static String expandDialogueOptions(String line, int lineNumber) {
        if (!line.contains("{options:")) {
            return line;
        }

        String[] split = line.split("\\{options: ?");

        if (split.length < 2 || split[1].replace("}", "").isBlank()) {
            throw new GeneratorException("Malformed {options: ...} block in dialogue (line " + lineNumber + ")! Expected format: {options: Option 1, Option 2}");
        }

        StringBuilder result = new StringBuilder(split[0]).append("\n&eSelect an option: &f");

        for (String option : split[1].replace("}", "").split(", ?")) {
            result.append("&a").append(option).append("&f ");
        }

        return result.toString();
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "history", description = "View your command history", guildOnly = true)
    public void viewHistory(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        List<String> history = getCommandHistory(findDiscordUser(event.getUser()));

        if (history.isEmpty()) {
            event.getHook().editOriginal("No history found").queue();
            return;
        }

        try {
            File file = FileUtils.createTempFile("generator_history.txt", String.join("\n\n", history));
            event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
        } catch (IOException e) {
            event.getHook().editOriginal("An error occurred while fetching your generator command history!").queue();
            log.error("Encountered an error while fetching generator command history for {}", event.getUser().getId(), e);
        }
    }

    @SlashAutocompleteHandler(id = "power-strengths")
    public List<Command.Choice> powerStrengths(CommandAutoCompleteInteractionEvent event) {
        return toChoices(PowerStrength.getPowerStrengthNames().stream(), event);
    }

    @SlashAutocompleteHandler(id = "item-names")
    public List<Command.Choice> itemNames(CommandAutoCompleteInteractionEvent event) {
        ResourcePackService packService = SkyBlockNerdsBot.resourcePackService();
        OptionMapping packOption = event.getOption("pack");
        Stream<String> packRefs = packService.itemRefsForOption(packOption == null ? null : packOption.getAsString()).stream();

        return toChoices(Stream.concat(Spritesheet.getImageMap().keySet().stream(), packRefs), event);
    }

    @SlashAutocompleteHandler(id = "pack-ids")
    public List<Command.Choice> packIds(CommandAutoCompleteInteractionEvent event) {
        return toChoices(SkyBlockNerdsBot.resourcePackService().packOptionChoices().stream(), event);
    }

    @SlashAutocompleteHandler(id = "tooltip-styles")
    public List<Command.Choice> tooltipStyles(CommandAutoCompleteInteractionEvent event) {
        OptionMapping packOption = event.getOption("pack");
        return toChoices(SkyBlockNerdsBot.resourcePackService()
            .tooltipStyleChoices(packOption == null ? null : packOption.getAsString()).stream(), event);
    }

    @SlashAutocompleteHandler(id = "item-rarities")
    public List<Command.Choice> itemRarities(CommandAutoCompleteInteractionEvent event) {
        return toChoices(Rarity.getRarityNames().stream(), event);
    }

    @SlashAutocompleteHandler(id = "tooltip-side")
    public List<Command.Choice> tooltipSide(CommandAutoCompleteInteractionEvent event) {
        return toChoices(Arrays.stream(MinecraftTooltipGenerator.TooltipSide.values()).map(MinecraftTooltipGenerator.TooltipSide::name), event);
    }

    @SlashAutocompleteHandler(id = "overlay-colors")
    public List<Command.Choice> overlayColors(CommandAutoCompleteInteractionEvent event) {
        return toChoices(OverlayLoader.getInstance().getAllColorOptionNames().stream().sorted(), event);
    }

    private static final int MAX_AUTOCOMPLETE_CHOICES = 25;
    private static final int MAX_CHOICE_LENGTH = 100; // JDA's Command.Choice name/value limit

    /**
     * Filters the candidate strings by the focused option's current input (case-insensitively),
     * drops any that exceed JDA's {@value MAX_CHOICE_LENGTH}-character choice limit (which would
     * otherwise throw and abort the whole autocomplete response), caps the result at
     * {@value MAX_AUTOCOMPLETE_CHOICES}, and maps each to a {@link Command.Choice}. The candidate
     * stream's encounter order is preserved.
     */
    private static List<Command.Choice> toChoices(Stream<String> candidates, CommandAutoCompleteInteractionEvent event) {
        String userInput = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        return candidates
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(userInput))
            .filter(name -> name.length() <= MAX_CHOICE_LENGTH)
            .limit(MAX_AUTOCOMPLETE_CHOICES)
            .map(name -> new Command.Choice(name, name))
            .toList();
    }

    /**
     * Resolves a command's pack option to the {@link PackId} passed to the generator builders.
     *
     * @param pack The raw pack option value, may be null when the user omitted the option
     *
     * @return The resolved {@link PackId}, or null for vanilla rendering
     *
     * @throws GeneratorException If the input is not a valid pack ID or references a pack that is not loaded
     */
    private PackId resolvePackOption(String pack) {
        return SkyBlockNerdsBot.resourcePackService().resolvePackOption(pack);
    }

    /**
     * Applies the resolved pack to an item builder: the pack selection, the repository the bot
     * manages the pack in, and the animated-textures opt-in. Mirrors {@link #applyPackTheme} for
     * tooltips so every item render resolves against the same pack state. With no pack (vanilla)
     * and animation off this leaves the render byte-identical to the pre-pack path.
     *
     * @param builder  The item builder to configure
     * @param packId   The resolved pack, or null for vanilla rendering
     * @param animated Whether to render the pack's animated item textures as a GIF
     */
    private void applyItemPack(MinecraftItemGenerator.Builder builder, PackId packId, boolean animated) {
        applyItemPack(SkyBlockNerdsBot.resourcePackService(), builder, packId, animated);
    }

    /**
     * Overload taking the pack service explicitly, so the render path can be exercised against a
     * fixture pack instead of the bot's global one.
     */
    static void applyItemPack(ResourcePackService packService, MinecraftItemGenerator.Builder builder, PackId packId, boolean animated) {
        builder.withPack(packId)
            .withPackRepository(packService.packRepository())
            .withAnimatedTextures(animated);
    }

    /**
     * Builds the Discord attachment for a generated render, branching on
     * {@link GeneratedObject#isAnimated()}: animated renders upload the encoded GIF bytes as
     * {@code <baseName>.gif}, static renders upload the PNG as {@code <baseName>.png}. Shared by
     * every generator command so the GIF/PNG decision stays in one place.
     *
     * @param generatedObject The render to attach
     * @param baseName        The attachment file name without extension (e.g. {@code "item"})
     *
     * @return The {@link FileUpload} to send back to Discord
     *
     * @throws IOException If writing the static PNG to a temporary file fails
     */
    static FileUpload renderAttachment(GeneratedObject generatedObject, String baseName) throws IOException {
        if (generatedObject.isAnimated()) {
            return FileUpload.fromData(generatedObject.getGifData(), baseName + ".gif");
        }

        return FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), baseName + ".png");
    }

    /**
     * Rejects an explicit tooltip_style when the border is disabled: the library only draws
     * theme sprites when the border is rendered, so the style would silently do nothing
     * (while an invalid style would still error — loud beats inconsistent).
     *
     * @throws GeneratorException If a tooltip style is given while the border is disabled
     */
    private static void requireBorderForTooltipStyle(String tooltipStyle, boolean renderBorder) {
        if (tooltipStyle != null && !tooltipStyle.isBlank() && !renderBorder) {
            throw new GeneratorException("The tooltip_style option only renders with the border; set render_border: true too!");
        }
    }

    /**
     * Applies pack theming for tooltips that have no rarity and no tooltip_style option (plain
     * text, dialogue, hovered inventory lore, parsed NBT previews): the pack's default tooltip
     * override when present, plus the pack's text color remap.
     */
    private void applyPackTheme(MinecraftTooltipGenerator.Builder builder, PackId packId) {
        applyPackTheme(builder, packId, null, null);
    }

    private void applyPackTheme(MinecraftTooltipGenerator.Builder builder, PackId packId, String explicitStyle, Rarity rarity) {
        applyPackTheme(SkyBlockNerdsBot.resourcePackService(), builder, packId, explicitStyle, rarity);
    }

    /**
     * Overload taking the pack service explicitly; see
     * {@link #applyItemPack(ResourcePackService, MinecraftItemGenerator.Builder, PackId, boolean)}.
     */
    static void applyPackTheme(ResourcePackService packService, MinecraftTooltipGenerator.Builder builder, PackId packId, String explicitStyle, Rarity rarity) {
        boolean hasExplicitStyle = explicitStyle != null && !explicitStyle.isBlank();

        if (packId == null) {
            if (hasExplicitStyle) {
                throw new GeneratorException("The tooltip_style option needs a resource pack; set the pack option too!");
            }
            return;
        }

        // The repository is injected rather than left to the tooltip generator's global fallback,
        // so tooltips and item renders always resolve against the same pack state. These coincide
        // in production, where the service wraps the global repository, but not for a service
        // built over its own.
        builder.withPack(packId).withPackRepository(packService.packRepository());

        String style = hasExplicitStyle ? explicitStyle.trim() : packService.tooltipStyleFor(packId, rarity);
        if (style != null) {
            builder.withTooltipStyle(style);
        }

        TextColorRemap textColorRemap = packService.textColorRemapFor(packId);
        if (textColorRemap != null) {
            builder.withTextColorRemap(textColorRemap);
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
        DiscordUser discordUser = findDiscordUser(user);

        if (discordUser == null) {
            return;
        }

        if (discordUser.getGeneratorHistory() == null) {
            discordUser.setGeneratorHistory(new GeneratorHistory());
        }

        discordUser.getGeneratorHistory().addCommand(command);
    }

    /**
     * Finds the {@link DiscordUser} for the given {@link User}.
     *
     * @param user The {@link User} to look up
     *
     * @return The {@link DiscordUser}, or {@code null} if the repository is unavailable or the user is not in the database
     */
    @Nullable
    private static DiscordUser findDiscordUser(User user) {
        DiscordUserRepository discordUserRepository = DiscordBotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        if (discordUserRepository == null) {
            return null;
        }

        return discordUserRepository.findById(user.getId()).orElse(null);
    }

    /**
     * Gets the generator command history of the given {@link DiscordUser}.
     *
     * @param discordUser The {@link DiscordUser} to get the history of
     *
     * @return The list of commands, or an empty list if the user is {@code null} or has no history yet
     */
    static List<String> getCommandHistory(@Nullable DiscordUser discordUser) {
        if (discordUser == null || discordUser.getGeneratorHistory() == null) {
            return List.of();
        }

        return discordUser.getGeneratorHistory().getCommandHistory();
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
        double statMultiplier = stat.getPowerScalingMultiplier() != null ? stat.getPowerScalingMultiplier() : 1;
        double logValue = Math.log(1 + (0.0019 * magicalPower));
        double magnitude = Math.pow(Math.abs(logValue), 1.2);
        double signedFactor = Math.signum(logValue) * magnitude;
        return ((double) basePower / 100) * statMultiplier * 719.28 * signedFactor;
    }
}
