package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.data.PowerStrength;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.NbtParseException;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.spritesheet.OverlayLoader;
import net.aerh.imagegenerator.spritesheet.Spritesheet;
import net.aerh.imagegenerator.tools.DialogueTool;
import net.aerh.imagegenerator.tools.DisplayTool;
import net.aerh.imagegenerator.tools.InventoryTool;
import net.aerh.imagegenerator.tools.ItemTool;
import net.aerh.imagegenerator.tools.ParseRequest;
import net.aerh.imagegenerator.tools.ParseResult;
import net.aerh.imagegenerator.tools.ParseTool;
import net.aerh.imagegenerator.tools.PowerStoneRequest;
import net.aerh.imagegenerator.tools.PowerStoneTool;
import net.aerh.imagegenerator.tools.RecipeTool;
import net.aerh.imagegenerator.tools.SearchResult;
import net.aerh.imagegenerator.tools.SearchTool;
import net.aerh.imagegenerator.tools.TextTool;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.imagegenerator.tools.support.GlyphListing;
import net.aerh.imagegenerator.tools.support.NumberFormats;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.generation.DiscordGenerationContexts;
import net.hypixel.nerdbot.app.inject.ImageRendering;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    private static final String COLOR_DESCRIPTION = "Overlay color for vanilla items, or dye color for pack items (e.g. red, light_blue, #FF0000)";
    private static final String ITEM_MODEL_DESCRIPTION = "The minecraft:item_model ref to render (e.g. hypixel_skyblock:item/jacob/cactus_knife)";
    private static final String PACK_DESCRIPTION = "The resource pack used to resolve item textures";
    private static final String TOOLTIP_STYLE_DESCRIPTION = "The pack tooltip style to render with (defaults to the rarity's configured style)";

    static final String WRONG_CHANNEL_MESSAGE = "Generator commands can only be used in image generator channels.";

    // Without the member's preference, err on the side of not posting their image publicly
    private static final boolean HIDDEN_WHEN_PREFERENCE_UNKNOWN = true;

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int SEARCH_RESULT_LIMIT = 10;
    private static final String SEARCH_TRUNCATION_MARKER = " - ...\n";
    private static final int SYMBOLS_PER_PAGE = 20;
    private static final String DISCORD_SYMBOLS_HEADER = "Use `%%name%%` in generator text. Pack glyphs are shown as codepoints because"
        + " Discord cannot display them; generated images can.";
    private static final int MAX_AUTOCOMPLETE_CHOICES = 25;
    private static final int MAX_CHOICE_LENGTH = 100;

    static final int MAX_NBT_FILE_BYTES = 64 * 1024;
    private static final Set<String> NBT_FILE_EXTENSIONS = Set.of(".txt", ".json", ".snbt", ".nbt");
    private static final long NBT_DOWNLOAD_TIMEOUT_SECONDS = 30;

    /** One generator run. Runs on the rendering pool and builds the reply that replaces the deferred one. */
    @FunctionalInterface
    interface Render {
        MessageEditData render(GenerationContext context) throws IOException;
    }

    @FunctionalInterface
    interface ImageRender {
        GeneratedObject render(GenerationContext context) throws IOException;
    }

    /** An NBT file a member uploaded, kept apart from JDA's attachment so the checks can be tested. */
    record NbtUpload(String fileName, int size, Supplier<CompletableFuture<InputStream>> download) {

        static @Nullable NbtUpload of(@Nullable Message.Attachment attachment) {
            return attachment == null ? null
                : new NbtUpload(attachment.getFileName(), attachment.getSize(), () -> attachment.getProxy().download());
        }
    }

    private record Started(InteractionHook hook, boolean hidden, Optional<DiscordUser> user) {
    }

    private final ResourcePackService packs;
    private final Provider<NerdBotConfig> config;
    private final DiscordUserStore users;
    private final DiscordGenerationContexts contexts;
    private final Executor rendering;

    @Inject
    public GeneratorCommands(ResourcePackService packs, Provider<NerdBotConfig> config, DiscordUserStore users,
                             DiscordGenerationContexts contexts, @ImageRendering Executor rendering) {
        this.packs = packs;
        this.config = config;
        this.users = users;
        this.contexts = contexts;
        this.rendering = rendering;
    }

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
        generateImage(event, hidden, "item", "An error occurred while generating that item!", context -> new DisplayTool(packs).render(
            GeneratorRequests.display(itemId, itemModel, data, color, enchanted, hoverEffect, skinValue, durability, packs.resolvePackOption(pack)),
            context));
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
        generateImage(event, hidden, "powerstone", "An error occurred while generating that Power Stone!", context -> {
            PowerStoneRequest request = GeneratorRequests.powerStone(powerName, powerStrength, magicalPower, scalingStats, uniqueBonus,
                itemId, color, skinValue, alpha, padding, selected, enchanted, packs.resolvePackOption(pack));
            PowerStoneTool tool = new PowerStoneTool(packs);

            if (Boolean.TRUE.equals(includeGenCommand)) {
                event.getHook().sendMessage("Your Power Stone has been parsed into a slash command:\n```" + tool.slashCommand(request).trim() + "```").queue();
            }

            return tool.render(request, context);
        });
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "search", description = "Search for an item", guildOnly = true)
    public void searchItem(SlashCommandInteractionEvent event, @SlashOption(description = "The ID of the item to search for") String itemId, @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden) {
        answer(event, hidden, "An error occurred while searching for that item!", context -> new MessageEditBuilder()
            .setContent(searchReply(itemId, new SearchTool(packs).search(itemId)))
            .build(), user -> {
        });
    }

    static String searchReply(String query, SearchResult result) {
        if (result.isEmpty()) {
            return "No results found for that item!";
        }

        StringBuilder message = new StringBuilder();
        appendSearchResults(message, "Top results for `" + query + "`", result.spritesheetResults());
        appendSearchResults(message, "Resource pack results for `" + query + "`", result.packResults());
        return message.toString();
    }

    /**
     * Called once per result source onto one shared message, so every append is checked against
     * Discord's message limit: a block whose header won't fit is skipped, and lines stop with a
     * truncation marker before they would overflow.
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

    @SlashCommand(name = BASE_COMMAND, subcommand = "symbols", description = "List the placeholder names usable in generator text", guildOnly = true)
    public void listSymbols(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Only show names containing this text", required = false) String filter,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        Optional<Started> started = start(event, hidden);
        if (started.isEmpty()) {
            return;
        }

        InteractionHook hook = started.get().hook();
        List<String> rows = GlyphListing.buildRows(filter);

        if (rows.isEmpty()) {
            hook.editOriginal("No placeholders match `" + filter + "`!").queue();
            return;
        }

        PaginatedResponse<String> pagination = PaginatedResponse.forText(rows, SYMBOLS_PER_PAGE,
            page -> GlyphListing.buildPage(DISCORD_SYMBOLS_HEADER, page), "gen-symbols-page");
        pagination.sendMessage(event);
        hook.retrieveOriginal().queue(message -> PaginationManager.registerPagination(message.getId(), pagination));
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
        generateImage(event, hidden, "recipe", "An error occurred while generating that recipe!", context -> new RecipeTool(packs).render(
            GeneratorRequests.recipe(recipe, renderBackground, packs.resolvePackOption(pack)), context));
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
        generateImage(event, hidden, "inventory", "An error occurred while generating that inventory!", context -> {
            boolean animateGlint = config.get().getGeneratorConfig().getInventory().isAnimateGlint();
            return new InventoryTool(packs).render(GeneratorRequests.inventory(rows, slotsPerRow, inventoryString, hoveredItemString,
                containerName, drawBorder, maxLineLength, animateGlint, packs.resolvePackOption(pack)), context);
        });
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "parse", description = "Parse an NBT string (JSON or SNBT format)", guildOnly = true)
    public void parseNbtString(
        SlashCommandInteractionEvent event,
        @SlashOption(description = NBT_DESCRIPTION, required = false) String nbt,
        @SlashOption(description = "Upload a text file containing NBT data", required = false) Message.Attachment attachment,
        @SlashOption(autocompleteId = "pack-ids", description = PACK_DESCRIPTION, required = false) String pack,
        @SlashOption(description = HIDDEN_OUTPUT_DESCRIPTION, required = false) Boolean hidden
    ) {
        NbtUpload upload = NbtUpload.of(attachment);

        generate(event, hidden, "An error occurred while parsing the NBT!", context -> {
            String input = nbtInput(nbt, upload);
            ParseResult result = new ParseTool(packs).render(new ParseRequest(input, packs.resolvePackOption(pack)), context);
            return parsedReply(result, upload == null ? "text input" : "attachment");
        });
    }

    /** An uploaded file wins over the text option. */
    static String nbtInput(@Nullable String text, @Nullable NbtUpload upload) throws IOException {
        if (upload == null) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Please provide NBT data either as text or by uploading a file.");
            }
            return text;
        }

        if (upload.size() > MAX_NBT_FILE_BYTES) {
            throw new IllegalArgumentException("Attachment is too large! Maximum size is " + NumberFormats.formatSize(MAX_NBT_FILE_BYTES));
        }

        String fileName = upload.fileName().toLowerCase(Locale.ROOT);
        if (NBT_FILE_EXTENSIONS.stream().noneMatch(fileName::endsWith)) {
            throw new IllegalArgumentException("Unsupported file type! Please upload a `.txt`, `.json`, `.snbt`, or `.nbt` file.");
        }

        String content = download(upload);
        if (content.isBlank()) {
            throw new IllegalArgumentException("The uploaded file is empty!");
        }

        return content;
    }

    private static String download(NbtUpload upload) throws IOException {
        try (InputStream input = upload.download().get().get(NBT_DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Couldn't download " + upload.fileName(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + upload.fileName(), exception);
        }
    }

    private static MessageEditData parsedReply(ParseResult result, String sourceLabel) throws IOException {
        String content = "Your NBT " + sourceLabel + " has been parsed into a slash command:\n```\n" + result.slashCommand() + "```";

        if (result.fallbackNotice() != null) {
            content += "\n-# " + result.fallbackNotice();
        }

        return new MessageEditBuilder().setContent(content).setFiles(renderAttachment(result.image(), "parsed_nbt")).build();
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
        generateImage(event, hidden, "item", "An error occurred while generating that item!", context -> new ItemTool(packs).render(
            GeneratorRequests.item(itemName, itemLore, type, rarity, itemId, itemModel, color, skinValue, recipe, alpha, padding, enchanted,
                centered, firstLinePadding, maxLineLength, tooltipSide, renderBorder, durability, packs.resolvePackOption(pack), tooltipStyle),
            context));
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
        generateImage(event, hidden, "text", "An error occurred while generating the text!", context -> new TextTool(packs).render(
            GeneratorRequests.text(text, centered, alpha, padding, maxLineLength, renderBorder, packs.resolvePackOption(pack), tooltipStyle),
            context));
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
        generateImage(event, hidden, "dialogue", "An error occurred while generating the dialogue!", context -> new DialogueTool(packs).render(
            GeneratorRequests.dialogueSingle(npcName, dialogue, maxLineLength, abiphone, skinValue, renderBackground, packs.resolvePackOption(pack)),
            context));
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
        generateImage(event, hidden, "dialogue", "An error occurred while generating the dialogue!", context -> new DialogueTool(packs).render(
            GeneratorRequests.dialogueMulti(npcNames, dialogue, maxLineLength, abiphone, skinValue, renderBackground, packs.resolvePackOption(pack)),
            context));
    }

    @SlashCommand(name = BASE_COMMAND, subcommand = "history", description = "View your command history", guildOnly = true)
    public void viewHistory(SlashCommandInteractionEvent event) {
        showHistory(event);
    }

    void showHistory(SlashCommandInteraction event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        List<String> history = userRecord(event.getUser().getId()).map(GeneratorCommands::commandHistory).orElse(List.of());

        if (history.isEmpty()) {
            hook.editOriginal("No history found").queue();
            return;
        }

        byte[] file = String.join("\n\n", history).getBytes(StandardCharsets.UTF_8);
        hook.editOriginalAttachments(FileUpload.fromData(file, "generator_history.txt")).queue();
    }

    static List<String> commandHistory(DiscordUser user) {
        GeneratorHistory history = user.getGeneratorHistory();
        return history == null ? List.of() : List.copyOf(history.getCommandHistory());
    }

    @SlashAutocompleteHandler(id = "power-strengths")
    public List<Command.Choice> powerStrengths(CommandAutoCompleteInteractionEvent event) {
        return toChoices(PowerStrength.getPowerStrengthNames().stream(), event.getFocusedOption().getValue());
    }

    @SlashAutocompleteHandler(id = "item-names")
    public List<Command.Choice> itemNames(CommandAutoCompleteInteractionEvent event) {
        Stream<String> packRefs = packs.itemRefsForOption(packOption(event)).stream();
        return toChoices(Stream.concat(Spritesheet.getImageMap().keySet().stream(), packRefs), event.getFocusedOption().getValue());
    }

    @SlashAutocompleteHandler(id = "pack-ids")
    public List<Command.Choice> packIds(CommandAutoCompleteInteractionEvent event) {
        return toChoices(packs.packOptionChoices().stream(), event.getFocusedOption().getValue());
    }

    @SlashAutocompleteHandler(id = "tooltip-styles")
    public List<Command.Choice> tooltipStyles(CommandAutoCompleteInteractionEvent event) {
        return toChoices(packs.tooltipStyleChoices(packOption(event)).stream(), event.getFocusedOption().getValue());
    }

    @SlashAutocompleteHandler(id = "item-rarities")
    public List<Command.Choice> itemRarities(CommandAutoCompleteInteractionEvent event) {
        return toChoices(Rarity.getRarityNames().stream(), event.getFocusedOption().getValue());
    }

    @SlashAutocompleteHandler(id = "tooltip-side")
    public List<Command.Choice> tooltipSide(CommandAutoCompleteInteractionEvent event) {
        return toChoices(Arrays.stream(MinecraftTooltipGenerator.TooltipSide.values()).map(MinecraftTooltipGenerator.TooltipSide::name),
            event.getFocusedOption().getValue());
    }

    @SlashAutocompleteHandler(id = "overlay-colors")
    public List<Command.Choice> overlayColors(CommandAutoCompleteInteractionEvent event) {
        return toChoices(OverlayLoader.getInstance().getAllColorOptionNames().stream().sorted(), event.getFocusedOption().getValue());
    }

    private static @Nullable String packOption(CommandAutoCompleteInteractionEvent event) {
        OptionMapping option = event.getOption("pack");
        return option == null ? null : option.getAsString();
    }

    /**
     * Keeps the candidates containing the typed text, in their original order. Names over Discord's
     * choice length are dropped, because one of them would make JDA reject the whole response.
     */
    static List<Command.Choice> toChoices(Stream<String> candidates, String typed) {
        String input = typed.toLowerCase(Locale.ROOT);

        return candidates
            .filter(name -> name.toLowerCase(Locale.ROOT).contains(input))
            .filter(name -> name.length() <= MAX_CHOICE_LENGTH)
            .limit(MAX_AUTOCOMPLETE_CHOICES)
            .map(name -> new Command.Choice(name, name))
            .toList();
    }

    static FileUpload renderAttachment(GeneratedObject generated, String baseName) throws IOException {
        if (generated.isAnimated()) {
            return FileUpload.fromData(generated.getGifData(), baseName + ".gif");
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(generated.getImage(), "png", png)) {
            throw new IOException("No PNG writer is available");
        }

        return FileUpload.fromData(png.toByteArray(), baseName + ".png");
    }

    private void generateImage(SlashCommandInteraction event, @Nullable Boolean hidden, String fileName, String failureMessage, ImageRender render) {
        generate(event, hidden, failureMessage, context -> new MessageEditBuilder()
            .setFiles(renderAttachment(render.render(context), fileName))
            .build());
    }

    /** As {@link #answer}, and a successful command is added to the member's generator history. */
    CompletableFuture<Void> generate(SlashCommandInteraction event, @Nullable Boolean hidden, String failureMessage, Render render) {
        return answer(event, hidden, failureMessage, render, user -> addToHistory(user, event.getCommandString()));
    }

    /**
     * Defers the reply, renders on the rendering pool and replaces the deferred reply with the
     * result. Input the generator rejects is shown to the member; anything else gets
     * {@code failureMessage}.
     *
     * @return completes once the reply has been sent, for tests
     */
    CompletableFuture<Void> answer(SlashCommandInteraction event, @Nullable Boolean hidden, String failureMessage, Render render,
                                   Consumer<DiscordUser> afterSuccess) {
        Optional<Started> started = start(event, hidden);
        if (started.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        InteractionHook hook = started.get().hook();
        GenerationContext context = contexts.create(event, started.get().hidden());

        return CompletableFuture.supplyAsync(() -> renderUnchecked(render, context), rendering)
            .thenAccept(reply -> {
                // Discord can still refuse the edit, for example an image over the upload limit
                hook.editOriginal(reply).queue(null, failure -> CommandReplies.fail(hook, failureMessage, failure));
                started.get().user().ifPresent(afterSuccess);
            })
            .exceptionally(error -> {
                reportFailure(hook, failureMessage, unwrap(error));
                return null;
            });
    }

    private Optional<Started> start(SlashCommandInteraction event, @Nullable Boolean hidden) {
        if (!inGeneratorChannel(event.getChannelId())) {
            event.reply(WRONG_CHANNEL_MESSAGE).setEphemeral(true).queue();
            log.warn("Blocked generator command '{}' from user {} in channel {}", event.getCommandString(), event.getUser().getId(), event.getChannelId());
            return Optional.empty();
        }

        Optional<DiscordUser> user = userRecord(event.getUser().getId());
        boolean hide = hidden != null ? hidden : user.map(DiscordUser::isAutoHideGenCommands).orElse(HIDDEN_WHEN_PREFERENCE_UNKNOWN);
        event.deferReply(hide).queue();

        return Optional.of(new Started(event.getHook(), hide, user));
    }

    private boolean inGeneratorChannel(String channelId) {
        String[] allowed = config.get().getChannelConfig().getGenChannelIds();
        return allowed == null || allowed.length == 0 || Arrays.asList(allowed).contains(channelId);
    }

    /** The generator works without a record, but every member should have one, so a missing one is logged as the bug it is. */
    private Optional<DiscordUser> userRecord(String discordId) {
        try {
            Optional<DiscordUser> user = users.findById(discordId);
            if (user.isEmpty()) {
                log.error("Member {} has no user record, so their generator preference and history are skipped", discordId);
            }
            return user;
        } catch (RuntimeException exception) {
            log.error("Couldn't look up the user record of {}, so their generator preference and history are skipped", discordId, exception);
            return Optional.empty();
        }
    }

    private void addToHistory(DiscordUser user, String command) {
        // Two renders for the same member can finish at the same time
        synchronized (user) {
            if (user.getGeneratorHistory() == null) {
                user.setGeneratorHistory(new GeneratorHistory());
            }
            user.getGeneratorHistory().addCommand(command);
        }
        users.cache(user);
    }

    private static MessageEditData renderUnchecked(Render render, GenerationContext context) {
        try {
            return render.render(context);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    static void reportFailure(InteractionHook hook, String failureMessage, Throwable error) {
        String message = error.getMessage();

        if (isRejectedInput(error) && message != null && !message.isBlank()) {
            log.debug("The generator rejected the input: {}", message);
            hook.editOriginal(message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message).queue();
        } else {
            CommandReplies.fail(hook, failureMessage, error);
        }
    }

    private static boolean isRejectedInput(Throwable error) {
        return error instanceof GeneratorException || error instanceof NbtParseException || error instanceof IllegalArgumentException;
    }
}
