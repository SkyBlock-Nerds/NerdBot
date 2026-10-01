package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.data.PowerStrength;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.NbtParseException;
import net.aerh.imagegenerator.exception.TooManyTexturesException;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.spritesheet.OverlayLoader;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import net.aerh.imagegenerator.tools.DialogueRequest;
import net.aerh.imagegenerator.tools.DialogueTool;
import net.aerh.imagegenerator.tools.DisplayRequest;
import net.aerh.imagegenerator.tools.DisplayTool;
import net.aerh.imagegenerator.tools.InventoryRequest;
import net.aerh.imagegenerator.tools.InventoryTool;
import net.aerh.imagegenerator.tools.ItemRequest;
import net.aerh.imagegenerator.tools.ItemTool;
import net.aerh.imagegenerator.tools.ParseRequest;
import net.aerh.imagegenerator.tools.ParseResult;
import net.aerh.imagegenerator.tools.ParseTool;
import net.aerh.imagegenerator.tools.PowerStoneRequest;
import net.aerh.imagegenerator.tools.PowerStoneTool;
import net.aerh.imagegenerator.tools.RecipeRequest;
import net.aerh.imagegenerator.tools.RecipeTool;
import net.aerh.imagegenerator.tools.SearchResult;
import net.aerh.imagegenerator.tools.SearchTool;
import net.aerh.imagegenerator.tools.TextRequest;
import net.aerh.imagegenerator.tools.TextTool;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.GlyphListing;
import net.aerh.imagegenerator.tools.support.NumberFormats;
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
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
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
import java.util.List;
import java.util.Locale;
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
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        try {
            DisplayRequest request = GeneratorRequests.display(itemId, itemModel, data, color, enchanted, hoverEffect, skinValue, durability,
                packService().resolvePackOption(pack));
            GeneratedObject generatedObject = new DisplayTool(packService()).render(request, context);

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
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        try {
            PowerStoneRequest request = GeneratorRequests.powerStone(powerName, powerStrength, magicalPower, scalingStats, uniqueBonus,
                itemId, color, skinValue, alpha, padding, selected, enchanted, packService().resolvePackOption(pack));
            PowerStoneTool tool = new PowerStoneTool(packService());

            if (includeGenCommand != null && includeGenCommand) {
                String slashCommand = tool.slashCommand(request);
                event.getHook().sendMessage("Your Power Stone has been parsed into a slash command:\n```" + slashCommand.trim() + "```").queue();
            }

            GeneratedObject generatedObject = tool.render(request, context);

            event.getHook().editOriginalAttachments(renderAttachment(generatedObject, "powerstone")).queue();

            addCommandToUserHistory(event.getUser(), event.getCommandString());
        } catch (GeneratorException | IllegalArgumentException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("Encountered an error while generating a Power Stone", exception);
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that Power Stone!").queue();
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

        SearchResult result = new SearchTool(packService()).search(itemId);

        if (result.isEmpty()) {
            event.getHook().editOriginal("No results found for that item!").queue();
            return;
        }

        StringBuilder message = new StringBuilder();
        appendSearchResults(message, "Top results for `" + itemId + "`", result.spritesheetResults());
        appendSearchResults(message, "Resource pack results for `" + itemId + "`", result.packResults());

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
    static void appendSearchResults(StringBuilder message, String header, List<String> results) {
        if (results.isEmpty()) {
            return;
        }

        String headerLine = header + " (" + NumberFormats.COMMA_SEPARATED.format(results.size()) + " total):\n";

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
    private static final String DISCORD_SYMBOLS_HEADER = "Use `%%name%%` in generator text. Pack glyphs are shown as codepoints because"
        + " Discord cannot display them; generated images can.";

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

        PaginatedResponse<String> pagination = PaginatedResponse.forText(rows, SYMBOLS_PER_PAGE,
            page -> GlyphListing.buildPage(DISCORD_SYMBOLS_HEADER, page), "gen-symbols-page");
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

        try {
            RecipeRequest request = GeneratorRequests.recipe(recipe, renderBackground, packService().resolvePackOption(pack));
            GeneratedObject generatedObject = new RecipeTool(packService()).render(request, context);

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

        boolean animateGlint = SkyBlockNerdsBot.config().getGeneratorConfig().getInventory().isAnimateGlint();

        try {
            InventoryRequest request = GeneratorRequests.inventory(rows, slotsPerRow, inventoryString, hoveredItemString, containerName,
                drawBorder, maxLineLength, animateGlint, packService().resolvePackOption(pack));
            GeneratedObject finalObject = new InventoryTool(packService()).render(request, context);

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
            ParseResult result = new ParseTool(packService()).render(new ParseRequest(nbtInput, packService().resolvePackOption(pack)), context);

            String sourceLabel = attachment != null ? "attachment" : "text input";
            String content = "Your NBT " + sourceLabel + " has been parsed into a slash command:"
                + System.lineSeparator() + "```" + System.lineSeparator() + result.slashCommand() + "```";

            if (result.fallbackNotice() != null) {
                content += System.lineSeparator() + "-# " + result.fallbackNotice();
            }

            MessageEditBuilder builder = new MessageEditBuilder().setContent(content);
            builder.setFiles(renderAttachment(result.image(), "parsed_nbt"));

            event.getHook().editOriginal(builder.build()).queue();
            addCommandToUserHistory(event.getUser(), event.getCommandString());
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
                throw new IllegalArgumentException("Attachment is too large! Maximum size is " + NumberFormats.formatSize(MAX_ATTACHMENT_SIZE_BYTES));
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
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        if (shouldBlockGeneratorCommand(event)) {
            return;
        }

        hidden = hidden == null ? getUserAutoHideSetting(event) : hidden;

        event.deferReply(hidden).complete();

        GenerationContext context = DiscordGenerationContext.fromEvent(event, hidden);

        try {
            ItemRequest request = GeneratorRequests.item(itemName, itemLore, type, rarity, itemId, itemModel, color, skinValue, recipe,
                alpha, padding, enchanted, centered, firstLinePadding, maxLineLength, tooltipSide, renderBorder, durability,
                packService().resolvePackOption(pack), tooltipStyle);
            GeneratedObject generatedObject = new ItemTool(packService()).render(request, context);

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

        try {
            TextRequest request = GeneratorRequests.text(text, centered, alpha, padding, maxLineLength, renderBorder,
                packService().resolvePackOption(pack), tooltipStyle);
            GeneratedObject generatedObject = new TextTool(packService()).render(request, context);

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

        try {
            DialogueRequest request = GeneratorRequests.dialogueSingle(npcName, dialogue, maxLineLength, abiphone, skinValue, renderBackground,
                packService().resolvePackOption(pack));
            GeneratedObject generatedObject = new DialogueTool(packService()).render(request, context);

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

        try {
            DialogueRequest request = GeneratorRequests.dialogueMulti(npcNames, dialogue, maxLineLength, abiphone, skinValue, renderBackground,
                packService().resolvePackOption(pack));
            GeneratedObject generatedObject = new DialogueTool(packService()).render(request, context);

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
        OptionMapping packOption = event.getOption("pack");
        Stream<String> packRefs = packService().itemRefsForOption(packOption == null ? null : packOption.getAsString()).stream();

        return toChoices(Stream.concat(Spritesheet.getImageMap().keySet().stream(), packRefs), event);
    }

    @SlashAutocompleteHandler(id = "pack-ids")
    public List<Command.Choice> packIds(CommandAutoCompleteInteractionEvent event) {
        return toChoices(packService().packOptionChoices().stream(), event);
    }

    @SlashAutocompleteHandler(id = "tooltip-styles")
    public List<Command.Choice> tooltipStyles(CommandAutoCompleteInteractionEvent event) {
        OptionMapping packOption = event.getOption("pack");
        return toChoices(packService()
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

    private static ResourcePackService packService() {
        return SkyBlockNerdsBot.resourcePackService();
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
}
