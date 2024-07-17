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
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.generator.builder.GeneratorImageBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftItemGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftPlayerHeadGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.skyblock.Rarity;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Log4j2
public class GeneratorCommands extends ApplicationCommand {

    private static final String BASE_COMMAND = "gen2"; // TODO change this back to "gen" when released
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

    @JDASlashCommand(name = BASE_COMMAND, group = "display", subcommand = "item", description = "Display an item")
    public void generateItem(
        GuildSlashEvent event,
        @AppOption(autocomplete = "item-names", description = ITEM_DESCRIPTION) String itemId,
        @AppOption(description = EXTRA_DATA_DESCRIPTION) @Optional String data,
        @AppOption(description = ENCHANTED_DESCRIPTION) @Optional Boolean enchanted,
        @AppOption(description = SKIN_VALUE_DESCRIPTION) @Optional String skinValue,
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        event.deferReply(!showPublicly).complete();

        enchanted = enchanted != null && enchanted;

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
                    .isBigImage()
                    .build()
                );
            }

            GeneratedObject generatedObject = item.build();
            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "item.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that item!").queue();
            log.error("Encountered an error while generating an item display", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "head", description = "Generate a player head")
    public void generateHead(
        GuildSlashEvent event,
        @AppOption(description = TEXTURE_DESCRIPTION) String texture,
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        event.deferReply(!showPublicly).complete();

        try {
            GeneratedObject generatedObject = new GeneratorImageBuilder()
                .addGenerator(new MinecraftPlayerHeadGenerator.Builder().withSkin(texture).build())
                .build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "head.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
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
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        event.deferReply(!showPublicly).complete();

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
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
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
        @AppOption(description = INVENTORY_NAME_DESCRIPTION) @Optional String containerName,
        @AppOption(description = RENDER_BORDER_DESCRIPTION) @Optional Boolean drawBorder,
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        event.deferReply(!showPublicly).complete();

        drawBorder = drawBorder == null || drawBorder;

        try {
            GeneratedObject generatedObject = new GeneratorImageBuilder()
                .addGenerator(new MinecraftInventoryGenerator.Builder()
                    .withRows(rows)
                    .withSlotsPerRow(slotsPerRow)
                    .drawBorder(drawBorder)
                    .drawBackground(true)
                    .withContainerTitle(containerName)
                    .withInventoryString(inventoryString)
                    .build())
                .build();

            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "inventory.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
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
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        event.deferReply(!showPublicly).complete();

        alpha = alpha == null ? 245 : alpha;
        padding = padding == null ? 0 : padding;

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
                    .parseBase64String(base64)
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
                .withPadding(padding);

            GeneratedObject generatedObject = generatorImageBuilder.addGenerator(tooltipGenerator.build()).build();
            // TODO output a command
            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatedObject.getImage()), "recipe.png")).queue();
        } catch (JsonParseException exception) {
            event.getHook().editOriginal("You provided badly formatted NBT!").queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException e) {
            event.getHook().editOriginal("An error occurred while parsing the NBT!").queue();
            log.error("Encountered an error while parsing NBT", e);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "item", description = "Generate a full item image. Supports displaying items, recipes, and tooltips")
    public void generateTooltip(
        GuildSlashEvent event,
        @AppOption(autocomplete = "item-names", description = ITEM_DESCRIPTION) String itemId,
        @AppOption(description = NAME_DESCRIPTION) String name,
        @AppOption(autocomplete = "item-rarities", description = RARITY_DESCRIPTION) String rarity,
        @AppOption(description = TYPE_DESCRIPTION) String type,
        @AppOption(description = LORE_DESCRIPTION) String itemLore,
        @AppOption(description = SKIN_VALUE_DESCRIPTION) @Optional String skinValue,
        @AppOption(description = RECIPE_STRING_DESCRIPTION) @Optional String recipeString,
        @AppOption(description = ALPHA_DESCRIPTION) @Optional Integer alpha,
        @AppOption(description = PADDING_DESCRIPTION) @Optional Integer padding,
        @AppOption(description = EMPTY_LINE_DESCRIPTION) @Optional Boolean emptyLine,
        @AppOption(description = CENTERED_DESCRIPTION) @Optional Boolean centered,
        @AppOption(description = NORMAL_ITEM_DESCRIPTION) @Optional Boolean paddingFirstLine,
        @AppOption(autocomplete = "tooltip-side", description = TOOLTIP_SIDE_DESCRIPTION) @Optional String tooltipSide,
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        event.deferReply(!showPublicly).complete();

        alpha = alpha == null ? 245 : alpha;
        padding = padding == null ? 0 : padding;
        emptyLine = emptyLine != null && emptyLine;
        centered = centered != null && centered;
        paddingFirstLine = paddingFirstLine != null && paddingFirstLine;

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withName(name)
                .withRarity((Rarity) Util.findValue(Rarity.VALUES, rarity.toUpperCase()))
                .withItemLore(itemLore)
                .withType(type)
                .withAlpha(alpha)
                .withPadding(padding)
                .withEmptyLine(emptyLine)
                .isTextCentered(centered)
                .isPaddingFirstLine(paddingFirstLine)
                .build();

            if (itemId.equalsIgnoreCase("player_head")) {
                MinecraftPlayerHeadGenerator.Builder generator = new MinecraftPlayerHeadGenerator.Builder();

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
        } catch (GeneratorException | IllegalArgumentException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating that player head!").queue();
            log.error("Encountered an error while generating a player head", exception);
        }
    }

    @JDASlashCommand(name = BASE_COMMAND, subcommand = "text", description = "Generate some text")
    public void generateText(
        GuildSlashEvent event,
        @AppOption(description = TEXT_DESCRIPTION) String text,
        @AppOption(description = CENTERED_DESCRIPTION) @Optional Boolean centered,
        @AppOption(description = ALPHA_DESCRIPTION) @Optional Integer alpha,
        @AppOption(description = PADDING_DESCRIPTION) @Optional Integer padding,
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        event.deferReply(!showPublicly).complete();

        centered = centered != null && centered;
        alpha = alpha == null ? 245 : alpha;
        padding = padding == null ? 0 : padding;

        try {
            GeneratorImageBuilder generatorImageBuilder = new GeneratorImageBuilder();
            MinecraftTooltipGenerator tooltipGenerator = new MinecraftTooltipGenerator.Builder()
                .withItemLore(text)
                .withAlpha(alpha)
                .withPadding(padding)
                .isTextCentered(centered)
                .isPaddingFirstLine(false)
                .withEmptyLine(false)
                .build();

            generatorImageBuilder.addGenerator(tooltipGenerator);
            event.getHook().editOriginalAttachments(FileUpload.fromData(ImageUtil.toFile(generatorImageBuilder.build().getImage()), "text.png")).queue();
        } catch (GeneratorException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
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
        @AppOption(description = "If the Abiphone symbol should be shown next to the dialogue") @Optional Boolean abiphone,
        @AppOption(description = "Player head texture (username, URL, etc.)") @Optional String skinValue,
        @AppOption(description = "Whether the result should be shown publicly (default: false)") @Optional Boolean showPublicly
    ) {
        showPublicly = showPublicly != null && showPublicly;
        abiphone = abiphone != null && abiphone;
        event.deferReply(!showPublicly).complete();

        String[] lines = dialogue.split("\\\\n");
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "&e[NPC] " + npcName + "&r: " + (abiphone ? "&b%%ABIPHONE%%&r " : "") + lines[i];
            String line = lines[i];

            if (line.contains("{options:")) {
                String[] split = line.split("\\{options: ");
                lines[i] = split[0];
                String[] options = split[1].replace("}", "").split(", ");
                lines[i] += "\n&eSelect an option: &r";
                for (String option : options) {
                    lines[i] += "&a" + option + "&r ";
                }
            }
        }

        dialogue = String.join("\n", lines);

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .withItemLore(dialogue)
            .withAlpha(0)
            .withPadding(0)
            .isPaddingFirstLine(false)
            .withEmptyLine(false);

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
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while generating the dialogue!").queue();
            log.error("Encountered an error while generating dialogue", exception);
        }
    }

    @AutocompletionHandler(name = "item-names", showUserInput = false, mode = AutocompletionMode.CONTINUITY)
    public List<String> itemNames(CommandAutoCompleteInteractionEvent event) {
        return Spritesheet.getImageMap().keySet().stream().toList();
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
