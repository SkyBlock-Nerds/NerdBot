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
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.generator.GeneratorBuilder;
import net.hypixel.nerdbot.generator.ImageMerger;
import net.hypixel.nerdbot.generator.parser.StringColorParser;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.Flavor;
import net.hypixel.nerdbot.util.skyblock.Icon;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.hypixel.nerdbot.generator.util.GeneratorStrings.COMMAND_PREFIX;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_ALPHA;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_CENTERED;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_DISABLE_RARITY_LINEBREAK;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_EXTRA_ITEM_MODIFIERS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_HIDDEN;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_INCLUDE_ITEM;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_ITEM_ID;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_ITEM_LORE;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_ITEM_NAME;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_MAX_LINE_LENGTH;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_PADDING;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_PARSE_ITEM;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_RARITY;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_RECIPE;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_RENDER_INVENTORY;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_TEXT;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DESC_TYPE;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DISPLAY_INFO_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DISPLAY_INFO_BASIC;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DISPLAY_INFO_ENCHANT_GLINT;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DISPLAY_INFO_EXTRA_MODIFIERS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DISPLAY_INFO_MODIFIERS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DISPLAY_INFO_OPTIONAL_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.DISPLAY_ITEM_INFO_PLAYER_HEAD;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.FULL_GEN_INFO;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.GENERAL_HELP;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.GENERAL_INFO;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.INVALID_BASE_64_SKIN_URL;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.INVALID_ITEM_SKULL_DATA;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_BASIC_INFO;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_COLOR_CODES;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_EXAMPLES;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_INFO_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_INFO_OPTIONAL_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_OTHER_INFO;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_PARSE_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_PARSE_COMMAND;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_PARSE_INFO;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_PARSE_JSON_FORMAT;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_PARSE_OPTIONAL_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_TEXT_BASIC_INFO;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_TEXT_INFO_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.ITEM_TEXT_INFO_OPTIONAL_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.MISSING_FULL_GEN_ITEM;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.MISSING_ITEM_NBT;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.MULTIPLE_ITEM_SKULL_DATA;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.RECIPE_INFO_ARGUMENTS;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.RECIPE_INFO_BASIC;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.RECIPE_INFO_EXAMPLES;
import static net.hypixel.nerdbot.generator.util.GeneratorStrings.stripString;

@Log4j2
public class GeneratorCommands extends ApplicationCommand {
    private static final Color[] EMBED_COLORS = new Color[]{
        new Color(167, 65, 92),
        new Color(26, 107, 124),
        new Color(137, 222, 74),
        new Color(151, 150, 164)
    };
    private final GeneratorBuilder builder;

    public GeneratorCommands() {
        super();
        this.builder = new GeneratorBuilder();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "item", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock")
    public void generateItem(GuildSlashEvent event,
                             @AppOption(description = DESC_ITEM_NAME) String itemName,
                             @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                             @AppOption(description = DESC_ITEM_LORE) String itemLore,
                             @Optional @AppOption(description = DESC_TYPE) String type,
                             @Optional @AppOption(description = DESC_DISABLE_RARITY_LINEBREAK) Boolean disableRarityLinebreak,
                             @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                             @Optional @AppOption(description = DESC_PADDING) Integer padding,
                             @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();
        // building the item's description
        BufferedImage generatedImage = builder.buildItem(event, itemName, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true, false);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }

        // Log item gen activity
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "text", description = "Creates an image that looks like a message from Minecraft, primarily used for Hypixel Skyblock")
    public void generateText(GuildSlashEvent event,
                             @AppOption(description = DESC_TEXT) String message,
                             @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                             @Optional @AppOption(description = DESC_CENTERED) Boolean centered,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        centered = (centered != null && centered);
        alpha = alpha == null ? 128 : Math.max(0, Math.min(alpha, 255));
        event.deferReply(hidden).complete();
        // building the chat message
        BufferedImage generatedImage = builder.buildItem(event, "NONE", "NONE", message, "", true, alpha, 1, StringColorParser.MAX_FINAL_LINE_LENGTH, false, centered);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }

        // Log item gen activity
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "display", description = "Draws a Minecraft item into a file")
    public void generateItemImage(GuildSlashEvent event,
                                  @AppOption(description = DESC_ITEM_ID, name = "item_id") String itemID,
                                  @Optional @AppOption(description = DESC_EXTRA_ITEM_MODIFIERS) String extraModifiers,
                                  @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();

        BufferedImage item = builder.buildUnspecifiedItem(event, itemID, extraModifiers, true);
        if (item != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(item))).setEphemeral(hidden).queue();
        }

        // Log item gen activity
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "full", description = "Creates an image that looks like an item from Minecraft, complete with lore and a display item.")
    public void generateFullItem(GuildSlashEvent event,
                                 @AppOption(description = DESC_ITEM_NAME) String itemName,
                                 @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                                 @AppOption(description = DESC_ITEM_LORE) String itemLore,
                                 @Optional @AppOption(description = DESC_TYPE) String type,
                                 @Optional @AppOption(description = DESC_DISABLE_RARITY_LINEBREAK) Boolean disableRarityLinebreak,
                                 @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                                 @Optional @AppOption(description = DESC_PADDING) Integer padding,
                                 @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                                 @Optional @AppOption(description = DESC_ITEM_ID) String itemId,
                                 @Optional @AppOption(description = DESC_EXTRA_ITEM_MODIFIERS) String extraModifiers,
                                 @Optional @AppOption(description = DESC_RECIPE) String recipe,
                                 @Optional @AppOption(description = DESC_RENDER_INVENTORY) Boolean renderBackground,
                                 @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();

        // checking that there are two or more different items to merge the images
        if ((itemName == null || rarity == null || itemLore == null) && itemId == null && recipe == null) {
            event.getHook().sendMessage(MISSING_FULL_GEN_ITEM).queue();
            return;
        }

        extraModifiers = Objects.requireNonNullElse(extraModifiers, "");
        renderBackground = Objects.requireNonNullElse(renderBackground, true);

        // building the description for the item
        BufferedImage generatedDescription = null;
        if (itemName != null && rarity != null && itemLore != null) {
            generatedDescription = builder.buildItem(event, itemName, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true, false);
            if (generatedDescription == null) {
                return;
            }
        }

        // building the item for the which is beside the description
        BufferedImage generatedItem = null;
        if (itemId != null) {
            generatedItem = builder.buildUnspecifiedItem(event, itemId, extraModifiers, true);
            if (generatedItem == null) {
                return;
            }
        }

        // building the recipe for the item
        BufferedImage generatedRecipe = null;
        if (recipe != null) {
            generatedRecipe = builder.buildRecipe(event, recipe, renderBackground);
            if (generatedRecipe == null) {
                return;
            }
        }

        ImageMerger merger = new ImageMerger(generatedDescription, generatedItem, generatedRecipe);
        merger.drawFinalImage();
        event.getHook().sendFiles(FileUpload.fromData(Util.toFile(merger.getImage()))).setEphemeral(hidden).queue();

        // Log item gen activity
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "recipe", description = "Generates a Minecraft Recipe Image")
    public void generateRecipe(GuildSlashEvent event,
                               @AppOption(description = DESC_RECIPE) String recipe,
                               @Optional @AppOption(description = DESC_RENDER_INVENTORY) Boolean renderBackground,
                               @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();

        renderBackground = (renderBackground == null || renderBackground);

        // building the Minecraft recipe
        BufferedImage generatedRecipe = builder.buildRecipe(event, recipe, renderBackground);
        if (generatedRecipe != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedRecipe))).queue();
        }

        // Log item gen activity
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "parse", description = "Converts a minecraft item into a Nerd Bot item!")
    public void parseItemDescription(GuildSlashEvent event,
                                     @AppOption(description = DESC_PARSE_ITEM, name = "item_nbt") String itemNBT,
                                     @Optional @AppOption(description = DESC_INCLUDE_ITEM) Boolean includeItem,
                                     @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden
    ) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }

        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();
        includeItem = Objects.requireNonNullElse(includeItem, false);

        // converting the nbt into json
        JsonObject itemJSON;
        try {
            itemJSON = NerdBotApp.GSON.fromJson(itemNBT, JsonObject.class);
        } catch (JsonSyntaxException exception) {
            event.getHook().sendMessage(ITEM_PARSE_JSON_FORMAT).queue();
            return;
        }

        // checking if the user has copied the text directly from in game
        JsonObject tagJSON = JsonUtil.isJsonObject(itemJSON, "tag");
        if (tagJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("tag")).queue();
            return;
        }

        // checking if there is a display tag
        JsonObject displayJSON = JsonUtil.isJsonObject(tagJSON, "display");
        if (displayJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("display")).queue();
            return;
        }
        // checking that there is a name and lore parameters in the JsonObject
        String itemName = JsonUtil.isJsonString(displayJSON, "Name");
        JsonArray itemLoreArray = JsonUtil.isJsonArray(displayJSON, "Lore");
        if (itemName == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Name")).queue();
            return;
        } else if (itemLoreArray == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Lore")).queue();
            return;
        }
        itemName = itemName.replaceAll("§", "&");

        String itemID = "";
        String extraModifiers = "";
        // checking if the user wants to create full gen
        if (includeItem) {
            itemID = JsonUtil.isJsonString(itemJSON, "id");
            if (itemID == null) {
                event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("id")).queue();
                return;
            }
            itemID = itemID.replace("minecraft:", "");

            if (itemID.equals("skull")) {
                // checking if there is a SkullOwner json object within the main tag json
                JsonObject skullOwnerJSON = JsonUtil.isJsonObject(tagJSON, "SkullOwner");
                if (skullOwnerJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("SkullOwner")).queue();
                    return;
                }
                // checking if there is a Properties json object within SkullOwner
                JsonObject propertiesJSON = JsonUtil.isJsonObject(skullOwnerJSON, "Properties");
                if (propertiesJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Properties")).queue();
                    return;
                }
                // checking if there is a textures json object within properties
                JsonArray texturesJSON = JsonUtil.isJsonArray(propertiesJSON, "textures");
                if (texturesJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("textures")).queue();
                    return;
                }
                // checking that there is only one json object in the array
                if (texturesJSON.size() != 1) {
                    event.getHook().sendMessage(MULTIPLE_ITEM_SKULL_DATA).queue();
                    return;
                } else if (!texturesJSON.get(0).isJsonObject()) {
                    event.getHook().sendMessage(INVALID_ITEM_SKULL_DATA).queue();
                    return;
                }
                // checking that there is a Base64 skin url string
                String base64String = JsonUtil.isJsonString(texturesJSON.get(0).getAsJsonObject(), "Value");
                if (base64String == null) {
                    event.getHook().sendMessage(INVALID_ITEM_SKULL_DATA).queue();
                    return;
                }
                // converting the Base64 string into the Skin URL
                try {
                    extraModifiers = builder.base64ToSkinURL(base64String);
                } catch (NullPointerException | IllegalArgumentException exception) {
                    event.getHook().sendMessage(INVALID_BASE_64_SKIN_URL).queue();
                    return;
                }
            } else {
                // checking if there is a color attribute present and adding it to the extra attributes
                String color = JsonUtil.isJsonString(displayJSON, "color");
                if (color != null) {
                    try {
                        Integer selectedColor = Integer.decode(color);
                        extraModifiers = String.valueOf(selectedColor);
                    } catch (NumberFormatException ignored) {
                    }
                }

                // checking if the item is enchanted and applying the enchantment glint to the extra modifiers
                JsonArray enchantJson = JsonUtil.isJsonArray(tagJSON, "ench");
                if (enchantJson != null) {
                    extraModifiers = extraModifiers.isEmpty() ? "enchant" : extraModifiers + ",enchant";
                }
            }
        }

        // adding all the text to the string builders
        StringBuilder itemGenCommand = new StringBuilder("/").append(COMMAND_PREFIX).append(includeItem ? " full" : " item");
        StringBuilder itemText = new StringBuilder();
        itemText.append(itemName).append("\\n");
        itemGenCommand.append(" item_name:").append(itemName).append(" rarity:NONE item_lore:");

        // adding the entire lore to the string builder
        int maxLineLength = 0;
        for (JsonElement element : itemLoreArray) {
            String itemLore = element.getAsString().replaceAll("§", "&").replaceAll("`", "");
            itemText.append(itemLore).append("\\n");
            itemGenCommand.append(itemLore).append("\\n");

            if (maxLineLength < itemLore.length()) {
                maxLineLength = itemLore.length();
            }
        }
        maxLineLength++;
        itemGenCommand.replace(itemGenCommand.length() - 2, itemGenCommand.length(), "").append(" max_line_length:").append(maxLineLength);
        itemText.replace(itemText.length() - 2, itemText.length(), "");
        // checking if there was supposed to be an item stack is displayed with the item
        if (includeItem) {
            itemGenCommand.append(" item_id:").append(itemID).append(!extraModifiers.isEmpty() ? " extra_modifiers:" + extraModifiers : "");
        }

        // creating the generated description
        BufferedImage generatedDescription = builder.buildItem(event, "NONE", "NONE", itemText.toString(), "NONE", false, 255, 0, maxLineLength, true, false);
        if (generatedDescription == null) {
            event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, itemGenCommand)).setEphemeral(true).queue();
            return;
        }

        // checking if an item should be displayed alongside the description
        if (includeItem) {
            BufferedImage generatedItem = builder.buildUnspecifiedItem(event, itemID, extraModifiers, true);
            if (generatedItem == null) {
                return;
            }

            ImageMerger merger = new ImageMerger(generatedDescription, generatedItem, null);
            merger.drawFinalImage();
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(merger.getImage()))).setEphemeral(hidden).queue();
        } else {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedDescription))).setEphemeral(false).queue();
        }

        event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, itemGenCommand)).setEphemeral(true).queue();

        // Log item gen activity
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());
        long currentTime = System.currentTimeMillis();
        discordUser.getLastActivity().setLastItemGenUsage(currentTime);
        log.info("Updating last item generator activity date for " + Util.getDisplayName(event.getUser()) + " to " + currentTime);
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "general", description = "Show some general tips for using the Item Generation commands.")
    public void askForGeneralHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder helpPointerBuilder = new EmbedBuilder();

        infoBuilder.setColor(EMBED_COLORS[0])
            .setTitle("Item Generation Help Desk")
            .setDescription(GENERAL_INFO);

        helpPointerBuilder.setColor(EMBED_COLORS[1])
            .addField("Other Help Commands", GENERAL_HELP, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(helpPointerBuilder.build());
        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "item", description = "Show help related to the Item Generation command.")
    public void askForItemRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder colorsBuilder = new EmbedBuilder();
        EmbedBuilder otherInfoBuilder = new EmbedBuilder();
        EmbedBuilder examplesBuilder = new EmbedBuilder();

        infoBuilder.setColor(EMBED_COLORS[0])
            .setTitle("Item Generation")
            .setDescription(ITEM_BASIC_INFO)
            .addField("Item Arguments", ITEM_INFO_ARGUMENTS, false)
            .addField("Optional Arguments", ITEM_INFO_OPTIONAL_ARGUMENTS, false);

        colorsBuilder.setColor(EMBED_COLORS[1])
            .addField("Colors and Stats", ITEM_COLOR_CODES, true);

        otherInfoBuilder.setColor(EMBED_COLORS[2])
            .addField("Other Information", ITEM_OTHER_INFO, true);

        examplesBuilder.setColor(EMBED_COLORS[3])
            .addField("Item Gen Examples", ITEM_EXAMPLES, true);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(colorsBuilder.build());
        embeds.add(otherInfoBuilder.build());
        embeds.add(examplesBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "text", description = "Show help related to the Item Generation Text command.")
    public void askForTextRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder()
            .setColor(EMBED_COLORS[0])
            .setTitle("Text Generation")
            .setDescription(ITEM_TEXT_BASIC_INFO)
            .addField("Item Arguments", ITEM_TEXT_INFO_ARGUMENTS, false)
            .addField("Optional Arguments", ITEM_TEXT_INFO_OPTIONAL_ARGUMENTS, false);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "full", description = "Show a full help page for the Item Generation command.")
    public void askForFullRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();

        infoBuilder.setColor(EMBED_COLORS[0])
            .setTitle("Full Item Generation Help")
            .setDescription(FULL_GEN_INFO);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "display", description = "Show help related to the Display Item Generation command.")
    public void askForRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder itemModifiersBuilder = new EmbedBuilder();
        EmbedBuilder headModifiersBuilder = new EmbedBuilder();

        infoBuilder.setColor(EMBED_COLORS[0])
            .setTitle("Display Generation Help")
            .setDescription(DISPLAY_INFO_BASIC)
            .addField("Arguments", DISPLAY_INFO_ARGUMENTS, false)
            .addField("Optional Arguments", DISPLAY_INFO_OPTIONAL_ARGUMENTS, false);

        itemModifiersBuilder.setColor(EMBED_COLORS[1])
            .addField("Extra Modifiers", DISPLAY_INFO_EXTRA_MODIFIERS, false)
            .addField("Items with Modifiers", DISPLAY_INFO_MODIFIERS, false)
            .addField("Enchant Glint", DISPLAY_INFO_ENCHANT_GLINT, false);

        headModifiersBuilder.setColor(EMBED_COLORS[2])
            .addField("Head Generation", DISPLAY_ITEM_INFO_PLAYER_HEAD.formatted(stripString(event.getMember().getEffectiveName())), false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(itemModifiersBuilder.build());
        embeds.add(headModifiersBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "recipe", description = "Show help related to the Recipe Generation command.")
    public void askForRecipeRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();

        infoBuilder.setColor(EMBED_COLORS[0])
            .setTitle("Recipe Generation Help")
            .setDescription(RECIPE_INFO_BASIC)
            .addField("Arguments", RECIPE_INFO_ARGUMENTS, false);

        extraInfoBuilder.setColor(EMBED_COLORS[1])
            .addField("Recipe Examples", RECIPE_INFO_EXAMPLES, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "parse", description = "Show help related to the Parse Generation command.")
    public void askForParseRenderHelp(GuildSlashEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();

        infoBuilder.setColor(EMBED_COLORS[0])
            .setTitle("Parse Generation Help")
            .setDescription(ITEM_PARSE_INFO)
            .addField("Arguments", ITEM_PARSE_ARGUMENTS, false)
            .addField("Optional Arguments", ITEM_PARSE_OPTIONAL_ARGUMENTS, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "symbols", description = "Show a list of all stats symbols")
    public void showAllStats(GuildSlashEvent event) {
        List<EmbedBuilder> embedBuilders = new ArrayList<>();
        EmbedBuilder currentEmbed = new EmbedBuilder().setTitle("All Available Symbols").setColor(EMBED_COLORS[0]);
        StringBuilder idBuilder = new StringBuilder();
        StringBuilder symbolBuilder = new StringBuilder();
        StringBuilder displayBuilder = new StringBuilder();

        for (Stat stat : Stat.VALUES) {
            String idLine = "%%" + stat.name() + "%%\n";
            String symbolLine = stat.getIcon() + "\n";
            String displayLine = stat.getDisplay() + "\n";

            if (idBuilder.length() + idLine.length() > 1_024
                || symbolBuilder.length() + symbolLine.length() > 1_024
                || displayBuilder.length() + displayLine.length() > 1_024) {
                currentEmbed.addField("ID", idBuilder.toString(), true);
                currentEmbed.addField("Symbol", symbolBuilder.toString(), true);
                currentEmbed.addField("Display", displayBuilder.toString(), true);

                embedBuilders.add(currentEmbed);
                currentEmbed = new EmbedBuilder().setTitle("All Available Symbols").setColor(EMBED_COLORS[0]);

                idBuilder = new StringBuilder();
                symbolBuilder = new StringBuilder();
                displayBuilder = new StringBuilder();
            }

            idBuilder.append(idLine);
            symbolBuilder.append(symbolLine);
            displayBuilder.append(displayLine);
        }

        if (!idBuilder.isEmpty()) {
            currentEmbed.addField("ID", idBuilder.toString(), true);
            currentEmbed.addField("Symbol", symbolBuilder.toString(), true);
            currentEmbed.addField("Display", displayBuilder.toString(), true);
            embedBuilders.add(currentEmbed);
        }

        event.replyEmbeds(embedBuilders.stream()
                .map(EmbedBuilder::build)
                .collect(Collectors.toList())
            ).setEphemeral(true)
            .queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "icons", description = "Show a list of all other icons")
    public void showAllIcons(GuildSlashEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("All Available Icons").setColor(EMBED_COLORS[0]);
        StringBuilder idBuilder = new StringBuilder();
        StringBuilder symbolBuilder = new StringBuilder();

        for (Icon icon : Icon.VALUES) {
            idBuilder.append("%%").append(icon.name()).append("%%").append("\n");
            symbolBuilder.append(icon.getIcon()).append("\n");
        }

        embedBuilder.addField("ID", idBuilder.toString(), true);
        embedBuilder.addField("Symbol", symbolBuilder.toString(), true);

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "colors", description = "Show a list of all colors")
    public void showAllColors(GuildSlashEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("All Available Colors").setColor(EMBED_COLORS[0]);
        StringBuilder idBuilder = new StringBuilder();
        StringBuilder colorBuilder = new StringBuilder();

        for (MCColor color : MCColor.VALUES) {
            idBuilder.append("&").append(color.getColorCode()).append(" or %%").append(color.name()).append("%%").append("\n");
            colorBuilder.append(color.name()).append("\n");
        }

        embedBuilder.addField("IDs", idBuilder.toString(), true);
        embedBuilder.addField("Color", colorBuilder.toString(), true);

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "flavors", description = "Show a list of all flavor texts")
    public void showAllFlavorTexts(GuildSlashEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("All Available Flavor texts").setColor(EMBED_COLORS[0]);
        StringBuilder idBuilder = new StringBuilder();
        StringBuilder textBuilder = new StringBuilder();

        for (Flavor flavor : Flavor.VALUES) {
            idBuilder.append("%%").append(flavor.name()).append("%%").append("\n");
            textBuilder.append(flavor.getText()).append("\n");
        }

        embedBuilder.addField("IDs", idBuilder.toString(), true);
        embedBuilder.addField("Flavor texts", textBuilder.toString(), true);

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }

    private boolean isIncorrectChannel(GuildSlashEvent event) {
        String senderChannelId = event.getChannel().getId();
        String[] itemGenChannelIds = NerdBotApp.getBot().getConfig().getChannelConfig().getGenChannelIds();

        if (itemGenChannelIds == null) {
            event.reply("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return true;
        }

        // If the command was used in a thread, check if the parent channel is an item gen channel
        if (event.getChannel() instanceof ThreadChannel threadChannel) {
            senderChannelId = threadChannel.getParentChannel().getId();
        }

        if (Util.safeArrayStream(itemGenChannelIds).noneMatch(senderChannelId::equalsIgnoreCase)) {
            ChannelCache.getChannelByName(itemGenChannelIds[0]).ifPresentOrElse(
                channel -> event.reply("This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue(),
                () -> event.reply("This can only be used in the item generating channel.").setEphemeral(true).queue()
            );

            return true;
        }

        return false;
    }
}

