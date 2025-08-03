package net.hypixel.nerdbot.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.generator.GeneratorBuilder;
import net.hypixel.nerdbot.generator.ImageMerger;
import net.hypixel.nerdbot.generator.parser.StringColorParser;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.ArrayUtils;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.FileUtils;
import net.hypixel.nerdbot.util.JsonUtils;
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
import java.util.concurrent.CompletableFuture;
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

@Slf4j
public class GeneratorCommands {
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

    @SlashCommand(name = COMMAND_PREFIX, subcommand = "item", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock", guildOnly = true)
    public void generateItem(SlashCommandInteractionEvent event,
                             @SlashOption(description = DESC_ITEM_NAME) String itemName,
                             @SlashOption(description = DESC_RARITY, autocompleteId = "rarities") String rarity,
                             @SlashOption(description = DESC_ITEM_LORE) String itemLore,
                             @SlashOption(description = DESC_TYPE, required = false) String type,
                             @SlashOption(description = DESC_DISABLE_RARITY_LINEBREAK, required = false) Boolean disableRarityLinebreak,
                             @SlashOption(description = DESC_ALPHA, required = false) Integer alpha,
                             @SlashOption(description = DESC_PADDING, required = false) Integer padding,
                             @SlashOption(description = DESC_MAX_LINE_LENGTH, required = false) Integer maxLineLength,
                             @SlashOption(description = DESC_HIDDEN, required = false) Boolean hidden) {
        if (isIncorrectChannel(event)) {
            return;
        }
        final boolean hide = (hidden != null && hidden);
        event.deferReply(hide).complete();
        // building the item's description
        builder.buildItemAsync(event, itemName, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true, false)
            .thenCompose(generatedImage -> {
                if (generatedImage == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return FileUtils.toFileAsync(generatedImage);
            })
            .thenAccept(file -> {
                if (file != null) {
                    event.getHook().sendFiles(FileUpload.fromData(file)).setEphemeral(hide).queue();
                }
            })
            .exceptionally(throwable -> {
                log.error("Failed to generate or convert image to file", throwable);
                return null;
            });

        logItemGenActivity(event);
    }

    @SlashCommand(name = COMMAND_PREFIX, subcommand = "text", description = "Creates an image that looks like a message from Minecraft, primarily used for Hypixel Skyblock", guildOnly = true)
    public void generateText(SlashCommandInteractionEvent event,
                             @SlashOption(description = DESC_TEXT) String message,
                             @SlashOption(description = DESC_ALPHA, required = false) Integer alpha,
                             @SlashOption(description = DESC_CENTERED, required = false) Boolean centered,
                             @SlashOption(description = DESC_HIDDEN, required = false) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        final boolean hide = (hidden != null && hidden);
        centered = (centered != null && centered);
        alpha = alpha == null ? 128 : Math.max(0, Math.min(alpha, 255));
        event.deferReply(hide).complete();
        // building the chat message
        builder.buildItemAsync(event, "NONE", "NONE", message, "", true, alpha, 1, StringColorParser.MAX_FINAL_LINE_LENGTH, false, centered)
            .thenCompose(generatedImage -> {
                if (generatedImage == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return FileUtils.toFileAsync(generatedImage);
            })
            .thenAccept(file -> {
                if (file != null) {
                    event.getHook().sendFiles(FileUpload.fromData(file)).setEphemeral(hide).queue();
                }
            })
            .exceptionally(throwable -> {
                log.error("Failed to generate or convert image to file", throwable);
                return null;
            });

        logItemGenActivity(event);
    }

    @SlashCommand(name = COMMAND_PREFIX, subcommand = "display", description = "Draws a Minecraft item into a file", guildOnly = true)
    public void generateItemImage(SlashCommandInteractionEvent event,
                                  @SlashOption(description = DESC_ITEM_ID, name = "item_id") String itemID,
                                  @SlashOption(description = DESC_EXTRA_ITEM_MODIFIERS, required = false) String extraModifiers,
                                  @SlashOption(description = DESC_HIDDEN, required = false) Boolean hidden) {
        if (isIncorrectChannel(event)) {
            return;
        }
        final boolean hide = (hidden != null && hidden);
        event.deferReply(hide).complete();

        builder.buildUnspecifiedItemAsync(event, itemID, extraModifiers, true)
            .thenCompose(item -> {
                if (item == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return FileUtils.toFileAsync(item);
            })
            .thenAccept(file -> {
                if (file != null) {
                    event.getHook().sendFiles(FileUpload.fromData(file)).setEphemeral(hide).queue();
                }
            })
            .exceptionally(throwable -> {
                log.error("Failed to generate or convert image to file", throwable);
                return null;
            });

        logItemGenActivity(event);
    }

    @SlashCommand(name = COMMAND_PREFIX, subcommand = "full", description = "Creates an image that looks like an item from Minecraft, complete with lore and a display item.", guildOnly = true)
    public void generateFullItem(SlashCommandInteractionEvent event,
                                 @SlashOption(description = DESC_ITEM_NAME) String itemName,
                                 @SlashOption(description = DESC_RARITY, autocompleteId = "rarities") String rarity,
                                 @SlashOption(description = DESC_ITEM_LORE) String itemLore,
                                 @SlashOption(description = DESC_TYPE, required = false) String type,
                                 @SlashOption(description = DESC_DISABLE_RARITY_LINEBREAK, required = false) Boolean disableRarityLinebreak,
                                 @SlashOption(description = DESC_ALPHA, required = false) Integer alpha,
                                 @SlashOption(description = DESC_PADDING, required = false) Integer padding,
                                 @SlashOption(description = DESC_MAX_LINE_LENGTH, required = false) Integer maxLineLength,
                                 @SlashOption(description = DESC_ITEM_ID, required = false) String itemId,
                                 @SlashOption(description = DESC_EXTRA_ITEM_MODIFIERS, required = false) String extraModifiers,
                                 @SlashOption(description = DESC_RECIPE, required = false) String recipe,
                                 @SlashOption(description = DESC_RENDER_INVENTORY, required = false) Boolean renderBackground,
                                 @SlashOption(description = DESC_HIDDEN, required = false) Boolean hidden) {
        if (isIncorrectChannel(event)) {
            return;
        }
        final boolean hide = (hidden != null && hidden);
        event.deferReply(hide).complete();

        // checking that there are two or more different items to merge the images
        if ((itemName == null || rarity == null || itemLore == null) && itemId == null && recipe == null) {
            event.getHook().sendMessage(MISSING_FULL_GEN_ITEM).queue();
            return;
        }

        extraModifiers = Objects.requireNonNullElse(extraModifiers, "");
        renderBackground = Objects.requireNonNullElse(renderBackground, true);

        // Create futures for all async operations
        CompletableFuture<BufferedImage> descriptionFuture =
            (itemName != null && rarity != null && itemLore != null)
                ? builder.buildItemAsync(event, itemName, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true, false)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<BufferedImage> itemFuture =
            (itemId != null)
                ? builder.buildUnspecifiedItemAsync(event, itemId, extraModifiers, true)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<BufferedImage> recipeFuture =
            (recipe != null)
                ? builder.buildRecipeAsync(event, recipe, renderBackground)
                : CompletableFuture.completedFuture(null);

        // Combine all futures and process the result
        CompletableFuture.allOf(descriptionFuture, itemFuture, recipeFuture)
            .thenCompose(unused -> {
                try {
                    BufferedImage generatedDescription = descriptionFuture.get();
                    BufferedImage generatedItem = itemFuture.get();
                    BufferedImage generatedRecipe = recipeFuture.get();

                    ImageMerger merger = new ImageMerger(generatedDescription, generatedItem, generatedRecipe);
                    merger.drawFinalImage();
                    return FileUtils.toFileAsync(merger.getImage());
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            })
            .thenAccept(file -> {
                if (file != null) {
                    event.getHook().sendFiles(FileUpload.fromData(file)).setEphemeral(hide).queue();
                }
            })
            .exceptionally(throwable -> {
                log.error("Failed to generate or convert merged image to file", throwable);
                return null;
            });

        logItemGenActivity(event);
    }

    @SlashCommand(name = COMMAND_PREFIX, subcommand = "recipe", description = "Generates a Minecraft Recipe Image", guildOnly = true)
    public void generateRecipe(SlashCommandInteractionEvent event,
                               @SlashOption(description = DESC_RECIPE) String recipe,
                               @SlashOption(description = DESC_RENDER_INVENTORY, required = false) Boolean renderBackground,
                               @SlashOption(description = DESC_HIDDEN, required = false) Boolean hidden) {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).complete();

        renderBackground = (renderBackground == null || renderBackground);

        // building the Minecraft recipe
        builder.buildRecipeAsync(event, recipe, renderBackground)
            .thenCompose(generatedRecipe -> {
                if (generatedRecipe == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return FileUtils.toFileAsync(generatedRecipe);
            })
            .thenAccept(file -> {
                if (file != null) {
                    event.getHook().sendFiles(FileUpload.fromData(file)).queue();
                }
            })
            .exceptionally(throwable -> {
                log.error("Failed to generate or convert recipe image to file", throwable);
                return null;
            });

        logItemGenActivity(event);
    }

    @SlashCommand(name = COMMAND_PREFIX, subcommand = "parse", description = "Converts a minecraft item into a Nerd Bot item!", guildOnly = true)
    public void parseItemDescription(SlashCommandInteractionEvent event,
                                     @SlashOption(description = DESC_PARSE_ITEM, name = "item_nbt") String itemNBT,
                                     @SlashOption(description = DESC_INCLUDE_ITEM, required = false) Boolean includeItem,
                                     @SlashOption(description = DESC_HIDDEN, required = false) Boolean hidden
    ) {
        if (isIncorrectChannel(event)) {
            return;
        }

        final boolean hide = (hidden != null && hidden);
        event.deferReply(hide).complete();
        final boolean shouldIncludeItem = Objects.requireNonNullElse(includeItem, false);

        JsonObject itemJSON = validateAndParseItemJSON(event, itemNBT);
        if (itemJSON == null) {
            return;
        }

        DisplayData displayData = validateDisplayData(event, itemJSON);
        if (displayData == null) {
            return;
        }

        String itemName = displayData.itemName();
        JsonArray itemLoreArray = displayData.itemLoreArray();
        JsonObject displayJSON = displayData.displayJSON();
        JsonObject tagJSON = displayData.tagJSON();

        final ItemData itemData;
        if (shouldIncludeItem) {
            itemData = processItemData(event, itemJSON, tagJSON, displayJSON);
            if (itemData == null) {
                return;
            }
        } else {
            itemData = new ItemData("", "");
        }

        CommandData commandData = buildCommandAndText(itemName, itemLoreArray, shouldIncludeItem, itemData.itemID(), itemData.extraModifiers());

        builder.buildItemAsync(event, "NONE", "NONE", commandData.itemText(), "NONE", false, 255, 0, commandData.maxLineLength(), true, false)
            .thenCompose(generatedDescription -> {
                if (generatedDescription == null) {
                    event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, commandData.itemGenCommand())).setEphemeral(true).queue();
                    return CompletableFuture.completedFuture(null);
                }

                if (shouldIncludeItem) {
                    return builder.buildUnspecifiedItemAsync(event, itemData.itemID(), itemData.extraModifiers(), true)
                        .thenCompose(generatedItem -> {
                            if (generatedItem == null) {
                                return CompletableFuture.completedFuture(null);
                            }

                            ImageMerger merger = new ImageMerger(generatedDescription, generatedItem, null);
                            merger.drawFinalImage();
                            return FileUtils.toFileAsync(merger.getImage());
                        })
                        .thenAccept(file -> {
                            if (file != null) {
                                event.getHook().sendFiles(FileUpload.fromData(file)).setEphemeral(hide).queue();
                                event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, commandData.itemGenCommand())).setEphemeral(true).queue();
                            }
                        })
                        .thenApply(unused -> null);
                } else {
                    return FileUtils.toFileAsync(generatedDescription)
                        .thenAccept(file -> {
                            if (file != null) {
                                event.getHook().sendFiles(FileUpload.fromData(file)).setEphemeral(false).queue();
                                event.getHook().sendMessage(String.format(ITEM_PARSE_COMMAND, commandData.itemGenCommand())).setEphemeral(true).queue();
                            }
                        })
                        .thenApply(unused -> null);
                }
            })
            .exceptionally(throwable -> {
                log.error("Failed to generate or convert image to file", throwable);
                return null;
            });

        logItemGenActivity(event);
    }

    private JsonObject validateAndParseItemJSON(SlashCommandInteractionEvent event, String itemNBT) {
        try {
            return NerdBotApp.GSON.fromJson(itemNBT, JsonObject.class);
        } catch (JsonSyntaxException exception) {
            event.getHook().sendMessage(ITEM_PARSE_JSON_FORMAT).queue();
            return null;
        }
    }

    private record DisplayData(String itemName, JsonArray itemLoreArray, JsonObject displayJSON, JsonObject tagJSON) {
    }

    private DisplayData validateDisplayData(SlashCommandInteractionEvent event, JsonObject itemJSON) {
        JsonObject tagJSON = JsonUtils.isJsonObject(itemJSON, "tag");
        if (tagJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("tag")).queue();
            return null;
        }

        JsonObject displayJSON = JsonUtils.isJsonObject(tagJSON, "display");
        if (displayJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("display")).queue();
            return null;
        }

        String itemName = JsonUtils.isJsonString(displayJSON, "Name");
        JsonArray itemLoreArray = JsonUtils.isJsonArray(displayJSON, "Lore");

        if (itemName == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Name")).queue();
            return null;
        }
        if (itemLoreArray == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Lore")).queue();
            return null;
        }

        itemName = itemName.replaceAll("§", "&");
        return new DisplayData(itemName, itemLoreArray, displayJSON, tagJSON);
    }

    private record ItemData(String itemID, String extraModifiers) {
    }

    private ItemData processItemData(SlashCommandInteractionEvent event, JsonObject itemJSON, JsonObject tagJSON, JsonObject displayJSON) {
        String itemID = JsonUtils.isJsonString(itemJSON, "id");
        if (itemID == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("id")).queue();
            return null;
        }
        itemID = itemID.replace("minecraft:", "");

        String extraModifiers = "";
        if (itemID.equals("skull")) {
            extraModifiers = processSkullData(event, tagJSON);
            if (extraModifiers == null) {
                return null;
            }
        } else {
            extraModifiers = processNonSkullModifiers(tagJSON, displayJSON);
        }

        return new ItemData(itemID, extraModifiers);
    }

    private String processSkullData(SlashCommandInteractionEvent event, JsonObject tagJSON) {
        JsonObject skullOwnerJSON = JsonUtils.isJsonObject(tagJSON, "SkullOwner");
        if (skullOwnerJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("SkullOwner")).queue();
            return null;
        }

        JsonObject propertiesJSON = JsonUtils.isJsonObject(skullOwnerJSON, "Properties");
        if (propertiesJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("Properties")).queue();
            return null;
        }

        JsonArray texturesJSON = JsonUtils.isJsonArray(propertiesJSON, "textures");
        if (texturesJSON == null) {
            event.getHook().sendMessage(MISSING_ITEM_NBT.formatted("textures")).queue();
            return null;
        }

        if (texturesJSON.size() != 1) {
            event.getHook().sendMessage(MULTIPLE_ITEM_SKULL_DATA).queue();
            return null;
        }

        if (!texturesJSON.get(0).isJsonObject()) {
            event.getHook().sendMessage(INVALID_ITEM_SKULL_DATA).queue();
            return null;
        }

        String base64String = JsonUtils.isJsonString(texturesJSON.get(0).getAsJsonObject(), "Value");
        if (base64String == null) {
            event.getHook().sendMessage(INVALID_ITEM_SKULL_DATA).queue();
            return null;
        }

        try {
            return builder.base64ToSkinURL(base64String);
        } catch (NullPointerException | IllegalArgumentException exception) {
            event.getHook().sendMessage(INVALID_BASE_64_SKIN_URL).queue();
            return null;
        }
    }

    private String processNonSkullModifiers(JsonObject tagJSON, JsonObject displayJSON) {
        String extraModifiers = "";

        if (displayJSON.has("color")) {
            try {
                int colorValue;
                if (displayJSON.get("color").isJsonPrimitive() && displayJSON.get("color").getAsJsonPrimitive().isNumber()) {
                    colorValue = displayJSON.get("color").getAsInt();
                } else {
                    String colorString = JsonUtils.isJsonString(displayJSON, "color");
                    if (colorString != null) {
                        colorValue = Integer.decode(colorString);
                    } else {
                        colorValue = -1;
                    }
                }
                
                if (colorValue >= 0) {
                    extraModifiers = String.format("#%06X", colorValue);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        JsonArray enchantJson = JsonUtils.isJsonArray(tagJSON, "ench");
        if (enchantJson != null) {
            extraModifiers = extraModifiers.isEmpty() ? "enchant" : extraModifiers + ",enchant";
        }

        return extraModifiers;
    }

    private record CommandData(String itemGenCommand, String itemText, int maxLineLength) {
    }

    private CommandData buildCommandAndText(String itemName, JsonArray itemLoreArray, boolean includeItem, String itemID, String extraModifiers) {
        StringBuilder itemGenCommand = new StringBuilder("/").append(COMMAND_PREFIX).append(includeItem ? " full" : " item");
        StringBuilder itemText = new StringBuilder();
        itemText.append(itemName).append("\\n");
        itemGenCommand.append(" item_name:").append(itemName).append(" rarity:NONE item_lore:");

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

        if (includeItem) {
            itemGenCommand.append(" item_id:").append(itemID).append(!extraModifiers.isEmpty() ? " extra_modifiers:" + extraModifiers : "");
        }

        return new CommandData(itemGenCommand.toString(), itemText.toString(), maxLineLength);
    }

    private void logItemGenActivity(SlashCommandInteractionEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        long currentTime = System.currentTimeMillis();

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser != null) {
                    discordUser.getLastActivity().setLastItemGenUsage(currentTime);
                    log.info("Updating last item generator activity date for {} to {}", DiscordUtils.getDisplayName(event.getUser()), currentTime);
                }
            })
            .exceptionally(throwable -> {
                log.warn("Failed to log item generator activity for user {}", event.getUser().getId(), throwable);
                return null;
            });
    }

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "general", description = "Show some general tips for using the Item Generation commands.", guildOnly = true)
    public void askForGeneralHelp(SlashCommandInteractionEvent event) {
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

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "item", description = "Show help related to the Item Generation command.", guildOnly = true)
    public void askForItemRenderHelp(SlashCommandInteractionEvent event) {
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

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "text", description = "Show help related to the Item Generation Text command.", guildOnly = true)
    public void askForTextRenderHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder()
            .setColor(EMBED_COLORS[0])
            .setTitle("Text Generation")
            .setDescription(ITEM_TEXT_BASIC_INFO)
            .addField("Item Arguments", ITEM_TEXT_INFO_ARGUMENTS, false)
            .addField("Optional Arguments", ITEM_TEXT_INFO_OPTIONAL_ARGUMENTS, false);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "full", description = "Show a full help page for the Item Generation command.", guildOnly = true)
    public void askForFullRenderHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder infoBuilder = new EmbedBuilder();

        infoBuilder.setColor(EMBED_COLORS[0])
            .setTitle("Full Item Generation Help")
            .setDescription(FULL_GEN_INFO);

        event.replyEmbeds(infoBuilder.build()).setEphemeral(true).queue();
    }

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "display", description = "Show help related to the Display Item Generation command.", guildOnly = true)
    public void askForRenderHelp(SlashCommandInteractionEvent event) {
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

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "recipe", description = "Show help related to the Recipe Generation command.", guildOnly = true)
    public void askForRecipeRenderHelp(SlashCommandInteractionEvent event) {
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

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "parse", description = "Show help related to the Parse Generation command.", guildOnly = true)
    public void askForParseRenderHelp(SlashCommandInteractionEvent event) {
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

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "symbols", description = "Show a list of all stats symbols", guildOnly = true)
    public void showAllStats(SlashCommandInteractionEvent event) {
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
                .toList()
            ).setEphemeral(true)
            .queue();
    }

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "icons", description = "Show a list of all other icons", guildOnly = true)
    public void showAllIcons(SlashCommandInteractionEvent event) {
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

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "colors", description = "Show a list of all colors", guildOnly = true)
    public void showAllColors(SlashCommandInteractionEvent event) {
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

    @SlashCommand(name = COMMAND_PREFIX, group = "help", subcommand = "flavors", description = "Show a list of all flavor texts", guildOnly = true)
    public void showAllFlavorTexts(SlashCommandInteractionEvent event) {
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

    @SlashAutocompleteHandler(id = "rarities")
    public List<Command.Choice> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES)
            .map(rarity -> new Command.Choice(rarity.name(), rarity.name()))
            .toList();
    }

    private boolean isIncorrectChannel(SlashCommandInteractionEvent event) {
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

        if (ArrayUtils.safeArrayStream(itemGenChannelIds).noneMatch(senderChannelId::equalsIgnoreCase)) {
            ChannelCache.getChannelByName(itemGenChannelIds[0]).ifPresentOrElse(
                channel -> event.reply("This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue(),
                () -> event.reply("This can only be used in the item generating channel.").setEphemeral(true).queue()
            );

            return true;
        }

        return false;
    }
}

