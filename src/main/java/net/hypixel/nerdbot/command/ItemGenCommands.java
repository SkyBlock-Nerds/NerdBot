package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.generator.*;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.Rarity;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.hypixel.nerdbot.generator.GeneratorStrings.*;

@Log4j2
public class ItemGenCommands extends ApplicationCommand {
    private final GeneratorBuilder builder;

    public ItemGenCommands() {
        super();
        this.builder = new GeneratorBuilder();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "item", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock")
    public void generateItem(GuildSlashEvent event,
                             @AppOption(description = DESC_NAME) String name,
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
        event.deferReply(hidden).queue();
        // building the item's description
        BufferedImage generatedImage = builder.buildItem(event, name, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "text", description = "Creates an image that looks like a message from Minecraft, primarily used for Hypixel Skyblock")
    public void generateText(GuildSlashEvent event, @AppOption(description = DESC_TEXT) String message, @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();
        // building the chat message
        BufferedImage generatedImage = builder.buildItem(event, "NONE", "NONE", message, "", true, 0, 1, StringColorParser.MAX_FINAL_LINE_LENGTH, false);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "head", description = "Draws a minecraft head into a file")
    public void generateHead(GuildSlashEvent event,
                             @AppOption(description = DESC_HEAD_ID) String skinId,
                             @Optional @AppOption(description = DESC_IS_PLAYER_NAME) Boolean isPlayerName,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        BufferedImage head = builder.buildHead(event, skinId, isPlayerName);
        if (head != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(head))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "full", description = "Generates a full item stack!")
    public void generateFullItem(GuildSlashEvent event,
                                 @AppOption(description = DESC_NAME) String name,
                                 @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                                 @AppOption(description = DESC_ITEM_LORE) String itemLore,
                                 @Optional @AppOption(description = DESC_ITEM_NAME) String itemName,
                                 @Optional @AppOption(description = DESC_EXTRA_ITEM_MODIFIERS) String extraModifiers,
                                 @Optional @AppOption(description = DESC_TYPE) String type,
                                 @Optional @AppOption(description = DESC_DISABLE_RARITY_LINEBREAK) Boolean disableRarityLinebreak,
                                 @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                                 @Optional @AppOption(description = DESC_PADDING) Integer padding,
                                 @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                                 @Optional @AppOption(description = DESC_RECIPE) String recipe,
                                 @Optional @AppOption(description = DESC_RENDER_INVENTORY) Boolean renderBackground,
                                 @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        // checking that there is a recipe and/or item to be displayed
        if (itemName == null && recipe == null) {
            event.getHook().sendMessage(MISSING_FULL_GEN_ITEM).queue();
            return;
        }

        extraModifiers = Objects.requireNonNullElse(extraModifiers, "");
        renderBackground = Objects.requireNonNullElse(renderBackground, true);

        // building the description for the item
        BufferedImage generatedDescription = builder.buildItem(event, name, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true);
        if (generatedDescription == null) {
            return;
        }

        // building the item for the which is beside the description
        BufferedImage generatedItem = null;
        if (itemName != null) {
           generatedItem = builder.buildUnspecifiedItem(event, itemName, extraModifiers);
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
        event.deferReply(hidden).queue();

        renderBackground = (renderBackground == null || renderBackground);

        // building the Minecraft recipe
        BufferedImage generatedRecipe = builder.buildRecipe(event, recipe, renderBackground);
        if (generatedRecipe != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedRecipe))).queue();
        }
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "parse", description = "Converts a minecraft item into a Nerd Bot item!")
    public void parseItemDescription(GuildSlashEvent event,
                                     @AppOption(description = DESC_PARSE_ITEM, name = "item_nbt") String itemNBT,
                                     @Optional @AppOption (description = DESC_INCLUDE_ITEM) Boolean includeItem,
                                     @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        includeItem = Objects.requireNonNullElse(includeItem, false);

        // converting the nbt into json
        JsonObject itemJSON;
        try {
            itemJSON = NerdBotApp.GSON.fromJson(itemNBT, JsonObject.class);
        } catch (JsonSyntaxException e) {
            event.getHook().sendMessage(ITEM_PARSE_JSON_FORMAT).queue();
            return;
        }

        // checking if the user has copied the text directly from in game
        JsonObject tagJSON = Util.isJsonObject(itemJSON, "tag");
        if (tagJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("tag")).queue();
            return;
        }

        // checking if there is a display tag
        JsonObject displayJSON = Util.isJsonObject(tagJSON, "display");
        if (displayJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("display")).queue();
            return;
        }
        // checking that there is a name and lore parameters in the JsonObject
        String itemName = Util.isJsonString(displayJSON, "Name");
        JsonArray itemLoreArray = Util.isJsonArray(displayJSON, "Lore");
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
            itemID = Util.isJsonString(itemJSON, "id");
            if (itemID == null) {
                event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("id")).queue();
                return;
            }
            itemID = itemID.replace("minecraft:", "");

            if (itemID.equals("skull")) {
                // checking if there is a SkullOwner json object within the main tag json
                JsonObject skullOwnerJSON = Util.isJsonObject(tagJSON, "SkullOwner");
                if (skullOwnerJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("SkullOwner")).queue();
                    return;
                }
                // checking if there is a Properties json object within SkullOwner
                JsonObject propertiesJSON = Util.isJsonObject(skullOwnerJSON, "Properties");
                if (propertiesJSON == null) {
                    event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Properties")).queue();
                    return;
                }
                // checking if there is a textures json object within properties
                JsonArray texturesJSON = Util.isJsonArray(propertiesJSON, "textures");
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
                String base64String = Util.isJsonString(texturesJSON.get(0).getAsJsonObject(), "Value");
                if (base64String == null) {
                    event.getHook().sendMessage(INVALID_ITEM_SKULL_DATA).queue();
                    return;
                }
                // converting the Base64 string into the Skin URL
                try {
                    extraModifiers = builder.base64ToSkinURL(base64String) + ",false";
                } catch (NullPointerException | IllegalArgumentException e) {
                    event.getHook().sendMessage(INVALID_BASE_64_SKIN_URL).queue();
                    return;
                }
            }
            else {
                // checking if there is a color attribute present and adding it to the extra attributes
                String color = Util.isJsonString(displayJSON, "color");
                if (color != null) {
                    try {
                        Integer selectedColor = Integer.decode(color);
                        extraModifiers = String.valueOf(selectedColor);
                    } catch (NumberFormatException ignored) {}
                }

                // checking if the item is enchanted and applying the enchantment glint to the extra modifiers
                JsonArray enchantJson = Util.isJsonArray(tagJSON, "ench");
                if (enchantJson != null) {
                    extraModifiers = extraModifiers.length() == 0 ? "enchant" : extraModifiers + ",enchant";
                }
            }
        }

        // adding all the text to the string builders
        StringBuilder itemGenCommand = new StringBuilder("/").append(COMMAND_PREFIX).append(includeItem ? " full" : " item");
        StringBuilder itemText = new StringBuilder();
        itemText.append(itemName).append("\\n");
        itemGenCommand.append(" name:").append(itemName).append(" rarity:NONE item_lore:");

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
            itemGenCommand.append(" item_name:").append(itemID).append(extraModifiers.length() != 0 ? " extra_modifiers:" + extraModifiers : "");
        }

        // creating the generated description
        BufferedImage generatedDescription = builder.buildItem(event, "NONE", "NONE", itemText.toString(), "NONE", false, 255, 0, maxLineLength, true);
        if (generatedDescription == null) {
            event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, itemGenCommand)).setEphemeral(true).queue();
            return;
        }

        // checking if an item should be displayed alongside the description
        if (includeItem) {
            BufferedImage generatedItem = builder.buildUnspecifiedItem(event, itemID, extraModifiers);
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
    }


    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "help", description = "Get a little bit of help with how to use the Generator bot.")
    public void askForInfo(GuildSlashEvent event) {
        if (isIncorrectChannel(event)) {
            return;
        }

        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder argumentBuilder = new EmbedBuilder();
        EmbedBuilder colorBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();
        infoBuilder.setColor(Color.CYAN)
                .setAuthor("SkyBlock Nerd Bot")
                .setTitle("Item Generation")
                .addField("Basic Info", ITEM_BASIC_INFO, true);

        argumentBuilder.setColor(Color.GREEN)
                .addField("Arguments", ITEM_INFO_ARGUMENTS, false);

        colorBuilder.setColor(Color.YELLOW)
                .addField("Color Codes", ITEM_COLOR_CODES, false);

        extraInfoBuilder.setColor(Color.GRAY)
                .addField("Other Information", ITEM_OTHER_INFO, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(colorBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "head_help", description = "Get a little bit of help with how to use the Head Rendering functions of the Generator bot.")
    public void askForRenderHelp(GuildSlashEvent event) {
        if (isIncorrectChannel(event)) {
            return;
        }

        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder argumentBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
                .setAuthor("SkyBlock Nerd Bot")
                .setTitle("Head Generation")
                .addField("Basic Info", HEAD_INFO_BASIC, true);

        argumentBuilder.setColor(Color.GREEN)
                .addField("Arguments", HEAD_INFO_ARGUMENTS, false);

        extraInfoBuilder.setColor(Color.GRAY)
                .addField("Other Information", HEAD_INFO_OTHER_INFORMATION, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }
    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "recipe_help", description = "Get a little bit of help with how to use the Recipe Rendering functions of the Generator bot.")
    public void askForRecipeRenderHelp(GuildSlashEvent event) {
        if (isIncorrectChannel(event)) {
            return;
        }

        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder argumentBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
                .setAuthor("Skyblock Nerd Bot")
                .setTitle("Recipe Generation Help")
                .addField("Basic Info", RECIPE_INFO_BASIC, true);

        argumentBuilder.setColor(Color.GREEN)
                .addField("Arguments", RECIPE_INFO_ARGUMENTS, false);

        extraInfoBuilder.setColor(Color.GRAY)
                .addField("Other Information", RECIPE_INFO_OTHER_INFORMATION, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = COMMAND_PREFIX, subcommand = "stat_symbols", description = "Show a list of all stats symbols")
    public void showAllStats(GuildSlashEvent event) {
        event.reply(STAT_SYMBOLS).setEphemeral(true).queue();
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }

    private boolean isIncorrectChannel(GuildSlashEvent event) {
        String senderChannelId = event.getChannel().getId();
        String[] itemGenChannelIds = NerdBotApp.getBot().getConfig().getItemGenChannel();

        if (itemGenChannelIds == null) {
            event.reply("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return true;
        }

        if (Arrays.stream(itemGenChannelIds).noneMatch(senderChannelId::equalsIgnoreCase)) {
            // The top channel in the config should be considered the 'primary channel', which is referenced in the
            // error message.
            TextChannel channel = ChannelManager.getChannel(itemGenChannelIds[0]);
            if (channel == null) {
                event.reply("This can only be used in the item generating channel.").setEphemeral(true).queue();
                return true;
            }
            event.reply("This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue();
            return true;
        }

        return false;
    }

}

