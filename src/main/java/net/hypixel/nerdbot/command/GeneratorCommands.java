package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.generator.ItemBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.impl.MinecraftItemGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftPlayerHeadGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftRecipeGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.util.GeneratorMessages;
import net.hypixel.nerdbot.generator.util.Item;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.spritesheet.ItemSpritesheet;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class GeneratorCommands extends ApplicationCommand {

    private static final String BASE_COMMAND = "generate";

    @JDASlashCommand(name = BASE_COMMAND, group = "display", subcommand = "item", description = "Display an item")
    public void generateItem(GuildSlashEvent event, @AppOption(autocomplete = "item-names") String itemId, @AppOption Boolean enchanted) {
        event.deferReply().complete();

        enchanted = enchanted != null && enchanted;

        try {
            Item item = new ItemBuilder()
                .addGenerator(new MinecraftItemGenerator.Builder()
                    .withItem(itemId)
                    .isEnchanted(enchanted)
                    .build()
                ).build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(item.getImage()), "item.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            exception.printStackTrace();
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "head", description = "Generate a player head")
    public void generateHead(GuildSlashEvent event, @AppOption String texture) {
        event.deferReply().complete();

        try {
            Item item = new ItemBuilder()
                .addGenerator(new MinecraftPlayerHeadGenerator.Builder().withSkin(texture).build())
                .build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(item.getImage()), "head.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that player head!").queue();
            exception.printStackTrace();
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "recipe", description = "Generate a recipe")
    public void generateRecipe(GuildSlashEvent event, @AppOption String recipeString, @AppOption @Optional Boolean renderBackground) {
        event.deferReply().complete();

        renderBackground = renderBackground == null || renderBackground;

        try {
            Item item = new ItemBuilder()
                .addGenerator(new MinecraftRecipeGenerator.Builder()
                    .withRecipeString(recipeString)
                    .renderBackground(renderBackground)
                    .build()
                ).build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(item.getImage()), "recipe.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that recipe!").queue();
            exception.printStackTrace();
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, group = "parse", subcommand = "nbt", description = "Parse an NBT string")
    public void parseNbtString(GuildSlashEvent event, @AppOption String nbt, @AppOption @Optional Integer alpha, @AppOption @Optional Integer padding) {
        event.deferReply().complete();

        alpha = alpha == null ? 245 : alpha;
        padding = padding == null ? 0 : padding;

        try {
            JsonObject jsonObject = JsonParser.parseString(nbt).getAsJsonObject();
            JsonObject tagObject = jsonObject.get("tag").getAsJsonObject();
            ItemBuilder itemBuilder = new ItemBuilder();

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
                    event.getHook().editOriginal(GeneratorMessages.MULTIPLE_ITEM_SKULL_DATA).queue();
                    return;
                }

                String base64 = textures.get(0).getAsJsonObject().get("Value").getAsString();

                itemBuilder.addGenerator(new MinecraftPlayerHeadGenerator.Builder()
                    .parseBase64String(base64)
                    .build()
                );
            } else {
                itemBuilder.addGenerator(new MinecraftItemGenerator.Builder()
                    .withItem(jsonObject.get("id").getAsString())
                    .build()
                );
            }

            Item item = itemBuilder
                .addGenerator(
                    new MinecraftTooltipGenerator.Builder()
                        .parseNbtJson(jsonObject)
                        .withAlpha(alpha)
                        .withPadding(padding)
                        .build()
                ).build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(item.getImage()), "recipe.png")).queue();
        } catch (JsonParseException exception) {
            event.getHook().editOriginal(GeneratorMessages.MISSING_ITEM_NBT).queue();
            return;
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException e) {
            event.getHook().editOriginal("An error occurred while parsing the NBT!").queue();
            throw new RuntimeException(e);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "item", description = "Generate a tooltip")
    public void generateTooltip(
        GuildSlashEvent event,
        @AppOption(autocomplete = "item-names") String itemId,
        @AppOption String name,
        @AppOption(autocomplete = "item-rarities") String rarity,
        @AppOption String type,
        @AppOption String itemLore,
        @AppOption @Optional String skinValue,
        @AppOption @Optional Integer alpha,
        @AppOption @Optional Integer padding,
        @AppOption @Optional Boolean emptyLine,
        @AppOption @Optional Boolean centered,
        @AppOption @Optional Integer maxLineLength,
        @AppOption @Optional Boolean normalItem,
        @AppOption(autocomplete = "tooltip-side") @Optional String tooltipSide
    ) {
        event.deferReply().complete();

        alpha = alpha == null ? 245 : alpha;
        padding = padding == null ? 0 : padding;
        emptyLine = emptyLine != null && emptyLine;
        centered = centered != null && centered;
        maxLineLength = maxLineLength == null ? 30 : maxLineLength;
        normalItem = normalItem != null && normalItem;

        try {
            ItemBuilder itemBuilder = new ItemBuilder();
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withName(name)
                .withRarity((Rarity) Util.findValue(Rarity.VALUES, rarity.toUpperCase()))
                .withItemLore(itemLore)
                .withType(type)
                .withAlpha(alpha)
                .withPadding(padding)
                .withEmptyLine(emptyLine)
                .isCentered(centered)
                .withMaxLineLength(maxLineLength)
                .isNormalItem(normalItem)
                .build();

            if (itemId.equalsIgnoreCase("player_head")) {
                if (skinValue == null) {
                    itemBuilder.addGenerator(new MinecraftItemGenerator.Builder().withItem(itemId).build());
                } else {
                    itemBuilder.addGenerator(new MinecraftPlayerHeadGenerator.Builder().withSkin(skinValue).build());
                }
            } else {
                itemBuilder.addGenerator(new MinecraftItemGenerator.Builder().withItem(itemId).build());
            }

            if (tooltipSide != null && MinecraftTooltipGenerator.TooltipSide.valueOf(tooltipSide.toUpperCase()) == MinecraftTooltipGenerator.TooltipSide.LEFT) {
                itemBuilder.addGenerator(0, tooltipGenerator);
            } else {
                itemBuilder.addGenerator(tooltipGenerator);
            }

            Item item = itemBuilder.build();
            JsonObject tooltipJson = tooltipGenerator.generateNbtJson();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(item.getImage()), "tooltip.png")).queue();
            System.out.println("Generated Tooltip JSON: " + tooltipJson.toString());
        } catch (GeneratorException | IllegalArgumentException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that player head!").queue();
            exception.printStackTrace();
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "text", description = "Generate some text")
    public void generateText(
        GuildSlashEvent event,
        @AppOption String text,
        @AppOption @Optional Boolean centered,
        @AppOption @Optional Integer alpha,
        @AppOption @Optional Integer padding,
        @AppOption @Optional Integer maxLineLength
    ) {
        event.deferReply().complete();

        centered = centered != null && centered;
        alpha = alpha == null ? 245 : alpha;
        padding = padding == null ? 0 : padding;
        maxLineLength = maxLineLength == null ? 30 : maxLineLength;

        try {
            ItemBuilder itemBuilder = new ItemBuilder();
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(text)
                .withAlpha(alpha)
                .withPadding(padding)
                .isCentered(centered)
                .withMaxLineLength(maxLineLength)
                .isNormalItem(false)
                .withEmptyLine(false)
                .build();

            itemBuilder.addGenerator(tooltipGenerator);

            Item item = itemBuilder.build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(item.getImage()), "text.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the text!").queue();
            exception.printStackTrace();
        }
    }

    @AutocompletionHandler(name = "item-names", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> itemNames(CommandAutoCompleteInteractionEvent event) {
        return ItemSpritesheet.getItems().keySet().stream().toList();
    }

    @AutocompletionHandler(name = "item-rarities", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> itemRarities(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(Rarity.values()).map(Rarity::name).toList();
    }

    @AutocompletionHandler(name = "tooltip-side", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> tooltipSide(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(MinecraftTooltipGenerator.TooltipSide.values()).map(MinecraftTooltipGenerator.TooltipSide::name).toList();
    }
}
